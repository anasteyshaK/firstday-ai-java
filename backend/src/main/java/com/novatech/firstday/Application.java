package com.novatech.firstday;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        boolean command=java.util.Arrays.stream(args).anyMatch(a->a.startsWith("--firstday.command="));
        SpringApplication application=new SpringApplication(Application.class);
        if(command)application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        var context=application.run(args);
        if(command)context.close();
    }
}
