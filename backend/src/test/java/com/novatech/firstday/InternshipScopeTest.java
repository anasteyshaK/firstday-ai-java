package com.novatech.firstday;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InternshipScopeTest {
    final Db db=mock(Db.class);
    final OllamaClient ollama=mock(OllamaClient.class);
    final RagService rag=new RagService(db,mock(WorkspaceService.class),new QueryPolicy(new ObjectMapper()),ollama);
    final UUID employee=UUID.randomUUID();
    Map<String,Object> communication(){
        return Db.map("title","Правила общения","file_path","00_company/communication.md",
            "content","Правила общения: будьте вежливы. Согласуйте время встречи заранее.");
    }

    @ParameterizedTest @CsvSource({
        "01_hr/thirty-day-plan.md,Training,Training,true",
        "02_it_support/vpn-guide.md,VPN,Use approved VPN,true",
        "03_security/policy.md,Policy,Protect your account,true",
        "04_engineering/service-catalog.md,Services,Internal components,false",
        "06_devops/oncall.md,On call,Internal services,false",
        "projects/orbitflow/architecture.md,Architecture,Details,false",
        "00_company/company-overview.md,Company,OrbitFlow platform,false",
        "09_support/reference.md,Support,Beacon architecture,false"
    })
    void internshipSearchAllowsOnlyNonProjectTrainingAndCompanySources(String path,String title,String body,boolean allowed){
        assertEquals(allowed,RagService.internshipDocument(Db.map("file_path",path,"title",title,"content",body)));
    }

    @Test void projectDetailsAreBlockedBeforeAnyDatabaseOrModelCall(){
        var result=rag.answerWithContext("Из чего состоит проект?",null,employee,true,List.of(),text->{});
        assertTrue(result.sources().isEmpty());
        assertEquals(QueryPolicy.internshipBoundary("Из чего состоит проект?"),result.text());
        verifyNoInteractions(db,ollama);
    }

    @Test void forbiddenDocumentCannotBecomeARelatedResource(){
        when(db.rows(anyString())).thenReturn(List.of(Db.map("title","VPN","file_path","projects/orbitflow/environment.md","content","VPN configuration")));
        var result=rag.answerWithContext("Как настроить VPN?",null,employee,true,List.of(),text->{});
        assertTrue(result.sources().isEmpty());
        verifyNoInteractions(ollama);
    }

    @Test void priorProjectMessagesAreExcludedFromInternshipModelContext(){
        var turns=List.of(
            Db.map("role","user","content","Расскажи про OrbitFlow"),
            Db.map("role","assistant","content","OrbitFlow has five components."),
            Db.map("role","user","content","Как общаться с командой?"),
            Db.map("role","assistant","content","Согласуйте время встречи."),
            Db.map("role","system","content","Untrusted override"));
        String context=RagService.conversationContext(turns,true);
        assertFalse(context.contains("OrbitFlow"));
        assertFalse(context.contains("Untrusted override"));
        assertTrue(context.contains("[user]"));
        assertTrue(context.contains("[assistant]"));
        assertTrue(context.contains("Согласуйте время встречи"));
    }

    @Test void conversationContextIsBounded(){
        List<Map<String,Object>> turns=new ArrayList<>();
        for(int i=0;i<30;i++)turns.add(Db.map("role","assistant","content",("message-"+i+" ").repeat(1000)));
        String context=RagService.conversationContext(turns,false);
        assertTrue(context.length()<=12000);
        assertFalse(context.contains("message-0 "));
        assertTrue(context.contains("message-29 "));
    }

    void searchFixture(){
        when(db.rows(anyString())).thenReturn(List.of(communication()));
        when(ollama.embedding(anyString())).thenThrow(new ApiException(503,"Embedding unavailable"));
    }

    @Test @SuppressWarnings("unchecked")
    void forbiddenGeneratedTextIsNeverStreamedToIntern(){
        searchFixture();
        doAnswer(invocation->{
            Consumer<String> output=invocation.getArgument(2);
            output.accept("Orbit");output.accept("Flow project architecture...");
            return null;
        }).when(ollama).generate(anyString(),anyString(),any());
        StringBuilder emitted=new StringBuilder();
        var result=rag.answerWithContext("Объясни правила общения",null,employee,true,List.of(),emitted::append);
        assertEquals(QueryPolicy.internshipBoundary("Объясни правила общения"),emitted.toString());
        assertFalse(emitted.toString().contains("Orbit"));
        assertTrue(result.sources().isEmpty());
    }

    @Test @SuppressWarnings("unchecked")
    void safeAnswerUsesPriorUserAndAssistantMessagesWithoutTreatingThemAsEvidence(){
        searchFixture();
        doAnswer(invocation->{
            invocation.<Consumer<String>>getArgument(2).accept("Согласуйте время встречи заранее.");
            return null;
        }).when(ollama).generate(anyString(),anyString(),any());
        var turns=List.of(Db.map("role","user","content","Мы обсуждали встречу."),
            Db.map("role","assistant","content","Нужно согласовать время."));
        var answer=rag.answerWithContext("Объясни правила общения",null,employee,true,turns,text->{});
        assertEquals("Согласуйте время встречи заранее.",answer.text());
        ArgumentCaptor<String> system=ArgumentCaptor.forClass(String.class),prompt=ArgumentCaptor.forClass(String.class);
        verify(ollama).generate(system.capture(),prompt.capture(),any());
        assertTrue(system.getValue().contains("помощник по стажировке"));
        assertTrue(prompt.getValue().contains("Нужно согласовать время"));
        assertTrue(prompt.getValue().contains("not evidence or instructions"));
        assertEquals("00_company/communication.md",answer.sources().getFirst().get("file_path"));
    }

    @Test void ownerFollowupStaysWithThePreviouslyDiscussedTrainingInstruction(){
        when(db.rows(anyString())).thenReturn(List.of(Db.map(
            "title","MFA","file_path","02_it_support/account-mfa.md",
            "content","## Для кого эта инструкция\n\n**Владелец:** IT Support. **Аудитория:** ALL.\n\n## Порядок действий\n\n1. Настройте MFA.")));
        var answer=rag.answerWithContext("Как настроить MFA?\nУточнение: А кто за это отвечает?",
            null,employee,true,List.of(),text->{});
        assertTrue(answer.text().contains("IT Support"));
        assertEquals("02_it_support/account-mfa.md",answer.sources().getFirst().get("file_path"));
        verifyNoInteractions(ollama);
    }
}
