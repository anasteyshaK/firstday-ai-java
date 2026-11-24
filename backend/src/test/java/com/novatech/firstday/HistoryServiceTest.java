package com.novatech.firstday;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryServiceTest {
    private final Db db = mock(Db.class);
    private final WorkspaceService workspace = mock(WorkspaceService.class);
    private final HistoryService history = new HistoryService(db, workspace);
    private final UUID employee = UUID.randomUUID();
    private final UUID conversation = UUID.randomUUID();
    private final UUID project = UUID.randomUUID();

    @Test
    void newConversationHasNoPreviousMessages() {
        assertEquals(List.of(), history.recent(null, employee, project));
        verifyNoInteractions(db, workspace);
    }

    @Test
    void unknownOrForeignConversationCannotExposeMessages() {
        when(db.optional(anyString(), eq(conversation), eq(employee))).thenReturn(null);
        assertEquals(404, assertThrows(ApiException.class, () -> history.get(conversation, employee)).status);
        verify(db).optional(contains("id=? AND employee_id=?"), eq(conversation), eq(employee));
        verify(db, never()).rows(anyString(), any(Object[].class));
        verifyNoInteractions(workspace);
    }

    @Test
    void revokedMembershipPreventsReadingExistingProjectChat() {
        when(db.optional(anyString(), eq(conversation), eq(employee)))
            .thenReturn(Db.map("id", conversation, "project_id", project));
        when(workspace.membership(project, employee)).thenThrow(ApiException.missing("Project unavailable"));
        assertEquals(404, assertThrows(ApiException.class, () -> history.get(conversation, employee)).status);
        verify(db, never()).rows(anyString(), any(Object[].class));
    }

    @Test
    void conversationCannotBeReusedInAnotherProject() {
        when(db.optional(anyString(), eq(conversation), eq(employee)))
            .thenReturn(Db.map("id", conversation, "project_id", project));
        assertEquals(409, assertThrows(ApiException.class,
            () -> history.recent(conversation, employee, UUID.randomUUID())).status);
        verify(db, never()).rows(anyString(), any(Object[].class));
    }

    @Test
    void previousQuestionsAreReturnedInChronologicalOrder() {
        when(db.optional(anyString(), eq(conversation), eq(employee)))
            .thenReturn(Db.map("id", conversation, "project_id", null));
        when(db.rows(anyString(), eq(conversation), eq(employee)))
            .thenReturn(List.of(Db.map("content", "latest question"), Db.map("content", "earlier question")));
        assertEquals(List.of("earlier question", "latest question"), history.recent(conversation, employee, null));
    }

    @Test
    void listingAProjectChatRequiresActiveMembership() {
        when(workspace.membership(project, employee)).thenThrow(ApiException.missing("Project unavailable"));
        assertThrows(ApiException.class, () -> history.list(employee, project));
        verifyNoInteractions(db);
    }

    @Test
    void deletingForeignConversationDoesNotWriteToDatabase() {
        when(db.optional(anyString(), eq(conversation), eq(employee))).thenReturn(null);
        assertThrows(ApiException.class, () -> history.delete(conversation, employee));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void revokedMembershipPreventsSavingNewProjectMessages() {
        when(workspace.membership(project, employee)).thenThrow(ApiException.missing("Project unavailable"));
        assertThrows(ApiException.class,
            () -> history.save(null, employee, project, "Question", "Answer", List.of()));
        verifyNoInteractions(db);
    }

    @Test void newChatHasNoAssistantContext(){
        assertTrue(history.context(null,employee,null).isEmpty());
        verifyNoInteractions(db,workspace);
    }
    @Test void assistantContextKeepsBothRolesInChronologicalOrder(){
        when(db.optional(anyString(),eq(conversation),eq(employee)))
            .thenReturn(Db.map("id",conversation,"project_id",null));
        when(db.rows(anyString(),eq(conversation),eq(employee))).thenReturn(List.of(
            Db.map("role","assistant","content","Earlier answer"),
            Db.map("role","user","content","Earlier question")));
        var context=history.context(conversation,employee,null);
        assertEquals(List.of("user","assistant"),context.stream().map(t->t.get("role")).toList());
        verify(db).rows(contains("conversation_id=? AND employee_id=?"),eq(conversation),eq(employee));
    }
    @Test void contextCannotCrossProjectScope(){
        when(db.optional(anyString(),eq(conversation),eq(employee)))
            .thenReturn(Db.map("id",conversation,"project_id",project));
        assertEquals(409,assertThrows(ApiException.class,()->history.context(conversation,employee,null)).status);
        verify(db,never()).rows(anyString(),any(Object[].class));
    }
    @Test void foreignChatCannotProvideModelContext(){
        when(db.optional(anyString(),eq(conversation),eq(employee))).thenReturn(null);
        assertEquals(404,assertThrows(ApiException.class,()->history.context(conversation,employee,null)).status);
        verify(db,never()).rows(anyString(),any(Object[].class));
    }
}
