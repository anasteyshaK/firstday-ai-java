package com.novatech.firstday;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OllamaClientTest {
    HttpServer server;OllamaClient client;String generation;
    @BeforeEach void setup()throws Exception{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        generation="{\"response\":\"Hello \",\"done\":false}\n{\"response\":\"world\",\"done\":false}\n{\"done\":true}\n";
        server.createContext("/api/generate",exchange->{exchange.getRequestBody().readAllBytes();byte[] bytes=generation.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);try(var stream=exchange.getResponseBody()){stream.write(bytes);}});
        server.createContext("/api/embed",exchange->{exchange.getRequestBody().readAllBytes();byte[] bytes=("{\"embeddings\":[["+String.join(",",Collections.nCopies(768,"0.1"))+"]]}").getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);try(var stream=exchange.getResponseBody()){stream.write(bytes);}});
        server.start();
        var env=mock(Configuration.Env.class);
        when(env.get(eq("OLLAMA_URL"),anyString())).thenReturn("http://127.0.0.1:"+server.getAddress().getPort());
        when(env.get(eq("CHAT_MODEL"),anyString())).thenReturn("synthetic-model");
        client=new OllamaClient(new ObjectMapper(),env);
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void streamsChunksAndRequiresDone(){var chunks=new ArrayList<String>();client.generate("synthetic system","synthetic question",chunks::add);assertEquals(List.of("Hello ","world"),chunks);}
    @Test void incompleteGenerationFails(){generation="{\"response\":\"Partial\"}\n";assertThrows(ApiException.class,()->client.generate("system","question",text->{}));}
    @Test void reads768DimensionalVector(){assertEquals(768,client.embedding("synthetic documentation question").size());}
    @Test void providerErrorDoesNotExposePayload(){generation="{\"error\":\"internal provider detail\"}\n";var error=assertThrows(ApiException.class,()->client.generate("system","question",text->{}));assertFalse(error.getMessage().contains("internal provider detail"));}
}
