package com.novatech.firstday;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PersonalService {
    final WorkspaceService workspace;final QueryPolicy policy;
    public PersonalService(WorkspaceService workspace,QueryPolicy policy){this.workspace=workspace;this.policy=policy;}
    @SuppressWarnings("unchecked")
    public String answer(String q,Map<String,Object> user,UUID project){
        String intent=policy.intent(q);
        if(project!=null&&QueryPolicy.has(q,"(?:мои|мой|my).*(?:блокер|blocker)|что.*мешает"))intent="blockers";
        if(intent==null)return null;
        boolean english=QueryPolicy.english(q);
        if(intent.equals("requests")){
            var items=(List<Map<String,Object>>)workspace.requests(user).get("items");
            var tools=policy.tools(q);
            var filtered=items.stream().filter(r->tools.isEmpty()||tools.stream().anyMatch(tool->Db.text(r,"system_name").toLowerCase(Locale.ROOT).contains(tool))).toList();
            if(filtered.isEmpty())return english?"You have no matching access requests.":"Подходящих заявок на доступ не найдено.";
            StringBuilder text=new StringBuilder(english?"Your access requests:\n\n":"Ваши заявки на доступ:\n\n");
            filtered.forEach(r->text.append("- ").append(r.get("system_name")).append(": ").append(status(Db.text(r,"status"),english)).append('\n'));
            return text.toString().strip();
        }
        Map<String,Object> plan=project==null?workspace.plan(user):workspace.projectPlan(project,Db.id(user.get("id")));
        var tasks=(List<Map<String,Object>>)plan.get("tasks");
        var remaining=tasks.stream().filter(t->!"DONE".equals(t.get("status"))).toList();
        if(intent.equals("blockers")){
            var blocked=tasks.stream().filter(t->"BLOCKED".equals(t.get("status"))).toList();
            if(blocked.isEmpty())return english?"You have no recorded blockers.":"Сохранённых блокеров нет.";
            return String.join("\n\n",blocked.stream().map(t->"**"+t.get("title")+"**\n"+Db.text(t,"blocker_note")).toList());
        }
        if(intent.equals("progress"))return english?
            "Your progress: "+plan.get("progress_percent")+"%. Completed "+plan.get("completed_tasks")+" of "+plan.get("total_tasks")+" tasks.":
            "Ваш прогресс: "+plan.get("progress_percent")+"%. Выполнено "+plan.get("completed_tasks")+" из "+plan.get("total_tasks")+" задач.";
        if(intent.startsWith("first")||intent.startsWith("next")){
            var list=intent.startsWith("first")?tasks:remaining;
            if(list.isEmpty())return english?"All recorded tasks are complete.":"Все сохранённые задачи выполнены.";
            var task=list.getFirst();
            String title=task.get("title").toString();
            if(intent.endsWith("status"))return "**"+title+"** — "+status(Db.text(task,"status"),english)+".";
            return (english?"Your task: ":"Ваша задача: ")+"**"+title+"**\n\n"+Db.text(task,"description")+"\n\n"+status(Db.text(task,"status"),english);
        }
        var selected=QueryPolicy.has(q,"выполнен|completed|finished")&&!QueryPolicy.has(q,"невыполнен|unfinished")?tasks.stream().filter(t->"DONE".equals(t.get("status"))).toList():remaining;
        if(selected.isEmpty())return english?"No matching tasks remain.":"Подходящих задач не осталось.";
        StringBuilder text=new StringBuilder(english?"Your tasks ("+selected.size()+"):\n\n":"Ваши задачи ("+selected.size()+"):\n\n");
        selected.forEach(t->text.append("- **").append(t.get("title")).append("** — ").append(status(Db.text(t,"status"),english)).append('\n'));
        return text.toString().strip();
    }
    static String status(String value,boolean english){
        return switch(value){
            case "DONE" -> english?"Completed":"Выполнена";
            case "IN_PROGRESS" -> english?"In progress":"В работе";
            case "BLOCKED" -> english?"Blocked":"Заблокирована";
            case "APPROVED" -> english?"Approved":"Одобрена";
            case "REJECTED" -> english?"Rejected":"Отклонена";
            case "PENDING" -> english?"Pending review":"Ожидает рассмотрения";
            default -> english?"Not started":"Не начата";
        };
    }
}
