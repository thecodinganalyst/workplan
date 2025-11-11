package com.example.workplan.service;

import com.example.workplan.domain.AppUser;
import com.example.workplan.domain.Project;
import com.example.workplan.domain.UserRole;
import com.example.workplan.repository.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class AuthServiceTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private EntityManager entityManager;

    private TestClock clock;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2024-01-01T00:00:00Z"));
        authService = new AuthService(appUserRepository, clock, 10);
    }

    @Test
    void generateOtpAndVerifyClearsStoredCode() {
        AppUser user = persistUser("admin@example.com", UserRole.ADMIN);

        String otp = authService.generateOtpForUser(user);

        assertThat(otp).matches("\\d{6}");

        AppUser verified = authService.verifyOtp(user.getEmail(), otp);

        assertThat(verified.getId()).isEqualTo(user.getId());
        assertThat(appUserRepository.findById(user.getId())).get()
            .extracting(AppUser::getLatestOtp)
            .isNull();
    }

    @Test
    void verifyOtpFailsWhenExpired() {
        AppUser user = persistUser("scrum@example.com", UserRole.SCRUM_MASTER);

        String otp = authService.generateOtpForUser(user);

        clock.advance(Duration.ofMinutes(15));

        assertThatThrownBy(() -> authService.verifyOtp(user.getEmail(), otp))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expired");
    }

    private AppUser persistUser(String email, UserRole role) {
        Project project = new Project();
        project.setName("Project " + email);
        entityManager.persist(project);

        AppUser user = new AppUser();
        user.setName("User " + email);
        user.setEmail(email);
        user.setRole(role);
        user.setProject(project);
        entityManager.persist(user);
        entityManager.flush();
        entityManager.clear();
        return appUserRepository.findByEmailIgnoreCase(email).orElseThrow();
    }

    private static class TestClock extends Clock {
        private Instant instant;
        private ZoneId zone;

        private TestClock(Instant instant) {
            this(instant, ZoneId.of("UTC"));
        }

        private TestClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new TestClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
