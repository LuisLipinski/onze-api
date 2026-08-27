package com.onze.api.auth;

import com.onze.api.auth.AuthModels.AuthResponse;
import com.onze.api.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "security.jwt.secret=onze-integration-test-secret-with-at-least-32-bytes",
        "security.jwt.issuer=onze-api-integration-test"
})
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("onze_test")
            .withUsername("onze")
            .withPassword("onze");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetCodeRepository resetCodeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        resetCodeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldRegisterLoginAndReadAuthenticatedUserAgainstRealPostgres() throws Exception {
        String password = "StrongPass123!";

        var registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "P0.User@Example.com",
                                  "password": "%s",
                                  "displayName": "  Jogador P0  "
                                }
                                """.formatted(password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("p0.user@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("Jogador P0"))
                .andReturn();

        AuthResponse registerResponse = jsonMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                AuthResponse.class);

        var persistedUser = userRepository.findByEmailIgnoreCase("p0.user@example.com").orElseThrow();
        assertThat(persistedUser.getPasswordHash()).isNotEqualTo(password);
        assertThat(persistedUser.getDisplayName()).isEqualTo("Jogador P0");

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "p0.user@example.com",
                                  "password": "%s"
                                }
                                """.formatted(password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();

        AuthResponse loginResponse = jsonMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthResponse.class);

        assertThat(loginResponse.accessToken()).isNotBlank();
        assertThat(loginResponse.user().id()).isEqualTo(registerResponse.user().id());

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(loginResponse.user().id().toString()))
                .andExpect(jsonPath("$.email").value("p0.user@example.com"))
                .andExpect(jsonPath("$.displayName").value("Jogador P0"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void shouldRejectDuplicateEmailIgnoringCase() throws Exception {
        String payload = """
                {
                  "email": "player@example.com",
                  "password": "StrongPass123!",
                  "displayName": "Jogador"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("player@example.com", "PLAYER@EXAMPLE.COM")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"));
    }

    @Test
    void shouldRejectInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "player@example.com",
                                  "password": "StrongPass123!",
                                  "displayName": "Jogador"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "player@example.com",
                                  "password": "WrongPass123!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldReturnGenericMessageForUnknownPasswordResetEmail() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "missing@example.com"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "Se existir uma conta com este e-mail, enviaremos um código de recuperação."));

        assertThat(resetCodeRepository.count()).isZero();
    }

    @Test
    void shouldPersistResetCodeAndCountInvalidAttemptAgainstRealPostgres() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "reset@example.com",
                                  "password": "StrongPass123!",
                                  "displayName": "Jogador Reset"
                                }
                                """))
                .andExpect(status().isCreated());

        var user = userRepository.findByEmailIgnoreCase("reset@example.com").orElseThrow();

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "RESET@EXAMPLE.COM"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "Se existir uma conta com este e-mail, enviaremos um código de recuperação."));

        var reset = resetCodeRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()).orElseThrow();
        assertThat(reset.getCodeHash()).isNotBlank();
        assertThat(reset.getExpiresAt()).isAfter(reset.getCreatedAt());
        assertThat(reset.getAttemptCount()).isZero();

        String wrongCode = passwordEncoder.matches("000000", reset.getCodeHash()) ? "000001" : "000000";

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "reset@example.com",
                                  "code": "%s",
                                  "newPassword": "NewStrongPass123!"
                                }
                                """.formatted(wrongCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_RESET_CODE"));

        var updatedReset = resetCodeRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()).orElseThrow();
        assertThat(updatedReset.getAttemptCount()).isEqualTo(1);
        assertThat(updatedReset.getConsumedAt()).isNull();
    }

    @Test
    void shouldProtectCurrentUserEndpointWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
