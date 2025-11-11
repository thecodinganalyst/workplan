package com.example.workplan.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ProjectSetupForm {

    @NotBlank(message = "Project name is required")
    private String projectName;

    @NotBlank(message = "Administrator name is required")
    private String adminName;

    @Email(message = "Email must be valid")
    @NotBlank(message = "Administrator email is required")
    private String adminEmail;

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getAdminName() {
        return adminName;
    }

    public void setAdminName(String adminName) {
        this.adminName = adminName;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }
}
