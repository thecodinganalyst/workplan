package com.example.workplan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConsoleEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailService.class);

    @Override
    public void sendEmail(String to, String subject, String body) {
        log.info("Sending email to {} with subject '{}':\n{}", to, subject, body);
    }
}
