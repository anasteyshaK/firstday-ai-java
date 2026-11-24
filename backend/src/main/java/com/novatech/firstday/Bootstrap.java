package com.novatech.firstday;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;

@Component
public class Bootstrap implements ApplicationRunner {
    final Db db;final OllamaClient ollama;final DataSource source;final Environment properties;
    final TransactionTemplate transaction;final ObjectMapper json;
    public Bootstrap(Db db,OllamaClient ollama,DataSource source,Environment properties,PlatformTransactionManager manager,ObjectMapper json){
        this.db=db;this.ollama=ollama;this.source=source;this.properties=properties;this.transaction=new TransactionTemplate(manager);this.json=json;
    }
    public void run(ApplicationArguments args)throws Exception{
        String command=properties.getProperty("firstday.command","");
        if(command.isEmpty())return;
        switch(command){
            case "init" -> {
                try(var connection=source.getConnection()){
                    for(String name:List.of("schema.sql","002_auth_sessions.sql","004_chat_messages_compat.sql","005_project_onboarding.sql","006_project_assignments.sql"))
                        ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.support.EncodedResource(new ClassPathResource("db/"+name),StandardCharsets.UTF_8));
                }
                seedProjects();System.out.println("Database schema and synthetic demonstration projects prepared.");
            }
            case "index" -> index(true);
            case "promote-admin" -> {
                var console=System.console();
                if(console==null)throw new IllegalStateException("Run administrator setup in an interactive terminal");
                String email=console.readLine("Email of your existing account: ").strip().toLowerCase(Locale.ROOT);
                var user=db.one("SELECT id,role FROM employees WHERE email=?",email);
                if(!"PROMOTE".equals(console.readLine("Type PROMOTE to grant ADMIN access: ")))return;
                db.update("UPDATE employees SET role='ADMIN' WHERE id=?",Db.id(user.get("id")));
                System.out.println("Administrator role granted. Sign in again.");
            }
            default -> throw new IllegalArgumentException("Unknown setup command");
        }
    }
    public static Path root(){Path root=Path.of("").toAbsolutePath();return root.getFileName().toString().equals("backend")?root.getParent():root;}
    public static String body(String raw){String text=raw.replace("\r\n","\n");if(text.startsWith("\uFEFF"))text=text.substring(1);if(text.startsWith("---\n")){int end=text.indexOf("\n---",4);if(end>=0)text=text.substring(end+4).strip();}return text;}
    public static String metadata(String raw,String key,String fallback){
        var matcher=java.util.regex.Pattern.compile("(?m)^"+java.util.regex.Pattern.quote(key)+":[ \\t]*([^\\n]+)").matcher(raw.split("\\n---",2)[0]);
        return matcher.find()?matcher.group(1).strip().replaceAll("^['\"]|['\"]$",""):fallback;
    }
    public static List<String> chunks(String content){
        List<String> chunks=new ArrayList<>();StringBuilder current=new StringBuilder();
        for(String paragraph:content.split("\\n\\s*\\n")){
            if(current.length()+paragraph.length()>1200&&!current.isEmpty()){chunks.add(current.toString().strip());current.setLength(0);}
            if(!current.isEmpty())current.append("\n\n");current.append(paragraph);
        }
        if(!current.isEmpty())chunks.add(current.toString().strip());return chunks;
    }
    public void index(boolean embeddings)throws Exception{
        Path directory=root().resolve("knowledge_base");
        try(var paths=Files.walk(directory)){
            for(Path path:paths.filter(p->p.toString().endsWith(".md")&&!p.getFileName().toString().equalsIgnoreCase("README.md")).sorted().toList()){
                String raw=Files.readString(path),content=body(raw),relative=directory.relativize(path).toString().replace('\\','/');
                String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
                String title=metadata(raw,"title",path.getFileName().toString()),category=relative.split("/")[0],access=metadata(raw,"access_level","EMPLOYEE");
                transaction.executeWithoutResult(status->{
                    var doc=db.one("INSERT INTO documents(title,file_path,category,access_level,content_hash) VALUES (?,?,?,?,?) ON CONFLICT(file_path) DO UPDATE SET title=EXCLUDED.title,category=EXCLUDED.category,access_level=EXCLUDED.access_level,content_hash=EXCLUDED.content_hash,updated_at=NOW() RETURNING id",title,relative,category,access,hash);
                    UUID id=Db.id(doc.get("id"));db.update("DELETE FROM document_chunks WHERE document_id=?",id);
                    int index=0;
                    for(String chunk:chunks(content)){
                        String vector=embeddings?db.json(ollama.embedding(chunk)):null;
                        db.update("INSERT INTO document_chunks(document_id,chunk_index,content,embedding) VALUES (?,?,?,CAST(? AS vector))",id,index++,chunk,vector);
                    }
                });
                if(embeddings)System.out.println("Indexed "+relative);
            }
        }
        System.out.println("Knowledge documents indexed"+(embeddings?" with local Ollama embeddings.":" without embeddings for isolated test fixtures."));
    }
    public String projectText(List<String> paths)throws Exception{
        List<String> sections=new ArrayList<>();sections.add("> Синтетический демо-проект NovaTech. Инструкции и системы вымышлены.");
        for(String path:paths){String text=body(Files.readString(root().resolve("knowledge_base").resolve(path)));int cut=text.indexOf("## Контрольный результат");if(cut>=0)text=text.substring(0,cut).strip();sections.add("Исходный учебный материал: "+path+"\n\n"+text);}
        return String.join("\n\n---\n\n",sections);
    }
    @SuppressWarnings("unchecked") public void seedProjects()throws Exception{
        Map<String,Map<String,Object>> projects;
        try(var input=new ClassPathResource("demo-projects.json").getInputStream()){projects=json.readValue(input,new TypeReference<>(){});}
        for(var entry:projects.entrySet()){
            var data=entry.getValue();
            UUID id=Db.id(db.one("INSERT INTO projects(slug,name,description,is_demo) VALUES (?,?,?,TRUE) ON CONFLICT(slug) DO UPDATE SET name=EXCLUDED.name,description=EXCLUDED.description RETURNING id",entry.getKey(),data.get("name"),data.get("description")).get("id"));
            for(var document:((Map<String,List<Object>>)data.get("docs")).entrySet()){
                var values=document.getValue();
                String content=projectText((List<String>)values.get(2));
                db.update("INSERT INTO project_documents(project_id,file_path,title,category,content) VALUES (?,?,?,?,?) ON CONFLICT(project_id,file_path) DO UPDATE SET title=EXCLUDED.title,category=EXCLUDED.category,content=EXCLUDED.content,updated_at=NOW()",id,"projects/"+entry.getKey()+"/"+document.getKey(),values.get(0),values.get(1),content);
            }
        }
    }
}
