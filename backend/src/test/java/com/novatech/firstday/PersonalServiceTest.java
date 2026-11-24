package com.novatech.firstday;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalServiceTest {
    private final WorkspaceService workspace = mock(WorkspaceService.class);
    private final QueryPolicy policy = new QueryPolicy(new ObjectMapper());
    private final PersonalService service = new PersonalService(workspace, policy);
    private final UUID employee = UUID.randomUUID();
    private final Map<String, Object> user = Db.map("id", employee);

    @Test
    void documentationQuestionDoesNotReadPersonalData() {
        assertNull(service.answer("Как настроить корпоративный VPN?", user, null));
        verifyNoInteractions(workspace);
    }

    @Test
    void requestsForOneToolDoNotIncludeAnotherToolsStatus() {
        when(workspace.requests(user)).thenReturn(Db.map("items", List.of(
            Db.map("system_name", "GitHub", "status", "PENDING"),
            Db.map("system_name", "VPN", "status", "APPROVED"))));
        String answer = service.answer("Какой статус моей заявки на GitHub?", user, null);
        assertTrue(answer.contains("GitHub"));
        assertTrue(answer.contains("Ожидает рассмотрения"));
        assertFalse(answer.contains("VPN"));
        assertFalse(answer.contains("Одобрена"));
        verify(workspace, never()).plan(user);
    }

    @Test
    void missingRequestIsNotInvented() {
        when(workspace.requests(user)).thenReturn(Db.map("items", List.of()));
        assertEquals("Подходящих заявок на доступ не найдено.",
            service.answer("Какой статус моей заявки на GitHub?", user, null));
    }

    @Test
    void remainingTasksExcludeCompletedTasks() {
        when(workspace.plan(user)).thenReturn(Db.map("tasks", List.of(
            Db.map("title", "Already finished", "status", "DONE"),
            Db.map("title", "Pending setup", "status", "TODO"))));
        String answer = service.answer("Какие у меня остались задачи?", user, null);
        assertTrue(answer.contains("Pending setup"));
        assertFalse(answer.contains("Already finished"));
    }

    @Test
    void nextTaskSkipsFinishedTasksAndKeepsDescription() {
        when(workspace.plan(user)).thenReturn(Db.map("tasks", List.of(
            Db.map("title", "Already finished", "status", "DONE"),
            Db.map("title", "Pending setup", "description", "Read the setup guide", "status", "IN_PROGRESS"))));
        String answer = service.answer("Какая моя следующая задача?", user, null);
        assertTrue(answer.contains("Pending setup"));
        assertTrue(answer.contains("Read the setup guide"));
        assertFalse(answer.contains("Already finished"));
    }

    @Test
    void completedPlanDoesNotInventAnotherTask() {
        when(workspace.plan(user)).thenReturn(Db.map("tasks", List.of(
            Db.map("title", "Finished", "status", "DONE"))));
        assertEquals("Все сохранённые задачи выполнены.",
            service.answer("Какая моя следующая задача?", user, null));
    }

    @Test
    void projectProgressUsesOnlyTheSelectedProjectPlan() {
        UUID project = UUID.randomUUID();
        when(workspace.projectPlan(project, employee)).thenReturn(Db.map(
            "tasks", List.of(), "progress_percent", 50, "completed_tasks", 3, "total_tasks", 6));
        assertTrue(service.answer("Какой мой прогресс?", user, project).contains("50%"));
        verify(workspace, never()).plan(user);
    }

    @Test
    void blockersIncludeOnlyRecordedBlockedTasks() {
        UUID project = UUID.randomUUID();
        when(workspace.projectPlan(project, employee)).thenReturn(Db.map("tasks", List.of(
            Db.map("title", "Environment", "status", "BLOCKED", "blocker_note", "Waiting for approved access"),
            Db.map("title", "Already finished", "status", "DONE", "blocker_note", ""),
            Db.map("title", "Pending setup", "status", "TODO", "blocker_note", ""))));
        String answer = service.answer("Какие мои блокеры?", user, project);
        assertTrue(answer.contains("Environment"));
        assertTrue(answer.contains("Waiting for approved access"));
        assertFalse(answer.contains("Already finished"));
        assertFalse(answer.contains("Pending setup"));
        verify(workspace, never()).plan(user);
    }
}
