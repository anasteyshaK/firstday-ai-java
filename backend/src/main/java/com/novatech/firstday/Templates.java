package com.novatech.firstday;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class Templates {
    final Map<String,Object> values;
    public Templates(ObjectMapper json) {
        try(var stream=new ClassPathResource("templates.json").getInputStream()){values=json.readValue(stream,new TypeReference<>(){});}
        catch(Exception ex){throw new IllegalStateException("Cannot load onboarding templates",ex);}
    }
    @SuppressWarnings("unchecked")
    public List<List<Object>> company(String track){
        var all=new ArrayList<>((List<List<Object>>)values.get("common"));
        all.addAll(((Map<String,List<List<Object>>>)values.get("roles")).get(track));return all;
    }
    public List<List<String>> project(String role){
        var tasks=new ArrayList<List<String>>();
        tasks.add(List.of("CONTEXT","Разобраться в назначении проекта","Прочитайте обзор и объясните основной пользовательский сценарий.","overview.md"));
        tasks.add(List.of("CONTEXT","Найти компоненты и ответственных","Изучите карту компонентов. Уточните владельцев через Atlas; не угадывайте имена.","architecture.md"));
        tasks.add(List.of("ENVIRONMENT","Проверить необходимые доступы","Сверьте доступы с инструкцией. При необходимости создайте заявку в Access Center.","environment.md"));
        var task=switch(role){
            case "QA" -> List.of("Подготовить тестовую среду","Проверьте staging, тестовые данные и smoke tests по инструкции проекта.");
            case "DEVOPS" -> List.of("Проверить окружения и наблюдаемость","Найдите dashboard и runbook, проверьте read-only доступ.");
            case "GENERAL" -> List.of("Проверить рабочий сценарий","Пройдите основной сценарий в sandbox и запишите открытые вопросы.");
            default -> List.of("Подготовить рабочее окружение","Следуйте README выбранного сервиса; проверьте тесты и health endpoint.");
        };
        tasks.add(List.of("ENVIRONMENT",task.get(0),task.get(1),"environment.md"));
        tasks.add(List.of("DELIVERY","Изучить процесс изменений","Уточните правила review, проверки и согласования изменений.","delivery.md"));
        tasks.add(List.of("DELIVERY","Согласовать первую задачу","Вместе с ответственным выберите небольшую задачу и критерии приёмки.","delivery.md"));
        return tasks;
    }
}
