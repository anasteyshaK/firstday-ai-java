package com.novatech.firstday;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InternshipAnswerTest {
    private final Db db = mock(Db.class);
    private final WorkspaceService workspace = mock(WorkspaceService.class);
    private final OllamaClient ollama = mock(OllamaClient.class);
    private final QueryPolicy policy = new QueryPolicy(new ObjectMapper());
    private final RagService rag = new RagService(db, workspace, policy, ollama);
    private final UUID employee = UUID.randomUUID();

    private Map<String, Object> plan() {
        return Db.map("title", "30-дневный план", "file_path", "01_hr/thirty-day-plan.md",
            "content", """
                # 30-дневный план
                ## Назначение и контекст
                30-дневная программа: дни 1–5 — доступы и ориентация; 6–10 — продукт и процессы;
                11–20 — первые контролируемые задачи; 21–30 — самостоятельная задача и итоги.
                ## Порядок действий
                1. Откройте свой план.
                2. Проверяйте прогресс дважды в неделю.
                3. На день 30 подготовьте демонстрацию результата.
                ## Другие сведения
                Эти сведения не нужны в ответе.
                """);
    }

    private Map<String, Object> unrelated() {
        return Db.map("title", "PostgreSQL connection refused",
            "file_path", "11_incidents/postgres-connection.md",
            "content", "Проверьте сервер через docker ps или systemctl status.",
            "similarity", 1.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "как проходит интершип?", "Как проходит интерншип?",
        "Как устроена стажировка?", "Расскажи об internship",
        "Как проходит onboarding в компании?", "Что такое интершип?"
    })
    void internshipOverviewUsesTrainingPlanInsteadOfServerRunbooks(String question) {
        when(db.rows(anyString())).thenReturn(List.of(unrelated(), plan()));
        StringBuilder output = new StringBuilder();
        var answer = rag.answer(question, null, employee, output::append);
        assertEquals(List.of("01_hr/thirty-day-plan.md"),
            answer.sources().stream().map(source -> source.get("file_path")).toList());
        assertTrue(answer.text().contains("1–5"));
        assertTrue(answer.text().contains("21–30"));
        assertTrue(answer.text().contains("демонстрацию"));
        assertFalse(answer.text().contains("docker"));
        assertFalse(answer.text().contains("systemctl"));
        assertFalse(answer.text().contains("\\\\n"));
        assertEquals(answer.text(), output.toString());
        verifyNoInteractions(ollama);
    }

    @Test
    void missingTrainingPlanDoesNotFallBackToUnrelatedTechnicalSources() {
        when(db.rows(anyString())).thenReturn(List.of(unrelated()));
        var answer = rag.answer("как проходит интершип?", null, employee, text -> {});
        assertTrue(answer.sources().isEmpty());
        assertEquals(QueryPolicy.refuse("как проходит интершип?"), answer.text());
        verifyNoInteractions(ollama);
    }

    @Test
    void technicalQuestionDuringInternshipKeepsItsOwnTopic() {
        assertFalse(QueryPolicy.internshipOverview("Как настроить VPN на стажировке?"));
        assertEquals("02_it_support/vpn-guide.md",
            rag.preferred("как настроить vpn на стажировке?", false));
    }

    @Test
    void roleSpecificFirstWeekQuestionKeepsItsLearningTrack() {
        assertFalse(QueryPolicy.internshipOverview("Что делать Java Developer в первую неделю стажировки?"));
        assertEquals("08_learning/java-track.md",
            rag.preferred("что делать java developer в первую неделю стажировки?", false));
    }

    @Test
    void internshipQuestionCannotReadCorporateDocumentsFromProjectScope() {
        when(workspace.membership(any(), eq(employee))).thenReturn(Db.map("slug", "orbitflow"));
        when(workspace.projectDocuments(any(), eq(employee))).thenReturn(List.of(
            Db.map("title", "Project environment", "file_path", "projects/orbitflow/environment.md",
                "content", "Start the server using Docker.")));
        var answer = rag.answer("Как проходит интершип?", UUID.randomUUID(), employee, text -> {});
        assertTrue(answer.sources().isEmpty());
        verifyNoInteractions(ollama);
        verify(db, never()).rows(anyString(), any(Object[].class));
    }

    @ParameterizedTest
    @ValueSource(strings={"How does an internship work?", "How does the intership work?"})
    void englishInternshipQuestionRetrievesOnlyTrainingDocuments(String question){
        when(db.rows(anyString())).thenReturn(List.of(unrelated(),plan()));
        var selected=rag.retrieve(question,null,employee);
        assertEquals(List.of("01_hr/thirty-day-plan.md"),
            selected.stream().map(doc->doc.get("file_path")).toList());
        verifyNoInteractions(ollama);
    }

    @Test
    void projectOnboardingIsNotMistakenForInternship(){
        assertFalse(QueryPolicy.internshipOverview("Как проходит onboarding проекта?"));
    }
}
