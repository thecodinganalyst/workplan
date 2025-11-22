package com.hevlar.workplan.web;

import com.hevlar.workplan.domain.AppUser;
import com.hevlar.workplan.domain.AppUser;
import com.hevlar.workplan.domain.Project;
import com.hevlar.workplan.domain.ProjectModule;
import com.hevlar.workplan.domain.UserRole;
import com.hevlar.workplan.service.BacklogService;
import com.hevlar.workplan.service.AuthService;
import com.hevlar.workplan.service.AuthService.OtpDetails;
import com.hevlar.workplan.service.EmailService;
import com.hevlar.workplan.service.ProjectService;
import com.hevlar.workplan.service.UserService;
import com.hevlar.workplan.web.form.CreateUserForm;
import com.hevlar.workplan.web.form.FeatureForm;
import com.hevlar.workplan.web.form.ModuleForm;
import com.hevlar.workplan.web.form.OtpRequestForm;
import com.hevlar.workplan.web.form.OtpVerificationForm;
import com.hevlar.workplan.web.form.ProjectSetupForm;
import com.hevlar.workplan.web.form.TaskForm;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping
public class PortalController {

    private static final String SESSION_USER_ID = "workplan:userId";

    private final ProjectService projectService;
    private final UserService userService;
    private final AuthService authService;
    private final EmailService emailService;
    private final BacklogService backlogService;

    public PortalController(ProjectService projectService,
                            UserService userService,
                            AuthService authService,
                            EmailService emailService,
                            BacklogService backlogService) {
        this.projectService = projectService;
        this.userService = userService;
        this.authService = authService;
        this.emailService = emailService;
        this.backlogService = backlogService;
    }

    @ModelAttribute("projectSetupForm")
    public ProjectSetupForm projectSetupForm() {
        return new ProjectSetupForm();
    }

    @ModelAttribute("otpRequestForm")
    public OtpRequestForm otpRequestForm() {
        return new OtpRequestForm();
    }

    @ModelAttribute("otpVerificationForm")
    public OtpVerificationForm otpVerificationForm() {
        return new OtpVerificationForm();
    }

    @ModelAttribute("createUserForm")
    public CreateUserForm createUserForm() {
        return new CreateUserForm();
    }

    @ModelAttribute("moduleForm")
    public ModuleForm moduleForm() {
        return new ModuleForm();
    }

    @ModelAttribute("featureForm")
    public FeatureForm featureForm() {
        return new FeatureForm();
    }

    @ModelAttribute("taskForm")
    public TaskForm taskForm() {
        return new TaskForm();
    }

    @ModelAttribute("availableRoles")
    public List<UserRole> availableRoles() {
        return EnumSet.complementOf(EnumSet.of(UserRole.ADMIN))
            .stream()
            .collect(Collectors.toList());
    }

    @GetMapping("/")
    public String landing() {
        if (!projectService.projectExists()) {
            return "setup";
        }
        return "login";
    }

    @PostMapping("/setup")
    public String handleSetup(@Valid @ModelAttribute("projectSetupForm") ProjectSetupForm form,
                              BindingResult bindingResult,
                              Model model) {
        if (projectService.projectExists()) {
            bindingResult.reject("project.exists", "A project is already configured");
        }
        if (bindingResult.hasErrors()) {
            return "setup";
        }
        Project project = projectService.createProject(form.getProjectName());
        backlogService.ensureBacklog(project);
        AppUser admin;
        try {
            admin = userService.createAdmin(project, form.getAdminName(), form.getAdminEmail());
        } catch (IllegalStateException ex) {
            bindingResult.rejectValue("adminEmail", "email.duplicate", ex.getMessage());
            return "setup";
        }
        String otp = authService.generateOtpForUser(admin);
        sendOtpEmail(admin, otp, project.getName());
        model.addAttribute("email", admin.getEmail());
        model.addAttribute("message", "An OTP was sent to " + admin.getEmail());
        return "otp-sent";
    }

    @PostMapping("/login/request-otp")
    public String requestOtp(@Valid @ModelAttribute("otpRequestForm") OtpRequestForm form,
                             BindingResult bindingResult,
                             Model model) {
        if (bindingResult.hasErrors()) {
            return "login";
        }
        try {
            OtpDetails details = authService.generateOtpForEmail(form.getEmail());
            sendOtpEmail(details.user(), details.otp(), details.user().getProject().getName());
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("email", "email.notFound", ex.getMessage());
            return "login";
        }
        model.addAttribute("email", form.getEmail());
        model.addAttribute("message", "An OTP was sent to " + form.getEmail());
        return "otp-sent";
    }

    @PostMapping("/login/verify")
    public String verifyOtp(@Valid @ModelAttribute("otpVerificationForm") OtpVerificationForm form,
                            BindingResult bindingResult,
                            HttpSession session) {
        if (bindingResult.hasErrors()) {
            return "login";
        }
        try {
            AppUser user = authService.verifyOtp(form.getEmail(), form.getOtp());
            session.setAttribute(SESSION_USER_ID, user.getId());
            return "redirect:/dashboard";
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("otp", "otp.invalid", ex.getMessage());
            return "login";
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Optional<AppUser> currentUser = authenticatedUser(session);
        if (currentUser.isEmpty()) {
            return "redirect:/";
        }
        if (currentUser.get().getRole() != UserRole.ADMIN) {
            return "redirect:/";
        }
        Project project = projectService.getProject()
            .orElseThrow(() -> new IllegalStateException("Project should be present"));
        if (!model.containsAttribute("createUserForm")) {
            model.addAttribute("createUserForm", new CreateUserForm());
        }
        if (!model.containsAttribute("moduleForm")) {
            model.addAttribute("moduleForm", new ModuleForm());
        }
        if (!model.containsAttribute("featureForm")) {
            model.addAttribute("featureForm", new FeatureForm());
        }
        if (!model.containsAttribute("taskForm")) {
            model.addAttribute("taskForm", new TaskForm());
        }
        model.addAttribute("project", project);
        model.addAttribute("users", project.getUsers());
        List<ProjectModule> modules = backlogService.modules(project);
        model.addAttribute("modules", modules);
        model.addAttribute("features", modules.stream().flatMap(module -> module.getFeatures().stream()).toList());
        model.addAttribute("developers", project.getUsers().stream()
            .filter(user -> user.getRole() == UserRole.DEVELOPER)
            .toList());
        return "dashboard";
    }

    @PostMapping("/dashboard/users")
    public String createUser(@Valid @ModelAttribute("createUserForm") CreateUserForm form,
                             BindingResult bindingResult,
                             HttpSession session,
                             Model model) {
        Optional<AppUser> currentUser = authenticatedUser(session);
        if (currentUser.isEmpty() || currentUser.get().getRole() != UserRole.ADMIN) {
            return "redirect:/";
        }
        if (bindingResult.hasErrors()) {
            return dashboard(session, model);
        }
        Project project = projectService.getProject()
            .orElseThrow(() -> new IllegalStateException("Project should be present"));
        try {
            AppUser newUser = userService.createUser(project, form.getName(), form.getEmail(), form.getRole());
            String otp = authService.generateOtpForUser(newUser);
            sendOtpEmail(newUser, otp, project.getName());
        } catch (IllegalStateException | IllegalArgumentException ex) {
            bindingResult.reject("user.create", ex.getMessage());
            return dashboard(session, model);
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/dashboard/backlog/modules")
    public String createModule(@Valid @ModelAttribute("moduleForm") ModuleForm form,
                               BindingResult bindingResult,
                               HttpSession session,
                               Model model) {
        Optional<AppUser> currentUser = authenticatedUser(session);
        if (currentUser.isEmpty() || currentUser.get().getRole() != UserRole.ADMIN) {
            return "redirect:/";
        }
        if (bindingResult.hasErrors()) {
            return dashboard(session, model);
        }
        Project project = projectService.getProject()
            .orElseThrow(() -> new IllegalStateException("Project should be present"));
        backlogService.addModule(project, form.getName(), form.getDescription());
        return "redirect:/dashboard";
    }

    @PostMapping("/dashboard/backlog/features")
    public String createFeature(@Valid @ModelAttribute("featureForm") FeatureForm form,
                                BindingResult bindingResult,
                                HttpSession session,
                                Model model) {
        Optional<AppUser> currentUser = authenticatedUser(session);
        if (currentUser.isEmpty() || currentUser.get().getRole() != UserRole.ADMIN) {
            return "redirect:/";
        }
        if (bindingResult.hasErrors()) {
            return dashboard(session, model);
        }
        try {
            backlogService.addFeature(form.getModuleId(), form.getName(), form.getDescription());
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("feature.create", ex.getMessage());
            return dashboard(session, model);
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/dashboard/backlog/tasks")
    public String createTask(@Valid @ModelAttribute("taskForm") TaskForm form,
                             BindingResult bindingResult,
                             HttpSession session,
                             Model model) {
        Optional<AppUser> currentUser = authenticatedUser(session);
        if (currentUser.isEmpty() || currentUser.get().getRole() != UserRole.ADMIN) {
            return "redirect:/";
        }
        if (bindingResult.hasErrors()) {
            return dashboard(session, model);
        }
        AppUser assignee = null;
        if (form.getAssigneeId() != null) {
            assignee = userService.findById(form.getAssigneeId())
                .orElseThrow(() -> new IllegalArgumentException("Assignee not found"));
            if (assignee.getRole() != UserRole.DEVELOPER) {
                bindingResult.rejectValue("assigneeId", "assignee.role", "Only developers can be assigned to tasks");
                return dashboard(session, model);
            }
        }
        try {
            backlogService.addTask(form.getFeatureId(), form.getName(), form.getDescription(), form.getEstimatedHours(), assignee);
        } catch (IllegalArgumentException ex) {
            bindingResult.reject("task.create", ex.getMessage());
            return dashboard(session, model);
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    private Optional<AppUser> authenticatedUser(HttpSession session) {
        Long userId = (Long) session.getAttribute(SESSION_USER_ID);
        if (userId == null) {
            return Optional.empty();
        }
        return userService.findById(userId);
    }

    private void sendOtpEmail(AppUser user, String otp, String projectName) {
        String subject = "Your Workplan login code";
        String body = "Hello " + user.getName() + ",\n\n" +
            "Use the following one-time password to access the Workplan project '" + projectName + "': " + otp + "\n" +
            "The code expires shortly.\n\n" +
            "Thanks,\nWorkplan";
        emailService.sendEmail(user.getEmail(), subject, body);
    }
}
