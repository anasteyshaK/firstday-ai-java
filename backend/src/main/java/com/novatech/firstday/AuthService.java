package com.novatech.firstday;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    static final String FIELDS="e.id,e.full_name,e.email,e.role,e.job_title,e.department,e.start_date,e.employment_type,e.onboarding_mode";
    final Db db;
    final Templates templates;
    public AuthService(Db db,Templates templates) {this.db=db;this.templates=templates;}
    public Map<String,Object> current(String authorization) {
        if(authorization==null || !authorization.regionMatches(true,0,"Bearer ",0,7) || authorization.substring(7).isBlank())
            throw new ApiException(401,"Sign in to your account");
        var user=db.optional("SELECT "+FIELDS+" FROM auth_sessions s JOIN employees e ON e.id=s.employee_id WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>NOW()",Passwords.digest(authorization.substring(7)));
        if(user==null) throw new ApiException(401,"Session invalid or expired");return user;
    }
    public Map<String,Object> admin(String authorization) {
        var user=current(authorization);
        if(!"ADMIN".equals(user.get("role"))) throw new ApiException(403,"Administrator access required");
        return user;
    }
    String session(Object employee) {
        String token=Passwords.token();
        db.update("INSERT INTO auth_sessions(employee_id,token_hash,expires_at) VALUES (?, ?, NOW()+INTERVAL '7 days')",Db.id(employee),Passwords.digest(token));
        return token;
    }
    @Transactional
    public Map<String,Object> register(ApiDtos.Register body) {
        String email=body.email().strip().toLowerCase(Locale.ROOT);
        String name=body.full_name().strip();
        if(name.length()<2 || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new ApiException(422,"Invalid name or email");
        String track=body.job_track();
        String title=Map.of("JAVA","Java Developer","QA","QA Engineer","DEVOPS","DevOps Engineer").get(track);
        String employment=body.employment_type()==null?"EMPLOYEE":body.employment_type();
        String mode=body.onboarding_mode()==null?("INTERN".equals(employment)?"COMPANY":"PROJECT"):body.onboarding_mode();
        var user=db.one("INSERT INTO employees(full_name,email,password_hash,role,job_title,department,start_date,employment_type,onboarding_mode) VALUES (?,?,?,'EMPLOYEE',?,?,CURRENT_DATE,?,?) RETURNING id,full_name,email,role,job_title,department,start_date,employment_type,onboarding_mode",
            name,email,Passwords.hash(body.password()),title,"QA".equals(track)?"Quality Engineering":"Engineering",employment,mode);
        Object plan=db.one("INSERT INTO onboarding_plans(employee_id,title,status) VALUES (?,?,'NOT_STARTED') RETURNING id",Db.id(user.get("id")),"30-дневная адаптация — "+title).get("id");
        for(var task:templates.company(track)) db.update("INSERT INTO onboarding_tasks(plan_id,title,description,day_number,status) VALUES (?,?,?,?,'TODO')",Db.id(plan),task.get(1),task.get(2),task.get(0));
        return Db.map("access_token",session(user.get("id")),"token_type","bearer","user",user);
    }
    @Transactional
    public Map<String,Object> login(ApiDtos.Login body) {
        var user=db.optional("SELECT "+FIELDS+",e.password_hash FROM employees e WHERE e.email=?",body.email().strip().toLowerCase(Locale.ROOT));
        if(user==null || !Passwords.verify(body.password(),Db.text(user,"password_hash"))) throw new ApiException(401,"Invalid email or password");
        user.remove("password_hash");
        return Db.map("access_token",session(user.get("id")),"token_type","bearer","user",user);
    }
    public void logout(String header) {
        current(header);db.update("UPDATE auth_sessions SET revoked_at=NOW() WHERE token_hash=?",Passwords.digest(header.substring(7)));
    }
}
