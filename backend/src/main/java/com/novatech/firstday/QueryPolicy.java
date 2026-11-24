package com.novatech.firstday;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class QueryPolicy {
    final Map<String,Object> rules;
    public QueryPolicy(ObjectMapper mapper){
        try(var input=new ClassPathResource("query-policies.json").getInputStream()){rules=mapper.readValue(input,new TypeReference<>(){});}
        catch(Exception ex){throw new IllegalStateException("Cannot load query policies",ex);}
    }
    public static String cleanQuestion(String text){
        return text.strip().replaceAll("^[\\s«»\\\"'“”‘’]+|[\\s«»\\\"'“”‘’]+$","").strip();
    }
    public static boolean projectQuestion(String q){
        return has(q,"\\bпроект\\w*|\\bprojects?\\b|orbitflow|beacon|архитектур\\w*|architecture|компонент\\w*|components|технологическ\\w*\\s+стек|technology\\s+stack");
    }
    public static String internshipBoundary(String q){
        return english(q)?"In Internship mode I can help with your training plan, tasks, and company procedures. Project information is not available in this mode."
            :"В режиме Internship я помогаю со стажировкой, личным планом, задачами и общими процедурами компании. Информацию о проектах в этом режиме не предоставляю.";
    }
    public static String selectProject(String q){
        return english(q)?"Select an assigned project to discuss its details. Which project do you mean?"
            :"Чтобы обсудить проект, выберите его из назначенных вам проектов. О каком проекте идёт речь?";
    }
    public static boolean followup(String q){
        q=cleanQuestion(q).replaceFirst("(?iu)^(?:хорошо|понятно|ок(?:ей)?|спасибо|отлично|okay|ok|thanks)[,!.:\\s]+","");
        return q.length()<180&&has(q,"^(?:а\\s+(?:какой|какие|кто|где|что|если|как|зачем|почему|я|дальше|теперь)|он\\b|она\\b|они\\b|их\\b|это\\b|дальше\\b|что\\s+(?:дальше|это|после)|подробнее|можешь\\s+подробнее|расскажи\\s+подробнее|кто\\s+за\\s+(?:них|это)|and\\s+|what\\s+about|who\\s+owns|who\\s+is\\s+responsible|what\\s+next)");
    }
    public static String normalize(String text){return text.toLowerCase(Locale.ROOT).replace('ё','е');}
    public static boolean has(String text,String regex){return Pattern.compile(regex,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE|Pattern.UNICODE_CHARACTER_CLASS).matcher(normalize(text)).find();}
    public static boolean english(String question){return !has(question,"[а-я]")&&has(question,"[a-z]");}
    @SuppressWarnings("unchecked") public boolean matches(String key,String text){
        Object value=rules.get(key);if(!(value instanceof List<?>))return false;
        return ((List<String>)value).stream().anyMatch(p->has(text,p));
    }
    public static boolean stack(String q){return has(q,"\\bстек\\w*|\\b(?:tech(?:nology|nological)?\\s+|software\\s+)?stack\\b|какие\\s+технологии|каких\\s+технологиях|what\\s+technolog");}
    public static boolean internshipOverview(String q){
        if(has(q,"проект|\\bproject\\b")&&!has(q,"интер(?:н)?шип|стажиров|inter(?:n)?ship"))return false;
        if(!has(q,"интер(?:н)?шип\\w*|стажиров\\w*|\\binter(?:n)?ship\\b|\\bonboarding\\b|адаптац\\w*"))return false;
        if(has(q,"vpn|впн|mfa|2fa|github|slack|java|\\bqa\\b|devops|sre|стек|stack|команд|command|недел|week|прогресс|progress|статус|status|заявк|доступ.*(?:получ|настро)|first.*day|перв\\w*\\s+ден"))return false;
        return has(q,"как.*(?:проход|устроен|организ|выгляд)|что\\s+такое|расскаж|объясн|этап|длится|длительн|сколько.*(?:дн|дней|времен)|how|what|explain|describe|tell");
    }
    public static boolean commands(String q){return has(q,"\\b(?:команд(?:а|у|ы)|command|commands|script|скрипт\\w*|one-liner|одностроч\\w*)\\b");}
    public static boolean invention(String q){return (commands(q)||has(q,"инструкц|процедур|\\bsteps\\b|procedure"))&&has(q,"придум|выдум|сочин|\\binvent\\b|make\\s+up|even\\s+if.+(?:not|missing)|даже\\s+если.+(?:нет|не\\s+указ)");}
    public static String refuse(String q){return english(q)?"There is insufficient information in the available documentation to answer.":"В доступной документации не найдено достаточной информации для ответа.";}
    public static List<String> split(String q){
        return Arrays.asList(Pattern.compile("\\s+(?:и|а\\s+также|and)\\s+(?=(?:как\\w*|что|где|кто|зачем|почему|how|what|where|who)\\b)",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CHARACTER_CLASS).split(q.strip()));
    }
    public String intent(String q){
        String t=normalize(cleanQuestion(q));
        if(matches("ROLE_PATTERNS",t)&&matches("CURRICULUM_PATTERNS",t)&&!matches("PERSONAL_REFERENCES",t))return null;
        if(matches("REQUEST_PHRASES",t)||(has(t,"статус|status|одобри|отклони")&&has(t,"доступ|access")&&!tools(t).isEmpty()))return "requests";
        if(matches("PROGRESS_PHRASES",t))return "progress";
        boolean status=has(t,"статус|status|выполнен|выполнил|завершен|готов|completed|done");
        if(matches("FIRST_TASK_PHRASES",t))return status?"first_task_status":"first_task";
        if(matches("NEXT_TASK_PHRASES",t))return status?"next_task_status":"next_task";
        if(matches("TASK_PHRASES",t))return "tasks";
        return null;
    }
    public String personalQuestion(String q,List<String> previous){
        if(intent(q)!=null)return q;
        if(previous.isEmpty())return q;
        for(int i=previous.size()-1;i>=0;i--){
            String old=previous.get(i),intent=intent(old);
            if(intent==null){
                if(followup(old)||matches("PERSONAL_TASK_FOLLOWUPS",old)||matches("NEXT_TASK_FOLLOWUPS",old))continue;
                return q;
            }
            if(matches("NEXT_TASK_FOLLOWUPS",cleanQuestion(q)))return "Какая моя следующая задача?";
            if(matches("PERSONAL_TASK_FOLLOWUPS",cleanQuestion(q)))return intent.startsWith("next")?"Какой статус моей следующей задачи?":"Какой статус моей первой задачи?";
            if(q.length()<80&&has(q,"а\\s+(?:github|vpn|figma|aws)|what\\s+about")&&intent.equals("requests"))return "Статус моей заявки "+q;
            break;
        }
        return q;
    }
    @SuppressWarnings("unchecked") public Set<String> tools(String q){
        Set<String> result=new LinkedHashSet<>();
        var patterns=(Map<String,List<String>>)rules.get("TOOL_PATTERNS");
        patterns.forEach((name,list)->{if(list.stream().anyMatch(p->has(q,p)))result.add(name);});return result;
    }
    @SuppressWarnings("unchecked") public Set<String> words(String q){
        Set<String> stop=new HashSet<>((List<String>)rules.get("STOP_WORDS"));
        Set<String> words=new LinkedHashSet<>();var matcher=Pattern.compile("[a-zа-я0-9]+").matcher(normalize(q));
        while(matcher.find()){String value=matcher.group();if(value.length()>=3&&!stop.contains(value))words.add(value);}
        return words;
    }
    public static String section(String content,String heading){
        var pattern=Pattern.compile("(?im)^(#{1,6})[ \\t]+"+Pattern.quote(heading)+"[ \\t]*$");
        content=content.replace("\r\n","\n");
        var matcher=pattern.matcher(content);
        if(!matcher.find())return null;
        int depth=matcher.group(1).length();String rest=content.substring(matcher.end());
        var next=Pattern.compile("(?m)^#{1,"+depth+"}[ \\t]+").matcher(rest);
        return (next.find()?rest.substring(0,next.start()):rest).strip();
    }
    public static String steps(String text){
        String section=section(text,"Порядок действий");
        return section!=null&&Pattern.compile("(?m)^[ \\t]*(?:\\d+[.)]|[-*])[ \\t]+\\S").matcher(section).find()?section:null;
    }
    public String systemPrompt(){return rules.get("SYSTEM_PROMPT").toString();}
}
