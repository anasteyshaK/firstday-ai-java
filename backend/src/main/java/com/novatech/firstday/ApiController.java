package com.novatech.firstday;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ApiController {
    final AuthService auth; final WorkspaceService workspace; final Db db;
    public ApiController(AuthService auth,WorkspaceService workspace,Db db){this.auth=auth;this.workspace=workspace;this.db=db;}
    @GetMapping("/health") public Object health(){return Db.map("status","ok","service","FirstDay AI Java API","version","1.0.0","streaming","ollama");}
    @GetMapping("/ready") public Object ready(){db.one("SELECT 1 AS connected");return Map.of("status","ok","database","connected");}
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public Object register(@Valid @RequestBody ApiDtos.Register body){return auth.register(body);}
    @PostMapping("/auth/login") public Object login(@Valid @RequestBody ApiDtos.Login body){return auth.login(body);}
    @GetMapping("/auth/me") public Object me(@RequestHeader(value="Authorization",required=false)String header){return auth.current(header);}
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value="Authorization",required=false)String header){auth.logout(header);}
    @GetMapping("/onboarding") public Object plan(@RequestHeader(value="Authorization",required=false)String header){return workspace.plan(auth.current(header));}
    @PatchMapping("/onboarding/tasks/{id}") public Object task(@RequestHeader(value="Authorization",required=false)String header,@PathVariable UUID id,@Valid @RequestBody ApiDtos.Task body){return workspace.updateTask(auth.current(header),id,body);}
    @GetMapping("/access-requests") public Object requests(@RequestHeader(value="Authorization",required=false)String header){return workspace.requests(auth.current(header));}
    @PostMapping("/access-requests") @ResponseStatus(HttpStatus.CREATED)
    public Object request(@RequestHeader(value="Authorization",required=false)String header,@Valid @RequestBody ApiDtos.Access body){return workspace.request(auth.current(header),body);}
    @GetMapping("/documents") public Object documents(@RequestHeader(value="Authorization",required=false)String header,@RequestParam(defaultValue="")@Size(max=150)String q,@RequestParam(defaultValue="")@Size(max=100)String category){auth.current(header);return workspace.documents(q,category);}
    @GetMapping("/documents/content") public Object document(@RequestHeader(value="Authorization",required=false)String header,@RequestParam @Size(min=5,max=400)String path,@RequestParam(required=false)UUID project_id){var user=auth.current(header);ChatService.allowProject(user,project_id);return workspace.document(path,project_id,Db.id(user.get("id")));}
    @GetMapping("/projects") public Object projects(@RequestHeader(value="Authorization",required=false)String header){var user=auth.current(header);return ChatService.internship(user)?List.of():workspace.projects(Db.id(user.get("id")));}
    @PostMapping("/projects/{project}/join") public Object join(@RequestHeader(value="Authorization",required=false)String header,@PathVariable UUID project,@Valid @RequestBody(required=false)ApiDtos.Assignment ignored){var user=auth.current(header);ChatService.allowProject(user,project);return workspace.projectPlan(project,Db.id(user.get("id")));}
    @GetMapping("/projects/{project}/onboarding") public Object projectPlan(@RequestHeader(value="Authorization",required=false)String header,@PathVariable UUID project){var user=auth.current(header);ChatService.allowProject(user,project);return workspace.projectPlan(project,Db.id(user.get("id")));}
    @PatchMapping("/projects/{project}/onboarding/tasks/{task}") public Object projectTask(@RequestHeader(value="Authorization",required=false)String header,@PathVariable UUID project,@PathVariable UUID task,@Valid @RequestBody ApiDtos.ProjectTask body){var user=auth.current(header);ChatService.allowProject(user,project);return workspace.projectTask(project,Db.id(user.get("id")),task,body);}
    @GetMapping("/projects/{project}/documents") public Object projectDocs(@RequestHeader(value="Authorization",required=false)String header,@PathVariable UUID project,@RequestParam(defaultValue="")@Size(max=150)String q,@RequestParam(defaultValue="")@Size(max=100)String category){var user=auth.current(header);ChatService.allowProject(user,project);return workspace.projectDocumentList(project,Db.id(user.get("id")),q,category);}
    @GetMapping("/admin/overview")public Object overview(@RequestHeader(value="Authorization",required=false)String header){auth.admin(header);return workspace.overview();}
    @GetMapping("/admin/employees")public Object employees(@RequestHeader(value="Authorization",required=false)String header){auth.admin(header);return workspace.employees();}
    @GetMapping("/admin/access-requests")public Object allRequests(@RequestHeader(value="Authorization",required=false)String header){auth.admin(header);return workspace.adminRequests();}
    @PatchMapping("/admin/access-requests/{id}/review")public Object review(@RequestHeader(value="Authorization",required=false)String header,@PathVariable UUID id,@Valid @RequestBody ApiDtos.Review body){auth.admin(header);return workspace.review(id,body);}
    @PatchMapping("/admin/employees/{id}/profile")public Object profile(@RequestHeader(value="Authorization",required=false)String header,@PathVariable UUID id,@Valid @RequestBody ApiDtos.Profile body){auth.admin(header);return workspace.profile(id,body);}
    @GetMapping("/admin/projects")public Object catalog(@RequestHeader(value="Authorization",required=false)String header){auth.admin(header);return workspace.catalog();}
    @GetMapping("/admin/employees/{id}/projects")public Object assignments(@RequestHeader(value="Authorization",required=false)String header,@PathVariable UUID id){auth.admin(header);return workspace.assignments(id);}
    @PutMapping("/admin/employees/{employee}/projects/{project}")public Object assign(@RequestHeader(value="Authorization",required=false)String header,@PathVariable UUID employee,@PathVariable UUID project,@Valid @RequestBody ApiDtos.Assignment body){return workspace.assign(employee,project,body,Db.id(auth.admin(header).get("id")));}
}
