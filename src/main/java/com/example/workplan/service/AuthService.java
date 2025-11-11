package com.example.workplan.service;

import com.example.workplan.domain.AppUser;
import com.example.workplan.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AuthService {

    public record OtpDetails(AppUser user, String otp) {}

    private final AppUserRepository appUserRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;
    private final long otpExpirationMinutes;

    public AuthService(AppUserRepository appUserRepository,
                       Clock clock,
                       @Value("${workplan.otp.expiration-minutes:10}") long otpExpirationMinutes) {
        this.appUserRepository = appUserRepository;
        this.clock = clock;
        this.otpExpirationMinutes = otpExpirationMinutes;
    }

    @Transactional
    public String generateOtpForUser(AppUser user) {
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        user.setLatestOtp(otp);
        user.setOtpGeneratedAt(LocalDateTime.now(clock));
        appUserRepository.save(user);
        return otp;
    }

    @Transactional
    public OtpDetails generateOtpForEmail(String email) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new IllegalArgumentException("No user found with email " + email));
        String otp = generateOtpForUser(user);
        return new OtpDetails(user, otp);
    }

    @Transactional
    public AppUser verifyOtp(String email, String otp) {
        AppUser user = appUserRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new IllegalArgumentException("No user found with email " + email));
        if (user.getLatestOtp() == null || !user.getLatestOtp().equals(otp)) {
            throw new IllegalArgumentException("Invalid OTP provided");
        }
        LocalDateTime generatedAt = user.getOtpGeneratedAt();
        if (generatedAt == null || generatedAt.plusMinutes(otpExpirationMinutes).isBefore(LocalDateTime.now(clock))) {
            throw new IllegalArgumentException("OTP has expired");
        }
        user.setLatestOtp(null);
        user.setOtpGeneratedAt(null);
        return user;
    }
}
