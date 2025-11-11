package com.example.workplan.service;

import com.example.workplan.domain.AppUser;
import com.example.workplan.domain.Project;
import com.example.workplan.domain.UserRole;
import com.example.workplan.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final AppUserRepository appUserRepository;

    public UserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public AppUser createAdmin(Project project, String name, String email) {
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("Email already registered: " + email);
        }
        AppUser admin = new AppUser();
        admin.setName(name);
        admin.setEmail(email);
        admin.setRole(UserRole.ADMIN);
        admin.setProject(project);
        return appUserRepository.save(admin);
    }

    @Transactional
    public AppUser createUser(Project project, String name, String email, UserRole role) {
        if (role == UserRole.ADMIN) {
            throw new IllegalArgumentException("Use createAdmin for administrator accounts");
        }
        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("Email already registered: " + email);
        }
        AppUser user = new AppUser();
        user.setName(name);
        user.setEmail(email);
        user.setRole(role);
        user.setProject(project);
        return appUserRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findById(Long id) {
        return appUserRepository.findById(id);
    }
}
