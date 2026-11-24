package com.novatech.firstday;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.stream.*;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.*;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="firstday.integration",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceIntegrationTest {
    static final String SCHEMA="java_test_"+UUID.randomUUID().toString().replace("-","");
    static final Configuration.Env SETTINGS=new Configuration.Env();
    @Autowired Db db;
    @Autowired Bootstrap bootstrap;
    @Autowired AuthService auth;
    @Autowired WorkspaceService workspace;
    @Autowired RagService rag;
    @Autowired QueryPolicy policy;
    @Autowired HistoryService history;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource dataSource;
    UUID user,other,admin,orbit,beacon;
    String userToken,adminToken;
    static Connection connection()throws Exception{
        return DriverManager.getConnection("jdbc:postgresql://"+SETTINGS.get("DB_HOST","127.0.0.1")+":"+SETTINGS.get("DB_PORT","5435")+"/"+SETTINGS.get("DB_NAME","firstday_java_db"),SETTINGS.get("DB_USER","firstday_user"),SETTINGS.get("DB_PASSWORD",""));
    }
    @DynamicPropertySource static void schema(DynamicPropertyRegistry registry)throws Exception{
        if(!SCHEMA.matches("java_test_[a-f0-9]{32}"))throw new IllegalStateException("Invalid isolated schema");
        try(var connection=connection();var statement=connection.createStatement()){
            statement.execute("CREATE SCHEMA "+SCHEMA);
            statement.execute("SET search_path TO "+SCHEMA+",public");
            for(String name:List.of("schema.sql","002_auth_sessions.sql","004_chat_messages_compat.sql","005_project_onboarding.sql","006_project_assignments.sql")){
                String sql;
                try(var stream=new ClassPathResource("db/"+name).getInputStream()){sql=new String(stream.readAllBytes(),StandardCharsets.UTF_8).replace("public.",SCHEMA+".");}
                ScriptUtils.executeSqlScript(connection,new EncodedResource(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8));
            }
        }
        System.setProperty("firstday.testSchema",SCHEMA);
    }
    @BeforeAll void fixture()throws Exception{
        assertEquals(SCHEMA,db.one("SELECT current_schema() AS name").get("name"),"Integration tests must use their isolated schema");
        bootstrap.seedProjects();bootstrap.index(false);
        user=employee("user");other=employee("other");admin=employee("admin");
        db.update("UPDATE employees SET role='ADMIN' WHERE id=?",admin);
        orbit=Db.id(db.one("SELECT id FROM projects WHERE slug='orbitflow'").get("id"));
        beacon=Db.id(db.one("SELECT id FROM projects WHERE slug='beacon'").get("id"));
        workspace.assign(user,orbit,new ApiDtos.Assignment("ENGINEERING",true),admin);
        workspace.assign(user,beacon,new ApiDtos.Assignment("GENERAL",true),admin);
        userToken=auth.session(user);adminToken=auth.session(admin);
    }
    UUID employee(String suffix){
        return Db.id(auth.register(new ApiDtos.Register("Synthetic Java "+suffix,suffix+"@example.invalid","Synthetic-test-password-123","JAVA","EMPLOYEE","PROJECT")).get("user") instanceof Map<?,?> row?row.get("id"):null);
    }
    @AfterAll void cleanup()throws Exception{
        if(dataSource instanceof HikariDataSource pool)pool.close();
        try(var connection=connection();var statement=connection.createStatement()){
            statement.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");
            for(UUID fixtureUser:Arrays.asList(user,other,admin)){
                if(fixtureUser==null)continue;
                try(var check=connection.prepareStatement("""
                    SELECT EXISTS(SELECT 1 FROM public.employees WHERE id=?)
                        OR EXISTS(SELECT 1 FROM public.chat_messages WHERE employee_id=?)
                        OR EXISTS(SELECT 1 FROM public.project_memberships WHERE employee_id=?) AS leaked
                    """)){
                    check.setObject(1,fixtureUser);check.setObject(2,fixtureUser);check.setObject(3,fixtureUser);
                    try(var result=check.executeQuery()){
                        result.next();assertFalse(result.getBoolean(1),"Synthetic integration records leaked into the application schema");
                    }
                }
            }
            try(var check=connection.prepareStatement("SELECT count(*) FROM information_schema.schemata WHERE schema_name=?")){
                check.setString(1,SCHEMA);
                try(var result=check.executeQuery()){result.next();assertEquals(0,result.getInt(1),"Temporary schema was not removed");}
            }
        }finally{System.clearProperty("firstday.testSchema");}
    }
    static Stream<Arguments> sourceCases()throws Exception{return fixtureCases("source-cases.json");}
    static Stream<Arguments> answerCases()throws Exception{return fixtureCases("answer-cases.json");}
    static Stream<Arguments> fixtureCases(String path)throws Exception{
        try(var stream=new ClassPathResource(path).getInputStream()){
            JsonNode cases=new ObjectMapper().readTree(stream);
            List<Arguments> result=new ArrayList<>();cases.forEach(c->result.add(Arguments.of(c.path("id").asText(),c)));return result.stream();
        }
    }
    @ParameterizedTest(name="{0}")@MethodSource("sourceCases")
    void sourceRegression(String id,JsonNode scenario){
        var selected=rag.retrieve(scenario.path("question").asText(),null,user);
        Set<String> paths=selected.stream().map(d->Db.text(d,"file_path")).collect(Collectors.toSet());
        if(scenario.path("expect_abstention").asBoolean())assertTrue(paths.isEmpty(),id);
        else{
            for(JsonNode expected:scenario.path("expected"))assertTrue(paths.contains(expected.asText()),id+" expected "+expected.asText()+" but found "+paths);
            Set<String> allowed=new HashSet<>();scenario.path("allowed").forEach(p->allowed.add(p.asText()));assertTrue(allowed.containsAll(paths),id+" contains unrelated sources");
        }
    }
    @ParameterizedTest(name="{0}")@MethodSource("answerCases")
    void answerRegression(String id,JsonNode scenario){
        String scope=scenario.path("scope").asText(),q=scenario.path("question").asText();
        UUID project=scope.equals("orbitflow")?orbit:scope.equals("beacon")?beacon:null;
        if(!scenario.path("previous").isEmpty()&&ChatService.followup(q)&&policy.tools(q).isEmpty())q=scenario.path("previous").get(scenario.path("previous").size()-1).asText()+"\nУточнение: "+q;
        var result=rag.answer(q,project,user,text->{});
        if(scenario.path("abstain").asBoolean()){
            assertTrue(result.sources().isEmpty(),id+" should have no sources");assertTrue(QueryPolicy.has(result.text(),"недостаточ|insufficient|не\\s+(?:указан|найден)"),id+" should explain missing evidence");
        }else{
            String expected=scenario.path("source").asText();
            assertTrue(result.sources().stream().anyMatch(source->expected.equals(source.get("file_path"))),id+" missing source "+expected);
            for(JsonNode alternatives:scenario.path("required")){
                boolean found=false;for(JsonNode expression:alternatives)if(QueryPolicy.has(result.text(),expression.asText()))found=true;
                assertTrue(found,id+" missing expected fact "+alternatives);
            }
        }
        for(JsonNode expression:scenario.path("forbidden"))assertFalse(QueryPolicy.has(result.text(),expression.asText()),id+" contains prohibited claim");
        if(scenario.path("response_language").asText().equals("en"))assertFalse(QueryPolicy.has(result.text(),"[а-я]"),id+" should be in English");
    }
    @Test @Transactional void registrationAndExistingPasswordCompatibility()throws Exception{
        var response=mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(json.writeValueAsString(new ApiDtos.Register("Synthetic Register","register@example.invalid","Synthetic-test-password-123","QA","INTERN","PROJECT")))).andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("EMPLOYEE")).andExpect(jsonPath("$.user.employment_type").value("INTERN")).andReturn();
        var value=json.readTree(response.getResponse().getContentAsString());
        String token=value.path("access_token").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\"register@example.invalid\",\"password\":\"Synthetic-test-password-123\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/projects").header("Authorization","Bearer "+token)).andExpect(content().json("[]"));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test @Transactional void employeesCannotAssignOrEnumerateAdmin()throws Exception{
        mvc.perform(get("/api/v1/admin/overview").header("Authorization","Bearer "+userToken)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/employees/"+user+"/projects/"+orbit).header("Authorization","Bearer "+userToken).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/overview").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.employees_total").value(3));
        mvc.perform(get("/api/v1/admin/employees").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
    }
    @Test @Transactional void membershipRevocationPreservesProgressAndHistory()throws Exception{
        var plan=workspace.projectPlan(orbit,user);
        @SuppressWarnings("unchecked") var tasks=(List<Map<String,Object>>)plan.get("tasks");
        UUID task=Db.id(tasks.getFirst().get("id"));
        workspace.projectTask(orbit,user,task,new ApiDtos.ProjectTask("DONE",""));
        UUID conversation=history.save(null,user,orbit,"Synthetic project question","Synthetic answer",List.of());
        workspace.assign(user,orbit,new ApiDtos.Assignment("ENGINEERING",false),admin);
        assertThrows(ApiException.class,()->history.get(conversation,user));
        assertThrows(ApiException.class,()->workspace.projectDocuments(orbit,user));
        assertEquals(409,assertThrows(ApiException.class,()->workspace.assign(user,orbit,new ApiDtos.Assignment("QA",true),admin)).status);
        workspace.assign(user,orbit,new ApiDtos.Assignment("ENGINEERING",true),admin);
        assertNotNull(history.get(conversation,user));assertEquals(1L,workspace.projectPlan(orbit,user).get("completed_tasks"));
        assertThrows(ApiException.class,()->history.get(conversation,other));
        assertThrows(ApiException.class,()->history.recent(conversation,user,beacon));
    }
    @Test @Transactional void privateDataAndRequestsRemainScoped()throws Exception{
        var profile=auth.current("Bearer "+userToken);
        var request=workspace.request(profile,new ApiDtos.Access("GitHub","Synthetic integration access request"));
        var otherProfile=auth.current("Bearer "+auth.session(other));
        assertEquals(0,workspace.requests(otherProfile).get("total"));
        mvc.perform(patch("/api/v1/admin/access-requests/"+request.get("id")+"/review").header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"status\":\"APPROVED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(patch("/api/v1/admin/access-requests/"+request.get("id")+"/review").header("Authorization","Bearer "+adminToken).contentType("application/json").content("{\"status\":\"REJECTED\"}")).andExpect(status().isConflict());
        assertThrows(ApiException.class,()->workspace.projectTask(orbit,other,UUID.randomUUID(),new ApiDtos.ProjectTask("DONE","")));
    }
    @Test void actualStreamingPersistsCompletedExchange()throws Exception{
        var request=mvc.perform(post("/api/v1/chat/stream").header("Authorization","Bearer "+userToken).contentType("application/json").content("{\"question\":\"Какой технологический стек используется?\",\"project_id\":\""+orbit+"\"}")).andExpect(request().asyncStarted()).andReturn();
        request.getAsyncResult(120000);
        var response=mvc.perform(asyncDispatch(request)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/x-ndjson")).andReturn();
        var events=response.getResponse().getContentAsString(StandardCharsets.UTF_8).lines().map(line->{try{return json.readTree(line);}catch(Exception e){throw new RuntimeException(e);}}).toList();
        assertEquals("status",events.getFirst().path("type").asText());assertEquals("done",events.getLast().path("type").asText());
        UUID conversation=UUID.fromString(events.getLast().path("conversation_id").asText());
        assertNotNull(history.get(conversation,user));assertThrows(ApiException.class,()->history.get(conversation,other));
    }
    @Test @Transactional void documentsAndPathsAreProtected()throws Exception{
        mvc.perform(get("/api/v1/documents").header("Authorization","Bearer "+userToken)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(71));
        mvc.perform(get("/api/v1/documents/content").param("path","../.env").header("Authorization","Bearer "+userToken)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects/"+orbit+"/documents").header("Authorization","Bearer "+adminToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/content").param("project_id",orbit.toString()).param("path","projects/orbitflow/environment.md").header("Authorization","Bearer "+userToken)).andExpect(status().isOk());
    }

    @Test @Transactional void realEmbeddingsRoundTripThroughPgvector(){
        var values=bootstrap.ollama.embedding("Synthetic local knowledge retrieval test");
        assertEquals(768,values.size());
        String vector=db.json(values);
        UUID chunk=Db.id(db.one("SELECT id FROM document_chunks ORDER BY id LIMIT 1").get("id"));
        db.update("UPDATE document_chunks SET embedding=CAST(? AS vector) WHERE id=?",vector,chunk);
        var result=db.one("SELECT vector_dims(embedding) AS dimensions,embedding <=> CAST(? AS vector) AS distance FROM document_chunks WHERE id=?",vector,chunk);
        assertEquals(768,((Number)result.get("dimensions")).intValue());
        assertEquals(0.0,((Number)result.get("distance")).doubleValue(),0.00001);
    }

    @Test @Transactional void internshipRejectsProjectInformationAndSpoofedProjectScope()throws Exception{
        db.update("UPDATE employees SET onboarding_mode='COMPANY',employment_type='INTERN' WHERE id=?",user);
        for(String question:List.of("Из чего состоит проект?","Расскажи про OrbitFlow","Кто отвечает за Beacon?")){
            mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+userToken)
                .contentType("application/json").content(json.writeValueAsString(Map.of("question",question))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.answer").value(QueryPolicy.internshipBoundary(question)));
        }
        mvc.perform(get("/api/v1/projects").header("Authorization","Bearer "+userToken))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/projects/"+orbit+"/onboarding").header("Authorization","Bearer "+userToken))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/projects/"+orbit+"/documents").header("Authorization","Bearer "+userToken))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/documents/content").param("project_id",orbit.toString())
            .param("path","projects/orbitflow/architecture.md").header("Authorization","Bearer "+userToken))
            .andExpect(status().isForbidden());
        String spoofed=json.writeValueAsString(Map.of("question","Из чего состоит проект?","project_id",orbit,"onboarding_mode","PROJECT"));
        mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+userToken)
            .contentType("application/json").content(spoofed)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/chat/stream").header("Authorization","Bearer "+userToken)
            .contentType("application/json").content(spoofed)).andExpect(status().isForbidden());
    }

    @Test @Transactional void internshipHistorySurvivesReloadAndQuotedFollowupUsesPreviousTrainingTopic()throws Exception{
        db.update("UPDATE employees SET onboarding_mode='COMPANY',employment_type='INTERN' WHERE id=?",user);
        var first=mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+userToken)
            .contentType("application/json").content(json.writeValueAsString(Map.of("question","Как проходит интершип?"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sources[0].file_path").value("01_hr/thirty-day-plan.md")).andReturn();
        String id=json.readTree(first.getResponse().getContentAsString()).path("conversation_id").asText();
        String question="«А кто за это отвечает?";
        mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+userToken)
            .contentType("application/json").content(json.writeValueAsString(Map.of("question",question,"conversation_id",id))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sources[0].file_path").value("01_hr/thirty-day-plan.md"))
            .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("People Operations")));
        mvc.perform(get("/api/v1/chat/conversations/"+id).header("Authorization","Bearer "+userToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(4))
            .andExpect(jsonPath("$.messages[2].content").value(question))
            .andExpect(jsonPath("$.messages[3].content").value(org.hamcrest.Matchers.containsString("People Operations")));
        mvc.perform(get("/api/v1/chat/conversations/"+id).header("Authorization","Bearer "+auth.session(other)))
            .andExpect(status().isNotFound());
    }

    @Test @Transactional void selectedProjectRetainsContextThroughQuotedOwnershipFollowup()throws Exception{
        var first=mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+userToken)
            .contentType("application/json").content(json.writeValueAsString(Map.of("question","Из каких частей состоит проект?","project_id",orbit))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("gateway"))).andReturn();
        String id=json.readTree(first.getResponse().getContentAsString()).path("conversation_id").asText();
        mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+userToken)
            .contentType("application/json").content(json.writeValueAsString(Map.of(
                "question","«А кто за них отвечает?","conversation_id",id,"project_id",orbit))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("Atlas")))
            .andExpect(jsonPath("$.sources[0].file_path").value("projects/orbitflow/architecture.md"));
    }

    @Test @Transactional void internshipCannotOpenOldProjectChatButPreservesItsMessages()throws Exception{
        UUID conversation=history.save(null,user,orbit,"Synthetic project question","Synthetic project answer",List.of());
        db.update("UPDATE employees SET onboarding_mode='COMPANY' WHERE id=?",user);
        mvc.perform(get("/api/v1/chat/conversations").param("project_id",orbit.toString())
            .header("Authorization","Bearer "+userToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/chat/conversations/"+conversation)
            .header("Authorization","Bearer "+userToken)).andExpect(status().isForbidden());
        assertEquals(2,Db.count(db.one("SELECT count(*) AS count FROM chat_messages WHERE conversation_id=?",conversation).get("count")));
    }
}
