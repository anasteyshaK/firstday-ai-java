package com.novatech.firstday;

import jakarta.validation.Valid;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    final AuthService auth;final ChatService chat;final HistoryService history;final Db db;
    public ChatController(AuthService auth,ChatService chat,HistoryService history,Db db){this.auth=auth;this.chat=chat;this.history=history;this.db=db;}
    @PostMapping public Object chat(@RequestHeader(value="Authorization",required=false)String header,@Valid @RequestBody ApiDtos.Chat request){return chat.respond(request,auth.current(header),text->{});}
    @PostMapping(value="/stream",produces="application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> stream(@RequestHeader(value="Authorization",required=false)String header,@Valid @RequestBody ApiDtos.Chat request){
        Map<String,Object> user=auth.current(header);request.text();ChatService.allowProject(user,request.project_id());
        StreamingResponseBody body=output->{
            event(output,Db.map("type","status","message","Searching documents and preparing an answer…"));
            try{
                var response=chat.respond(request,user,text->event(output,Db.map("type","delta","text",text)));
                event(output,Db.map("type","done","conversation_id",response.conversation_id(),"sources",response.sources()));
            }catch(UncheckedIOException ex){throw ex.getCause();}
            catch(Exception ex){event(output,Db.map("type","error","message",ex instanceof ApiException?ex.getMessage():"The answer could not be completed. Please retry."));}
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-ndjson")).header("Cache-Control","no-cache").header("X-Accel-Buffering","no").body(body);
    }
    void event(OutputStream output,Object event){try{output.write((db.json(event)+"\n").getBytes(StandardCharsets.UTF_8));output.flush();}catch(IOException e){throw new UncheckedIOException(e);}}
    @GetMapping("/conversations")public Object list(@RequestHeader(value="Authorization",required=false)String header,@RequestParam(required=false)UUID project_id){var user=auth.current(header);ChatService.allowProject(user,project_id);return history.list(Db.id(user.get("id")),project_id);}
    @GetMapping("/conversations/{id}")public Object get(@RequestHeader(value="Authorization",required=false)String header,@PathVariable UUID id){var user=auth.current(header);var conversation=history.owned(id,Db.id(user.get("id")));ChatService.allowProject(user,Db.id(conversation.get("project_id")));return history.get(id,Db.id(user.get("id")));}
    @DeleteMapping("/conversations/{id}")@ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(value="Authorization",required=false)String header,@PathVariable UUID id){history.delete(id,Db.id(auth.current(header).get("id")));}
}
