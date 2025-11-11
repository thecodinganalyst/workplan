package com.hevlar.workplan.web;

import com.hevlar.workplan.domain.AppUser;
import com.hevlar.workplan.domain.Project;
import com.hevlar.workplan.domain.UserRole;
import com.hevlar.workplan.service.AuthService;
import com.hevlar.workplan.service.AuthService.OtpDetails;
import com.hevlar.workplan.service.EmailService;
import com.hevlar.workplan.service.ProjectService;
import com.hevlar.workplan.service.UserService;
import com.hevlar.workplan.web.form.CreateUserForm;
import com.hevlar.workplan.web.form.OtpRequestForm;
import com.hevlar.workplan.web.form.OtpVerificationForm;
import com.hevlar.workplan.web.form.ProjectSetupForm;
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

    public PortalController(ProjectService projectService,
                            UserService userService,
                            AuthService authService,
                            EmailService emailService) {
        this.projectService = projectService;
        this.userService = userService;
        this.authService = authService;
        this.emailService = emailService;
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
        model.addAttribute("project", project);
        model.addAttribute("users", project.getUsers());
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
