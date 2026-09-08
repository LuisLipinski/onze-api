package com.onze.api.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
public class ResendPasswordResetEmailSender implements PasswordResetEmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResendPasswordResetEmailSender.class);
    private static final URI RESEND_EMAILS_URI = URI.create("https://api.resend.com/emails");

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String from;

    public ResendPasswordResetEmailSender(
            JsonMapper jsonMapper,
            @Value("${email.resend.api-key:}") String apiKey,
            @Value("${email.resend.from:}") String from) {
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.apiKey = apiKey;
        this.from = from;
    }

    @Override
    public void sendResetCode(String email, String code) {
        if (apiKey.isBlank() || from.isBlank()) {
            LOGGER.warn("Password reset email skipped because Resend is not configured");
            return;
        }

        Map<String, Object> payload = Map.of(
                "from", from,
                "to", List.of(email),
                "subject", "Seu código de recuperação do Onze",
                "text", "Seu código de recuperação é " + code
                        + ". Ele expira em 15 minutos. Se você não solicitou a troca de senha, ignore este e-mail.");

        try {
            HttpRequest request = HttpRequest.newBuilder(RESEND_EMAILS_URI)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Resend returned HTTP " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Password reset email delivery was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not deliver password reset email", exception);
        }
    }
}
