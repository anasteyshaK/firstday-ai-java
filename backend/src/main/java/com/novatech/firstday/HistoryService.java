package com.novatech.firstday;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryService {
    final Db db; final WorkspaceService workspace;
    public HistoryService(Db db,WorkspaceService workspace){this.db=db;this.workspace=workspace;}
    public Map<String,Object> owned(UUID conversation,UUID employee){
        var row=db.optional("SELECT id,title,project_id FROM chat_conversations WHERE id=? AND employee_id=?",conversation,employee);
        if(row==null)throw ApiException.missing("Conversation not found");
        UUID project=Db.id(row.get("project_id"));if(project!=null)workspace.membership(project,employee);return row;
    }
    public List<String> recent(UUID conversation,UUID employee,UUID project){
        if(conversation==null)return List.of();
        var info=owned(conversation,employee);
        if(!Objects.equals(project,Db.id(info.get("project_id"))))throw new ApiException(409,"Start a new chat for the selected project");
        return db.rows("SELECT content FROM chat_messages WHERE conversation_id=? AND employee_id=? AND sender='USER' ORDER BY created_at DESC,id DESC LIMIT 8",conversation,employee).stream().map(r->Db.text(r,"content")).toList().reversed();
    }
    public List<Map<String,Object>> context(UUID conversation,UUID employee,UUID project){
        if(conversation==null)return List.of();
        var info=owned(conversation,employee);
        if(!Objects.equals(project,Db.id(info.get("project_id"))))throw new ApiException(409,"Start a new chat for the selected project");
        return db.rows("SELECT lower(sender) AS role,content FROM chat_messages WHERE conversation_id=? AND employee_id=? ORDER BY created_at DESC,id DESC LIMIT 12",conversation,employee).reversed();
    }
    public List<Map<String,Object>> list(UUID employee,UUID project){
        if(project!=null)workspace.membership(project,employee);
        return db.rows("SELECT id,title,project_id,created_at,updated_at FROM chat_conversations WHERE employee_id=? AND project_id IS NOT DISTINCT FROM CAST(? AS UUID) ORDER BY updated_at DESC,id DESC LIMIT 100",employee,project);
    }
    public Map<String,Object> get(UUID conversation,UUID employee){
        var info=owned(conversation,employee);
        info.put("messages",db.rows("SELECT id,lower(sender) AS role,content,coalesce(sources,'[]'::jsonb) AS sources,created_at FROM chat_messages WHERE conversation_id=? AND employee_id=? ORDER BY created_at,id LIMIT 300",conversation,employee));return info;
    }
    @Transactional
    public UUID save(UUID conversation,UUID employee,UUID project,String question,String answer,List<Map<String,Object>> sources){
        if(project!=null)workspace.membership(project,employee);
        if(conversation==null){
            String title=question.replaceAll("\\s+"," ").strip();if(title.length()>80)title=title.substring(0,77)+"…";
            conversation=Db.id(db.one("INSERT INTO chat_conversations(employee_id,title,project_id) VALUES (?,?,?) RETURNING id",employee,title,project).get("id"));
        }else recent(conversation,employee,project);
        db.update("INSERT INTO chat_messages(employee_id,conversation_id,sender,content,sources,created_at) VALUES (?,?,'USER',?,'[]'::jsonb,clock_timestamp())",employee,conversation,question);
        db.update("INSERT INTO chat_messages(employee_id,conversation_id,sender,content,sources,created_at) VALUES (?,?,'ASSISTANT',?,CAST(? AS jsonb),clock_timestamp())",employee,conversation,answer,db.json(sources));
        db.update("UPDATE chat_conversations SET updated_at=NOW() WHERE id=? AND employee_id=?",conversation,employee);return conversation;
    }
    @Transactional
    public void delete(UUID conversation,UUID employee){
        owned(conversation,employee);
        db.update("DELETE FROM chat_messages WHERE conversation_id=? AND employee_id=?",conversation,employee);
        db.update("DELETE FROM chat_conversations WHERE id=? AND employee_id=?",conversation,employee);
    }
}
