package com.novatech.firstday;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class OllamaClient {
    final ObjectMapper json;final Configuration.Env env;
    final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public OllamaClient(ObjectMapper json,Configuration.Env env){this.json=json;this.env=env;}
    HttpRequest request(String path,Object payload){
        try{return HttpRequest.newBuilder(URI.create(env.get("OLLAMA_URL","http://127.0.0.1:11434").replaceAll("/+$","")+path))
            .timeout(Duration.ofSeconds(300)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();}
        catch(Exception e){throw new ApiException(503,"Ollama configuration invalid");}
    }
    public List<Double> embedding(String text){
        try{
            var response=client.send(request("/api/embed",Map.of("model","nomic-embed-text","input",text)),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new IOException();
            JsonNode vector=json.readTree(response.body()).path("embeddings").path(0);
            if(vector.size()!=768)throw new IOException();
            List<Double> values=new ArrayList<>();
            for(JsonNode value:vector){double number=value.doubleValue();if(!Double.isFinite(number))throw new IOException();values.add(number);}return values;
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ApiException(503,"Ollama embedding interrupted");}
        catch(Exception e){throw new ApiException(503,"Ollama embeddings unavailable");}
    }
    public void generate(String system,String prompt,Consumer<String> output){
        String model=env.get("CHAT_MODEL","qwen3:1.7b");
        Object payload=Db.map("model",model,"system",system,"prompt",prompt,"stream",true,"think",false,"options",Map.of("temperature",0,"num_predict",1200));
        try{
            var response=client.send(request("/api/generate",payload),HttpResponse.BodyHandlers.ofInputStream());
            try(var body=response.body()){
                if(response.statusCode()!=200)throw new ApiException(503,"Ollama generation unavailable");
                boolean done=false;
                try(var reader=new BufferedReader(new InputStreamReader(body,java.nio.charset.StandardCharsets.UTF_8))){
                    for(String line;(line=reader.readLine())!=null;){
                        if(line.isBlank())continue;
                        JsonNode event=json.readTree(line);
                        if(event.has("error"))throw new ApiException(503,"Ollama generation failed");
                        String text=event.path("response").asText("");
                        if(!text.isEmpty())output.accept(text);
                        if(event.path("done").asBoolean()){done=true;break;}
                    }
                }
                if(!done)throw new ApiException(503,"Ollama returned an incomplete stream");
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ApiException(503,"Ollama generation interrupted");}
        catch(UncheckedIOException e){throw e;}
        catch(ApiException e){throw e;}
        catch(Exception e){throw new ApiException(503,"Ollama generation unavailable");}
    }
}
