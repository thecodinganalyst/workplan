package com.hevlar.workplan.service;

public interface EmailService {

    void sendEmail(String to, String subject, String body);
}
