package com.novatech.firstday;

import java.util.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    public record Result(String answer,List<Map<String,Object>> sources,UUID conversation_id) {}
    final HistoryService history;final WorkspaceService workspace;final PersonalService personal;final RagService rag;final QueryPolicy policy;
    public ChatService(HistoryService history,WorkspaceService workspace,PersonalService personal,RagService rag,QueryPolicy policy){
        this.history=history;this.workspace=workspace;this.personal=personal;this.rag=rag;this.policy=policy;
    }
    public static boolean internship(Map<String,Object> user){return "COMPANY".equals(user.get("onboarding_mode"));}
    public static void allowProject(Map<String,Object> user,UUID project){
        if(internship(user)&&project!=null)throw new ApiException(403,"Project conversations are unavailable in Internship mode");
    }
    public Result respond(ApiDtos.Chat request,Map<String,Object> user,Consumer<String> output){
        String original=request.text(),question=QueryPolicy.cleanQuestion(original);
        UUID employee=Db.id(user.get("id")),project=request.project_id();
        boolean internship=internship(user);
        allowProject(user,project);
        if(project!=null)workspace.membership(project,employee);
        var previous=history.recent(request.conversation_id(),employee,project);
        String contextual=contextualQuestion(question,previous);
        String text;List<Map<String,Object>> sources=List.of();
        if(QueryPolicy.projectQuestion(contextual)&&(internship||project==null)){
            text=internship?QueryPolicy.internshipBoundary(question):QueryPolicy.selectProject(question);
            output.accept(text);
        }else{
            String personalQ=policy.personalQuestion(question,previous);
            text=personal.answer(personalQ,user,project);
            if(text!=null)output.accept(text);
            else{
                var turns=request.conversation_id()!=null?history.context(request.conversation_id(),employee,project):List.<Map<String,Object>>of();
                RagService.Answer answer;
                if(internship||!turns.isEmpty())answer=rag.answerWithContext(contextual,project,employee,internship,turns,output);
                else answer=rag.answer(contextual,project,employee,output);
                text=answer.text();sources=answer.sources();
            }
        }
        UUID conversation=history.save(request.conversation_id(),employee,project,original,text,sources);
        return new Result(text,sources,conversation);
    }
    String contextualQuestion(String question,List<String> previous){
        if(previous.isEmpty()||!followup(question)||explicitTopic(question))return question;
        int anchor=previous.size()-1;
        while(anchor>0&&followup(previous.get(anchor))&&!explicitTopic(previous.get(anchor)))anchor--;
        if(policy.intent(previous.get(anchor))!=null)return question;
        StringBuilder context=new StringBuilder(QueryPolicy.cleanQuestion(previous.get(anchor)));
        for(int i=anchor+1;i<previous.size();i++)context.append("\nУточнение: ").append(QueryPolicy.cleanQuestion(previous.get(i)));
        return context.append("\nУточнение: ").append(question).toString();
    }
    boolean explicitTopic(String q){
        return !policy.tools(q).isEmpty()||QueryPolicy.internshipOverview(q)
            ||QueryPolicy.has(q,"orbitflow|beacon|интер(?:н)?шип|стажиров|internship|отпуск|зарплат|salary|vacation|компани");
    }
    static boolean followup(String q){return QueryPolicy.followup(q);}
}
