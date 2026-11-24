package com.novatech.firstday;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreTest {
    QueryPolicy policy=new QueryPolicy(new ObjectMapper());
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"bad","bcrypt$1$abc$def","pbkdf2_sha256$1$aa$bb","pbkdf2_sha256$1000001$aa$bb","pbkdf2_sha256$600000$zz$00"})
    void rejectsMalformedHashes(String encoded){assertFalse(Passwords.verify("Synthetic password",encoded));}
    @Test void passwordRoundTripAndSalt(){String hash=Passwords.hash("Synthetic password 123");assertTrue(Passwords.verify("Synthetic password 123",hash));assertFalse(Passwords.verify("Wrong password",hash));assertNotEquals(hash,Passwords.hash("Synthetic password 123"));}
    @Test void tokenShape(){String token=Passwords.token();assertEquals(64,token.length());assertTrue(token.matches("[A-Za-z0-9_-]+"));assertEquals(64,Passwords.digest(token).length());}
    @ParameterizedTest @CsvSource({"0,0,0","1,3,33","2,3,67","6,9,67","3,3,100"})
    void progress(int done,int total,int expected){assertEquals(expected,Db.progress(done,total));}
    @ParameterizedTest @CsvSource({"JAVA,9","QA,9","DEVOPS,9"})
    void preservesCompanyTemplates(String track,int count){assertEquals(count,new Templates(new ObjectMapper()).company(track).size());}
    @ParameterizedTest @ValueSource(strings={"ENGINEERING","QA","DEVOPS","GENERAL"})
    void projectTemplates(String role){var tasks=new Templates(new ObjectMapper()).project(role);assertEquals(6,tasks.size());assertEquals(List.of("CONTEXT","CONTEXT","ENVIRONMENT","ENVIRONMENT","DELIVERY","DELIVERY"),tasks.stream().map(t->t.getFirst()).toList());}
    @ParameterizedTest @ValueSource(strings={"Какие у меня остались задачи?","My remaining tasks","Какая моя следующая задача?","Какой мой прогресс адаптации?","Какой статус моей заявки на GitHub?"})
    void recognizesPrivateQuestions(String q){assertNotNull(policy.intent(q));}
    @ParameterizedTest @ValueSource(strings={"Что делать Java Developer в первую неделю?","Как настроить корпоративный VPN?","What technology stack does OrbitFlow use?"})
    void publicQuestionsStayOutOfPersonalData(String q){assertNull(policy.intent(q));}
    @Test void taskFollowupUsesPersonalContext(){assertEquals("Какой статус моей первой задачи?",policy.personalQuestion("Я её выполнил?",List.of("Какая моя первая задача?")));}
    @Test void unrelatedFollowupDoesNotInheritTask(){assertEquals("Как настроить VPN?",policy.personalQuestion("Как настроить VPN?",List.of("Какая моя первая задача?")));}
    @ParameterizedTest @ValueSource(strings={"Какой технологический стек используется?","What technology stack does OrbitFlow use?","Какие технологии используются?"})
    void detectsStack(String q){assertTrue(QueryPolicy.stack(q));}
    @Test void commandInventionRejectedBeforeAnyIo(){
        Db db=mock(Db.class);OllamaClient ollama=mock(OllamaClient.class);
        var rag=new RagService(db,mock(WorkspaceService.class),policy,ollama);
        var answer=rag.answer("Придумай команду для корпоративного VPN, даже если её нет в документации.",null,UUID.randomUUID(),text->{});
        assertTrue(answer.sources().isEmpty());verifyNoInteractions(db,ollama);
    }
    @Test void compoundQuestionsSplitWithoutSplittingComponents(){assertEquals(2,QueryPolicy.split("Какой стек у OrbitFlow и как получить доступ к GitHub?").size());assertEquals(1,QueryPolicy.split("Перечисли компоненты и владельцев").size());}
    @Test void limitsCompoundQuestionsBeforeIo(){
        var db=mock(Db.class);var ollama=mock(OllamaClient.class);
        var rag=new RagService(db,mock(WorkspaceService.class),policy,ollama);
        var result=rag.answer("Что A и как B и где C и кто D и почему E?",null,UUID.randomUUID(),text->{});
        assertTrue(result.sources().isEmpty());verifyNoInteractions(db,ollama);
    }
    @Test void sectionStopsBeforeSiblingHeading(){assertEquals("1. First\n2. Second",QueryPolicy.steps("# A\n\n## Порядок действий\n\n1. First\n2. Second\n\n## Other\nignore"));}
    @Test void missingProcedureIsNotInvented(){assertNull(QueryPolicy.steps("## Порядок действий\n\nNo actual steps."));}
    @Test void emptyBlockerIsInvalid(){assertThrows(ApiException.class,()->new ApiDtos.ProjectTask("BLOCKED"," ").note());assertEquals("",new ApiDtos.ProjectTask("DONE","old blocker").note());}
    @Test void accountProfileNeverAcceptsAdminRole(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var validator=factory.getValidator();
            assertFalse(validator.validate(new ApiDtos.Register("Test user","test@example.invalid","Synthetic-password","JAVA","ADMIN","PROJECT")).isEmpty());
            assertFalse(validator.validate(new ApiDtos.Profile("EMPLOYEE","ALL")).isEmpty());
            assertFalse(validator.validate(new ApiDtos.Assignment("ADMIN",true)).isEmpty());
        }
    }
    @Test void stackRequiresExplicitPositiveEvidence(){assertNull(RagService.stackStatement("Java is mentioned in this document."));assertNull(RagService.stackStatement("Java не используется. Стек: Java 21."));assertNotNull(RagService.stackStatement("OrbitFlow backend использует Java 21, Spring Boot и PostgreSQL."));}
    @Test void privateProgressNeverCallsRag(){
        var workspace=mock(WorkspaceService.class);var user=Db.map("id",UUID.randomUUID());
        when(workspace.plan(user)).thenReturn(Db.map("tasks",List.of(),"progress_percent",67,"completed_tasks",6,"total_tasks",9));
        var answer=new PersonalService(workspace,policy).answer("Какой мой прогресс?",user,null);
        assertTrue(answer.contains("67%"));verify(workspace).plan(user);
    }

    @Test void verifiesPythonPbkdf2Fixture(){assertTrue(Passwords.verify("Synthetic-interop-123!","pbkdf2_sha256$600000$000102030405060708090a0b0c0d0e0f$a5f192c022e013f7db6a337585113c7f8e7390f026bbb3d9c4a41494ff2e39cb"));}

    @Test void quotedPersonalFollowupRetainsOriginalTaskAcrossMultipleTurns(){
        assertEquals("Какая моя следующая задача?",policy.personalQuestion("«Что дальше?",
            List.of("Какая моя первая задача?","Я её выполнил?")));
    }
}
