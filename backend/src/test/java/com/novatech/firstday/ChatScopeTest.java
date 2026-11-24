package com.novatech.firstday;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatScopeTest {
    final HistoryService history=mock(HistoryService.class);
    final WorkspaceService workspace=mock(WorkspaceService.class);
    final PersonalService personal=mock(PersonalService.class);
    final RagService rag=mock(RagService.class);
    final QueryPolicy policy=new QueryPolicy(new ObjectMapper());
    final ChatService chat=new ChatService(history,workspace,personal,rag,policy);
    final UUID employee=UUID.randomUUID(),conversation=UUID.randomUUID();
    final Map<String,Object> intern=Db.map("id",employee,"onboarding_mode","COMPANY");

    @ParameterizedTest
    @ValueSource(strings={"Из чего состоит проект?","Расскажи про OrbitFlow","Кто владелец Beacon?","What are the project components?","Опиши архитектуру"})
    void internshipProjectQuestionNeverReachesKnowledgeSearch(String question){
        StringBuilder output=new StringBuilder();
        var result=chat.respond(new ApiDtos.Chat(question,null,null),intern,output::append);
        assertEquals(QueryPolicy.internshipBoundary(question),result.answer());
        assertEquals(result.answer(),output.toString());
        assertTrue(result.sources().isEmpty());
        verifyNoInteractions(rag,personal,workspace);
        verify(history).save(null,employee,null,question,result.answer(),List.of());
    }

    @Test void projectIdCannotOverrideInternshipProfile(){
        assertEquals(403,assertThrows(ApiException.class,
            ()->chat.respond(new ApiDtos.Chat("Question",null,UUID.randomUUID()),intern,text->{})).status);
        verifyNoInteractions(history,rag,personal,workspace);
    }

    @Test void quotedFollowupRetainsTheBlockedProjectTopic(){
        when(history.recent(conversation,employee,null)).thenReturn(List.of("Из чего состоит проект?"));
        String question="«А кто за них отвечает?";
        var result=chat.respond(new ApiDtos.Chat(question,conversation,null),intern,text->{});
        assertEquals(QueryPolicy.internshipBoundary("А кто за них отвечает?"),result.answer());
        assertTrue(result.sources().isEmpty());
        verifyNoInteractions(rag,personal,workspace);
        verify(history).save(conversation,employee,null,question,result.answer(),List.of());
    }

    @Test void projectModeWithoutSelectedProjectAsksForSelection(){
        var user=Db.map("id",employee,"onboarding_mode","PROJECT");
        var result=chat.respond(new ApiDtos.Chat("Из чего состоит проект?",null,null),user,text->{});
        assertEquals(QueryPolicy.selectProject("Из чего состоит проект?"),result.answer());
        verifyNoInteractions(rag,personal,workspace);
    }

    @Test void successiveFollowupsKeepTheOriginalTopicAndPriorAnswers(){
        var previous=List.of("Как настроить VPN?","А если ошибка?","А если не поможет?");
        var turns=List.of(Db.map("role","user","content","Как настроить VPN?"),
            Db.map("role","assistant","content","Профиль выдаёт IT Support."));
        when(history.recent(conversation,employee,null)).thenReturn(previous);
        when(history.context(conversation,employee,null)).thenReturn(turns);
        String question="«А кто за это отвечает?";
        String contextual="Как настроить VPN?\nУточнение: А если ошибка?\nУточнение: А если не поможет?\nУточнение: А кто за это отвечает?";
        when(rag.answerWithContext(eq(contextual),isNull(),eq(employee),eq(true),eq(turns),any()))
            .thenReturn(new RagService.Answer("Владелец инструкции — IT Support.",List.of()));
        assertTrue(chat.respond(new ApiDtos.Chat(question,conversation,null),intern,text->{}).answer().contains("IT Support"));
        verify(rag).answerWithContext(eq(contextual),isNull(),eq(employee),eq(true),eq(turns),any());
    }

    @Test void newTopicDoesNotInheritBlockedProjectQuestion(){
        when(history.recent(conversation,employee,null)).thenReturn(List.of("Из чего состоит проект?"));
        String question="А как настроить VPN?";
        when(rag.answerWithContext(eq(question),isNull(),eq(employee),eq(true),anyList(),any()))
            .thenReturn(new RagService.Answer("VPN procedure",List.of()));
        assertEquals("VPN procedure",chat.respond(new ApiDtos.Chat(question,conversation,null),intern,text->{}).answer());
    }

    @Test void privateQuestionsStillUseTheInternshipPlan(){
        when(personal.answer("Какой мой прогресс?",intern,null)).thenReturn("Ваш прогресс: 33%.");
        assertEquals("Ваш прогресс: 33%.",
            chat.respond(new ApiDtos.Chat("Какой мой прогресс?",null,null),intern,text->{}).answer());
        verifyNoInteractions(rag,workspace);
    }

    @Test void newConversationDoesNotReuseAnotherConversationsTopic(){
        var previous=List.of("Как настроить VPN?");
        assertEquals("Как проходит интершип?",chat.contextualQuestion("Как проходит интершип?",previous));
        assertEquals("А кто за это отвечает?",chat.contextualQuestion("А кто за это отвечает?",List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings={"«А кто за это отвечает?","Отлично, а кто за это отвечает?","Что это значит?","Подробнее","Можешь подробнее?"})
    void naturalFollowupFormsKeepTheEarlierTopic(String question){
        assertTrue(ChatService.followup(question));
        assertTrue(chat.contextualQuestion(question,List.of("Как настроить VPN?")).startsWith("Как настроить VPN?"));
    }
}
