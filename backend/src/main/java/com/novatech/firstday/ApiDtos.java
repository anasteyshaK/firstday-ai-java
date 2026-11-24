package com.novatech.firstday;

import jakarta.validation.constraints.*;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {}
    public record Register(@NotNull @Size(min=2,max=150) String full_name,
        @NotNull @Size(min=5,max=255) String email,@NotNull @Size(min=12,max=128) String password,
        @NotNull @Pattern(regexp="JAVA|QA|DEVOPS") String job_track,
        @Pattern(regexp="INTERN|EMPLOYEE") String employment_type,@Pattern(regexp="COMPANY|PROJECT") String onboarding_mode) {}
    public record Login(@NotNull String email,@NotNull String password) {}
    public record Task(@NotNull @Pattern(regexp="TODO|IN_PROGRESS|DONE") String status) {}
    public record ProjectTask(@NotNull @Pattern(regexp="TODO|IN_PROGRESS|BLOCKED|DONE") String status,@Size(max=1000) String blocker_note) {
        public String note() {
            String note=blocker_note==null?"":blocker_note.strip();
            if(status.equals("BLOCKED") && note.isEmpty()) throw new ApiException(422,"Describe the blocker");
            return status.equals("BLOCKED")?note:"";
        }
    }
    public record Access(@NotNull @Size(min=2,max=100) String system_name,@NotNull @Size(min=10,max=1000) String reason) {}
    public record Review(@NotNull @Pattern(regexp="APPROVED|REJECTED") String status) {}
    public record Profile(@NotNull @Pattern(regexp="INTERN|EMPLOYEE") String employment_type,@NotNull @Pattern(regexp="COMPANY|PROJECT") String onboarding_mode) {}
    public record Assignment(@Pattern(regexp="ENGINEERING|QA|DEVOPS|GENERAL") String project_role,Boolean is_active) {
        public String role(){return project_role==null?"ENGINEERING":project_role;}
        public boolean active(){return is_active==null || is_active;}
    }
    public record Chat(@NotNull @Size(min=1,max=2000) String question,UUID conversation_id,UUID project_id) {
        public String text(){ if(question.isBlank())throw new ApiException(422,"Question cannot be empty");return question.strip(); }
    }
}
