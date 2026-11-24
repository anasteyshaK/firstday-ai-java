package com.novatech.firstday;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {
    final Db db; final Templates templates;
    public WorkspaceService(Db db,Templates templates){this.db=db;this.templates=templates;}
    public Map<String,Object> plan(Map<String,Object> user){
        UUID employee=Db.id(user.get("id"));
        var plan=db.one("SELECT id,title,status FROM onboarding_plans WHERE employee_id=?",employee);
        var tasks=db.rows("SELECT id,title,description,day_number,status FROM onboarding_tasks WHERE plan_id=? ORDER BY day_number,created_at,id",Db.id(plan.get("id")));
        for(var task:tasks) {int day=Db.count(task.get("day_number"));task.put("week",day<=5?1:day<=10?2:day<=20?3:4);}
        long done=tasks.stream().filter(t->"DONE".equals(t.get("status"))).count();
        return Db.map("job_title",user.get("job_title"),"plan_id",plan.get("id"),"title",plan.get("title"),"status",plan.get("status"),"total_tasks",tasks.size(),"completed_tasks",done,"progress_percent",Db.progress(done,tasks.size()),"tasks",tasks,"demo",false);
    }
    @Transactional
    public Map<String,Object> updateTask(Map<String,Object> user,UUID task,ApiDtos.Task body){
        UUID employee=Db.id(user.get("id"));
        var plan=db.one("SELECT id FROM onboarding_plans WHERE employee_id=? FOR UPDATE",employee);
        var updated=db.optional("UPDATE onboarding_tasks SET status=?,completed_at=CASE WHEN ?='DONE' THEN NOW() ELSE NULL END WHERE id=? AND plan_id=? RETURNING id",body.status(),body.status(),task,Db.id(plan.get("id")));
        if(updated==null)throw ApiException.missing("Task not found");
        var counts=db.one("SELECT count(*) AS total,count(*) FILTER (WHERE status='DONE') AS done FROM onboarding_tasks WHERE plan_id=?",Db.id(plan.get("id")));
        int total=Db.count(counts.get("total")),done=Db.count(counts.get("done"));
        db.update("UPDATE onboarding_plans SET status=? WHERE id=?",total>0&&done==total?"COMPLETED":done>0?"IN_PROGRESS":"NOT_STARTED",Db.id(plan.get("id")));
        return plan(user);
    }
    public Map<String,Object> requests(Map<String,Object> user){
        var items=db.rows("SELECT id,system_name,reason,status,requested_at,resolved_at FROM access_requests WHERE employee_id=? ORDER BY requested_at DESC,id DESC",Db.id(user.get("id")));
        return Db.map("demo",false,"items",items,"total",items.size());
    }
    public Map<String,Object> request(Map<String,Object> user,ApiDtos.Access body){
        if(body.system_name().isBlank()||body.reason().isBlank())throw new ApiException(422,"Fill in tool and reason");
        return db.one("INSERT INTO access_requests(employee_id,system_name,reason,status) VALUES (?,?,?,'PENDING') RETURNING id,system_name,reason,status,requested_at,resolved_at",Db.id(user.get("id")),body.system_name().strip(),body.reason().strip());
    }
    public List<Map<String,Object>> adminRequests(){
        return db.rows("SELECT r.id,r.employee_id,e.full_name AS employee_name,e.email AS employee_email,r.system_name,r.reason,r.status,r.requested_at,r.resolved_at FROM access_requests r JOIN employees e ON e.id=r.employee_id ORDER BY (r.status='PENDING') DESC,r.requested_at DESC LIMIT 500");
    }
    @Transactional
    public Map<String,Object> review(UUID id,ApiDtos.Review body){
        if(db.optional("UPDATE access_requests SET status=?,resolved_at=NOW() WHERE id=? AND status='PENDING' RETURNING id",body.status(),id)==null)throw new ApiException(409,"Request already reviewed or not found");
        return db.one("SELECT r.id,r.employee_id,e.full_name AS employee_name,e.email AS employee_email,r.system_name,r.reason,r.status,r.requested_at,r.resolved_at FROM access_requests r JOIN employees e ON e.id=r.employee_id WHERE r.id=?",id);
    }
    public Map<String,Object> overview(){
        var row=db.one("""
            SELECT (SELECT count(*) FROM employees) AS employees_total,
            (SELECT count(*) FROM onboarding_plans) AS plans_total,
            (SELECT count(*) FROM onboarding_plans WHERE status='COMPLETED') AS completed_plans,
            (SELECT count(*) FROM onboarding_tasks) AS tasks_total,
            (SELECT count(*) FROM onboarding_tasks WHERE status='DONE') AS tasks_done,
            (SELECT count(*) FROM access_requests WHERE status='PENDING') AS requests_pending,
            (SELECT count(*) FROM access_requests WHERE status='APPROVED') AS requests_approved,
            (SELECT count(*) FROM access_requests WHERE status='REJECTED') AS requests_rejected
            """);
        row.put("average_progress",db.one("SELECT coalesce(round(avg(progress)),0)::int AS value FROM (SELECT 100.0*count(t.id) FILTER (WHERE t.status='DONE')/nullif(count(t.id),0) AS progress FROM onboarding_plans p LEFT JOIN onboarding_tasks t ON t.plan_id=p.id GROUP BY p.id) totals").get("value"));
        return row;
    }
    public List<Map<String,Object>> employees(){
        return db.rows("""
            SELECT e.id,e.full_name,e.email,e.role,e.job_title,e.department,e.start_date,e.employment_type,e.onboarding_mode,
            p.status AS plan_status,count(t.id)::int AS tasks_total,count(t.id) FILTER (WHERE t.status='DONE')::int AS tasks_done,
            coalesce(round(100.0*count(t.id) FILTER (WHERE t.status='DONE')/nullif(count(t.id),0)),0)::int AS progress_percent
            FROM employees e LEFT JOIN onboarding_plans p ON p.employee_id=e.id LEFT JOIN onboarding_tasks t ON t.plan_id=p.id
            GROUP BY e.id,p.id ORDER BY e.start_date DESC,e.full_name LIMIT 500
            """);
    }
    public Map<String,Object> profile(UUID id,ApiDtos.Profile body){
        return db.one("UPDATE employees SET employment_type=?,onboarding_mode=? WHERE id=? RETURNING id,full_name,email,role,job_title,department,start_date,employment_type,onboarding_mode",body.employment_type(),body.onboarding_mode(),id);
    }
    public Map<String,Object> membership(UUID project,UUID employee){
        var row=db.optional("SELECT p.id,p.slug,p.name,m.project_role FROM projects p JOIN project_memberships m ON m.project_id=p.id WHERE p.id=? AND m.employee_id=? AND m.is_active=TRUE",project,employee);
        if(row==null)throw ApiException.missing("Project not found or unavailable");return row;
    }
    public List<Map<String,Object>> projects(UUID employee){
        return db.rows("SELECT p.id,p.slug,p.name,p.description,p.is_demo,TRUE AS joined,m.project_role FROM projects p JOIN project_memberships m ON m.project_id=p.id WHERE m.employee_id=? AND m.is_active=TRUE ORDER BY p.name",employee);
    }
    public List<Map<String,Object>> catalog(){return db.rows("SELECT id,name,slug,is_demo FROM projects ORDER BY name");}
    public List<Map<String,Object>> assignments(UUID employee){
        db.one("SELECT id FROM employees WHERE id=?",employee);
        return db.rows("SELECT p.id AS project_id,p.name,m.project_role,m.is_active FROM project_memberships m JOIN projects p ON p.id=m.project_id WHERE m.employee_id=? ORDER BY p.name",employee);
    }
    @Transactional
    public Map<String,Object> assign(UUID employee,UUID project,ApiDtos.Assignment body,UUID admin){
        var info=db.one("SELECT id,slug,name FROM projects WHERE id=? FOR UPDATE",project);
        db.one("SELECT id FROM employees WHERE id=? FOR UPDATE",employee);
        var existing=db.optional("SELECT project_role,is_active FROM project_memberships WHERE project_id=? AND employee_id=? FOR UPDATE",project,employee);
        if(!body.active()){
            if(existing==null)throw ApiException.missing("Assignment not found");
            db.update("UPDATE project_memberships SET is_active=FALSE,assigned_by=?,updated_at=NOW() WHERE project_id=? AND employee_id=?",admin,project,employee);
            return Db.map("project_id",project,"name",info.get("name"),"project_role",existing.get("project_role"),"is_active",false);
        }
        if(existing!=null&&!body.role().equals(existing.get("project_role")))throw new ApiException(409,"Restore the original project role to preserve progress");
        db.update("INSERT INTO project_memberships(project_id,employee_id,project_role,is_active,assigned_by) VALUES (?,?,?,TRUE,?) ON CONFLICT(project_id,employee_id) DO UPDATE SET is_active=TRUE,assigned_by=EXCLUDED.assigned_by,updated_at=NOW()",project,employee,body.role(),admin);
        int position=0;
        for(var task:templates.project(body.role())) db.update("INSERT INTO project_onboarding_tasks(project_id,employee_id,position,stage,title,description,document_path) VALUES (?,?,?,?,?,?,?) ON CONFLICT(project_id,employee_id,position) DO NOTHING",project,employee,++position,task.get(0),task.get(1),task.get(2),"projects/"+info.get("slug")+"/"+task.get(3));
        return Db.map("project_id",project,"name",info.get("name"),"project_role",body.role(),"is_active",true);
    }
    public Map<String,Object> projectPlan(UUID project,UUID employee){
        var info=membership(project,employee);
        var tasks=db.rows("SELECT id,position,stage,title,description,document_path,status,blocker_note FROM project_onboarding_tasks WHERE project_id=? AND employee_id=? ORDER BY position",project,employee);
        long done=tasks.stream().filter(t->"DONE".equals(t.get("status"))).count();
        long blocked=tasks.stream().filter(t->"BLOCKED".equals(t.get("status"))).count();
        return Db.map("project_id",project,"project_name",info.get("name"),"project_role",info.get("project_role"),"tasks",tasks,"total_tasks",tasks.size(),"completed_tasks",done,"blocked_tasks",blocked,"progress_percent",Db.progress(done,tasks.size()));
    }
    @Transactional
    public Map<String,Object> projectTask(UUID project,UUID employee,UUID task,ApiDtos.ProjectTask body){
        membership(project,employee);String note=body.note();
        if(db.optional("UPDATE project_onboarding_tasks SET status=?,blocker_note=?,updated_at=NOW() WHERE id=? AND project_id=? AND employee_id=? RETURNING id",body.status(),note,task,project,employee)==null)throw ApiException.missing("Task not found");
        return projectPlan(project,employee);
    }
    public List<Map<String,Object>> projectDocuments(UUID project,UUID employee){
        membership(project,employee);
        return db.rows("SELECT title,file_path,category,content,updated_at FROM project_documents WHERE project_id=? ORDER BY category,title",project);
    }
    public Map<String,Object> projectDocumentList(UUID project,UUID employee,String query,String category){
        var docs=projectDocuments(project,employee);
        Map<String,Integer> counts=new TreeMap<>();docs.forEach(d->counts.merge(Db.text(d,"category"),1,Integer::sum));
        String term=query.strip().toLowerCase(Locale.ROOT);
        var filtered=docs.stream().filter(d->category.isBlank()||category.equals(d.get("category")))
            .filter(d->term.isEmpty()||(d.get("title")+" "+d.get("file_path")+" "+d.get("content")).toLowerCase(Locale.ROOT).contains(term))
            .map(d->{Map<String,Object> item=new LinkedHashMap<>(d);item.remove("content");return item;}).toList();
        return Db.map("items",filtered,"total",filtered.size(),"categories",counts.entrySet().stream().map(e->Db.map("name",e.getKey(),"count",e.getValue())).toList());
    }
    public Map<String,Object> documents(String query,String category){
        String term=query.strip(),escaped="%"+term.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        var categories=db.rows("SELECT category AS name,count(*) AS count FROM documents WHERE access_level='EMPLOYEE' GROUP BY category ORDER BY category");
        var items=db.rows("""
            SELECT d.title,d.file_path,d.category,d.department,d.updated_at FROM documents d
            WHERE d.access_level='EMPLOYEE' AND (?='' OR d.category=?) AND (?='' OR d.title ILIKE ? ESCAPE '!'
            OR d.file_path ILIKE ? ESCAPE '!' OR d.category ILIKE ? ESCAPE '!' OR EXISTS
            (SELECT 1 FROM document_chunks c WHERE c.document_id=d.id AND c.content ILIKE ? ESCAPE '!'))
            ORDER BY d.category,d.title,d.file_path LIMIT 200
            """,category,category,term,escaped,escaped,escaped,escaped);
        return Db.map("items",items,"total",items.size(),"categories",categories);
    }
    public Map<String,Object> document(String path,UUID project,UUID employee){
        if(!path.matches("[a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)*\\.md"))throw new ApiException(400,"Invalid document path");
        if(project!=null){
            var result=projectDocuments(project,employee).stream().filter(d->path.equals(d.get("file_path"))).findFirst().orElseThrow(()->ApiException.missing("Document not found"));
            return Db.map("title",result.get("title"),"file_path",path,"category",result.get("category"),"content",result.get("content"));
        }
        return db.one("SELECT d.title,d.file_path,d.category,string_agg(c.content,E'\\n\\n' ORDER BY c.chunk_index) AS content FROM documents d JOIN document_chunks c ON c.document_id=d.id WHERE d.file_path=? AND d.access_level='EMPLOYEE' GROUP BY d.id",path);
    }
}
