package com.novatech.firstday;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiContractTest {
    MockMvc mvc;AuthService auth;Db db;WorkspaceService workspace;HistoryService history;ChatService chat;
    @BeforeEach void setup(){
        auth=mock(AuthService.class);db=mock(Db.class);workspace=mock(WorkspaceService.class);history=mock(HistoryService.class);chat=mock(ChatService.class);
        when(auth.current(null)).thenThrow(new ApiException(401,"Sign in"));
        when(auth.admin(null)).thenThrow(new ApiException(401,"Sign in"));
        mvc=MockMvcBuilders.standaloneSetup(new ApiController(auth,workspace,db),new ChatController(auth,chat,history,db)).setControllerAdvice(new ApiException.Handler()).build();
    }
    @ParameterizedTest @ValueSource(strings={"/api/v1/auth/me","/api/v1/onboarding","/api/v1/projects","/api/v1/documents","/api/v1/access-requests","/api/v1/chat/conversations","/api/v1/admin/overview","/api/v1/admin/employees","/api/v1/admin/projects","/api/v1/admin/access-requests"})
    void privateEndpointsRequireAuthentication(String path)throws Exception{mvc.perform(get(path)).andExpect(status().isUnauthorized());verifyNoInteractions(workspace,history,chat);}
    @Test void healthHasCompatibleShape()throws Exception{mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok")).andExpect(jsonPath("$.streaming").value("ollama"));}
    @Test void readinessChecksDatabase()throws Exception{when(db.one("SELECT 1 AS connected")).thenReturn(Map.of("connected",1));mvc.perform(get("/api/v1/ready")).andExpect(status().isOk());verify(db).one("SELECT 1 AS connected");}
    @ParameterizedTest @ValueSource(strings={"/api/v1/chat","/api/v1/chat/stream"})
    void chatsRequireAuthentication(String path)throws Exception{mvc.perform(post(path).contentType("application/json").content("{\"question\":\"Hello\"}")).andExpect(status().isUnauthorized());}
    @Test void registrationDoesNotAcceptBadFields()throws Exception{mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"full_name\":\"x\",\"email\":\"x\",\"password\":\"tiny\",\"job_track\":\"ADMIN\"}")).andExpect(status().isUnprocessableEntity());verifyNoInteractions(auth);}
    @Test void databaseErrorsDoNotExposeSecrets()throws Exception{when(db.one("SELECT 1 AS connected")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("sensitive internal diagnostic"));mvc.perform(get("/api/v1/ready")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.detail").value("Database unavailable or schema not initialized"));}
    @Test void invalidTaskStatusRejected()throws Exception{mvc.perform(patch("/api/v1/onboarding/tasks/"+UUID.randomUUID()).contentType("application/json").content("{\"status\":\"ADMIN\"}")).andExpect(status().isUnprocessableEntity());}
    @Test void streamPreservesNdjson()throws Exception{
        var user=Db.map("id",UUID.randomUUID());when(auth.current("Bearer synthetic")).thenReturn(user);
        when(db.json(any())).thenAnswer(call->new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(call.getArgument(0)));
        when(chat.respond(any(),eq(user),any())).thenAnswer(call->{
            java.util.function.Consumer<String> output=call.getArgument(2);output.accept("Hello");
            return new ChatService.Result("Hello",List.of(),UUID.randomUUID());
        });
        var result=mvc.perform(post("/api/v1/chat/stream").header("Authorization","Bearer synthetic").contentType("application/json").content("{\"question\":\"Hello\"}")).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/x-ndjson")).andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"done\"")));
    }
}
