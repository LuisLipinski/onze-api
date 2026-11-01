package com.onze.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import com.onze.api.IntegrationTestSupport;
import com.onze.api.auth.AuthModels.AuthResponse;
import com.onze.api.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "security.jwt.secret=onze-refresh-integration-secret-with-at-least-32-bytes",
        "security.jwt.issuer=onze-refresh-test"
})
@AutoConfigureMockMvc
@Import(RefreshSessionIntegrationTest.TimeConfiguration.class)
class RefreshSessionIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordResetCodeRepository resetCodes;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired JdbcTemplate db;
    @Autowired MutableClock clock;

    @BeforeEach void reset() {
        clock.current.set(Instant.now());
        resetCodes.deleteAll();
        users.deleteAll();
    }

    private AuthResponse register() throws Exception {
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"refresh@onze.test","password":"StrongPass123!","displayName":"Jogador"}
                        """)).andExpect(status().isCreated()).andReturn();
        return response(result);
    }

    private AuthResponse response(MvcResult result) throws Exception {
        return json.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private MvcResult refresh(String token, int expected) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new AuthModels.RefreshRequest(token))))
                .andExpect(status().is(expected)).andReturn();
    }

    @Test void fingerprintCredentialCanRenewAfterAccessTokenExpires() throws Exception {
        var original = register();
        assertThat(original.refreshToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(original.refreshExpiresIn()).isEqualTo(Duration.ofDays(30).toSeconds());
        String stored = db.queryForObject("select token_hash from auth_refresh_tokens", String.class);
        assertThat(stored).isEqualTo(RefreshTokenService.hash(original.refreshToken()));
        assertThat(stored).isNotEqualTo(original.refreshToken());
        clock.advance(Duration.ofHours(3));
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + original.accessToken()))
                .andExpect(status().isUnauthorized());
        var renewed = response(refresh(original.refreshToken(), 200));
        assertThat(renewed.user().id()).isEqualTo(original.user().id());
        assertThat(renewed.accessToken()).isNotEqualTo(original.accessToken());
        assertThat(renewed.refreshToken()).isNotEqualTo(original.refreshToken());
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + renewed.accessToken()))
                .andExpect(status().isOk());
    }

    @Test void replayCommitsRevocationOfTheLatestTokenInTheFamily() throws Exception {
        var first = register();
        var second = response(refresh(first.refreshToken(), 200));
        refresh(first.refreshToken(), 401);
        refresh(second.refreshToken(), 401);
        assertThat(db.queryForObject("select count(*) from auth_refresh_tokens where revoked_at is null", Integer.class))
                .isZero();
    }

    @Test void logoutIsIdempotentAndDoesNotRevokeAnotherLogin() throws Exception {
        var first = register();
        var loginResult = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(""" 
                        {"email":"refresh@onze.test","password":"StrongPass123!"}
                        """)).andExpect(status().isOk()).andReturn();
        var other = response(loginResult);
        for (int call = 0; call < 2; call++) {
            mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new AuthModels.RefreshRequest(first.refreshToken()))))
                    .andExpect(status().isNoContent());
        }
        refresh(first.refreshToken(), 401);
        refresh(other.refreshToken(), 200);
    }

    @Test void idleAndAbsoluteExpirationAreEnforcedByServerClock() throws Exception {
        var original = register();
        clock.advance(Duration.ofDays(30));
        refresh(original.refreshToken(), 401);
        var newLogin = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"refresh@onze.test","password":"StrongPass123!"}
                        """)).andExpect(status().isOk()).andReturn();
        var active = response(newLogin);
        for (int turn = 0; turn < 4; turn++) {
            clock.advance(Duration.ofDays(20));
            active = response(refresh(active.refreshToken(), 200));
        }
        assertThat(active.refreshExpiresIn()).isEqualTo(Duration.ofDays(10).toSeconds());
        clock.advance(Duration.ofDays(10));
        refresh(active.refreshToken(), 401);
    }

    private String legacy(AuthResponse account) {
        var claims = JwtClaimsSet.builder().issuer("onze-refresh-test")
                .subject(account.user().id().toString()).issuedAt(clock.instant().minusSeconds(1))
                .expiresAt(clock.instant().plus(Duration.ofHours(2))).build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    @Test void legacyInstallationMigratesOneValidJwtWithoutPassword() throws Exception {
        var original = register();
        String legacy = legacy(original);
        var result = mvc.perform(post("/api/auth/sessions/legacy")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + legacy)).andExpect(status().isOk()).andReturn();
        var migrated = response(result);
        assertThat(migrated.user().id()).isEqualTo(original.user().id());
        assertThat(decoder.decode(migrated.accessToken()).getId()).isNotBlank();
        mvc.perform(post("/api/auth/sessions/legacy").header(HttpHeaders.AUTHORIZATION, "Bearer " + legacy))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/sessions/legacy").header(HttpHeaders.AUTHORIZATION, "Bearer " + migrated.accessToken()))
                .andExpect(status().isUnauthorized());
        clock.advance(Duration.ofHours(3));
        refresh(migrated.refreshToken(), 200);
        mvc.perform(post("/api/auth/sessions/legacy").header(HttpHeaders.AUTHORIZATION, "Bearer " + legacy))
                .andExpect(status().isUnauthorized());
    }

    @Test void passwordResetRevokesRefreshAndPreventsOldLegacyExchange() throws Exception {
        var original = register();
        var second = response(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"refresh@onze.test","password":"StrongPass123!"}
                        """)).andExpect(status().isOk()).andReturn());
        String legacy = legacy(original);
        Instant now = Instant.now();
        resetCodes.save(new PasswordResetCode(original.user().id(), passwords.encode("123456"),
                now.plusSeconds(900), now));
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"refresh@onze.test","code":"123456","newPassword":"NewStrongPass123!"}
                        """)).andExpect(status().isOk());
        refresh(original.refreshToken(), 401);
        refresh(second.refreshToken(), 401);
        mvc.perform(post("/api/auth/sessions/legacy").header(HttpHeaders.AUTHORIZATION, "Bearer " + legacy))
                .andExpect(status().isUnauthorized());
        var after = response(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"refresh@onze.test","password":"NewStrongPass123!"}
                        """)).andExpect(status().isOk()).andReturn());
        refresh(after.refreshToken(), 200);
    }

    @Test void concurrentUseOfOneTokenIsDetectedAndRevocationPersists() throws Exception {
        var account = register();
        var start = new CountDownLatch(1);
        List<Future<MvcResult>> pending = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int call = 0; call < 2; call++) pending.add(executor.submit(() -> {
                start.await();
                return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new AuthModels.RefreshRequest(account.refreshToken()))))
                        .andReturn();
            }));
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            AuthResponse winner = null;
            for (var future : pending) {
                var result = future.get();
                codes.add(result.getResponse().getStatus());
                if (result.getResponse().getStatus() == 200) winner = response(result);
            }
            assertThat(codes).containsExactlyInAnyOrder(200, 401);
            assertThat(winner).isNotNull();
            refresh(winner.refreshToken(), 401);
        }
    }

    @Test void malformedOrUnknownTokensNeverCreateSessions() throws Exception {
        refresh("short", 400);
        refresh("z".repeat(43), 401);
        mvc.perform(post("/api/auth/sessions/legacy")).andExpect(status().isUnauthorized());
        assertThat(db.queryForObject("select count(*) from auth_refresh_tokens", Integer.class)).isZero();
    }

    static final class MutableClock extends Clock {
        final AtomicReference<Instant> current = new AtomicReference<>(Instant.now());
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return current.get(); }
        void advance(Duration duration) { current.updateAndGet(value -> value.plus(duration)); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary MutableClock refreshClock() { return new MutableClock(); }
    }
}
