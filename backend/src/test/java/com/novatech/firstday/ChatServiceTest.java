package com.novatech.firstday;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    private final HistoryService history = mock(HistoryService.class);
    private final WorkspaceService workspace = mock(WorkspaceService.class);
    private final PersonalService personal = mock(PersonalService.class);
    private final RagService rag = mock(RagService.class);
    private final QueryPolicy policy = new QueryPolicy(new ObjectMapper());
    private final ChatService chat = new ChatService(history, workspace, personal, rag, policy);
    private final UUID employee = UUID.randomUUID();
    private final Map<String, Object> user = Db.map("id", employee);

    @Test
    void privateAnswerBypassesRagAndIsSavedWithoutDocumentSources() {
        String question = "Какой мой прогресс?";
        String answer = "Ваш прогресс: 50%.";
        UUID conversation = UUID.randomUUID();
        when(personal.answer(question, user, null)).thenReturn(answer);
        when(history.save(null, employee, null, question, answer, List.of())).thenReturn(conversation);
        StringBuilder output = new StringBuilder();
        var result = chat.respond(new ApiDtos.Chat(question, null, null), user, output::append);
        assertEquals(answer, output.toString());
        assertEquals(answer, result.answer());
        assertEquals(conversation, result.conversation_id());
        assertTrue(result.sources().isEmpty());
        verifyNoInteractions(rag);
    }

    @Test
    void projectMembershipIsCheckedBeforeHistoryAndGeneration() {
        UUID project = UUID.randomUUID();
        when(workspace.membership(project, employee)).thenThrow(ApiException.missing("Project unavailable"));
        assertThrows(ApiException.class,
            () -> chat.respond(new ApiDtos.Chat("Question", null, project), user, text -> {}));
        verifyNoInteractions(history, personal, rag);
    }

    @Test
    void providerFailureDoesNotSaveAnIncompleteExchange() {
        when(rag.answer(anyString(), isNull(), eq(employee), any())).thenAnswer(invocation -> {
            invocation.<Consumer<String>>getArgument(3).accept("partial output");
            throw new ApiException(503, "Ollama unavailable");
        });
        StringBuilder output = new StringBuilder();
        assertEquals(503, assertThrows(ApiException.class,
            () -> chat.respond(new ApiDtos.Chat("Explain communication rules", null, null), user, output::append)).status);
        assertEquals("partial output", output.toString());
        verify(history, never()).save(any(), any(), any(), anyString(), anyString(), anyList());
    }

    @Test
    void disconnectedClientDoesNotCreateACompletedExchange() {
        String question = "Какой мой прогресс?";
        when(personal.answer(question, user, null)).thenReturn("Your progress: 50%.");
        assertThrows(UncheckedIOException.class,
            () -> chat.respond(new ApiDtos.Chat(question, null, null), user,
                text -> { throw new UncheckedIOException(new IOException("Synthetic disconnected client")); }));
        verify(history, never()).save(any(), any(), any(), anyString(), anyString(), anyList());
        verifyNoInteractions(rag);
    }

    @Test
    void contextualFollowupUsesPriorQuestionButSavesTheUsersOriginalQuestion() {
        UUID conversation = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        String previous = "Из каких частей состоит проект?";
        String question = "А кто за них отвечает?";
        String contextual = previous + "\nУточнение: " + question;
        List<Map<String, Object>> sources = List.of(Db.map("file_path", "projects/orbitflow/architecture.md"));
        when(history.recent(conversation, employee, project)).thenReturn(List.of(previous));
        when(rag.answer(eq(contextual), eq(project), eq(employee), any()))
            .thenReturn(new RagService.Answer("Find owners in Atlas.", sources));
        var result = chat.respond(new ApiDtos.Chat(question, conversation, project), user, text -> {});
        assertEquals(sources, result.sources());
        verify(history).save(conversation, employee, project, question, "Find owners in Atlas.", sources);
    }

    @Test
    void explicitNewToolQuestionDoesNotInheritOldTopic() {
        UUID conversation = UUID.randomUUID();
        String question = "А как настроить VPN?";
        when(history.recent(conversation, employee, null)).thenReturn(List.of("Как получить доступ к GitHub?"));
        when(rag.answer(eq(question), isNull(), eq(employee), any()))
            .thenReturn(new RagService.Answer("VPN procedure.", List.of()));
        assertEquals("VPN procedure.",
            chat.respond(new ApiDtos.Chat(question, conversation, null), user, text -> {}).answer());
        verify(rag).answer(eq(question), isNull(), eq(employee), any());
    }

    @Test
    void blankQuestionFailsBeforeReadingHistoryOrPrivateData() {
        assertEquals(422, assertThrows(ApiException.class,
            () -> chat.respond(new ApiDtos.Chat("   ", null, null), user, text -> {})).status);
        verifyNoInteractions(history, workspace, personal, rag);
    }
}
