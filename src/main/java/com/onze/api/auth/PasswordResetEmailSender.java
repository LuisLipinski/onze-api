package com.onze.api.auth;

public interface PasswordResetEmailSender {

    void sendResetCode(String email, String code);
}
