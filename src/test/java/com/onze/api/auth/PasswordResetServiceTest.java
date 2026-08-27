package com.onze.api.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.onze.api.auth.AuthModels.PasswordResetConfirmRequest;
import com.onze.api.auth.AuthModels.PasswordResetRequest;
import com.onze.api.auth.PasswordResetService.InvalidPasswordResetCodeException;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetCodeRepository resetCodeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetEmailSender emailSender;

    @Mock
    private User user;

    private PasswordResetService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                userRepository,
                resetCodeRepository,
                passwordEncoder,
                emailSender);
        userId = UUID.randomUUID();
    }

    @Test
    void ignoresUnknownEmailWithoutSendingAnything() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        service.requestReset(new PasswordResetRequest(" Missing@Example.com "));

        verify(emailSender, never()).sendResetCode(any(), any());
        verify(resetCodeRepository, never()).save(any());
    }

    @Test
    void generatesSixDigitCodeAndStoresOnlyItsHash() {
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(resetCodeRepository.findTopByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(argThat(code -> code != null && code.matches("\\d{6}"))))
                .thenReturn("code-hash");

        service.requestReset(new PasswordResetRequest("user@example.com"));

        verify(resetCodeRepository).consumeActiveCodes(any(), any());
        verify(resetCodeRepository).save(argThat(reset ->
                "code-hash".equals(reset.getCodeHash())
                        && reset.getAttemptCount() == 0
                        && reset.getExpiresAt().isAfter(reset.getCreatedAt())));
        verify(emailSender).sendResetCode(
                org.mockito.ArgumentMatchers.eq("user@example.com"),
                argThat(code -> code.matches("\\d{6}")));
    }

    @Test
    void suppressesResendDuringOneMinuteWindow() {
        Instant now = Instant.now();
        PasswordResetCode recent = new PasswordResetCode(
                userId,
                "code-hash",
                now.plusSeconds(900),
                now);

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(resetCodeRepository.findTopByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(recent));

        service.requestReset(new PasswordResetRequest("user@example.com"));

        verify(emailSender, never()).sendResetCode(any(), any());
        verify(resetCodeRepository, never()).save(any());
    }

    @Test
    void changesPasswordAndConsumesValidCode() {
        Instant now = Instant.now();
        PasswordResetCode reset = new PasswordResetCode(
                userId,
                "code-hash",
                now.plusSeconds(900),
                now.minusSeconds(60));

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(resetCodeRepository.findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(reset));
        when(passwordEncoder.matches("123456", "code-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewStrongPass123!"))
                .thenReturn("new-password-hash");

        service.confirmReset(new PasswordResetConfirmRequest(
                "user@example.com",
                "123456",
                "NewStrongPass123!"));

        verify(user).changePasswordHash("new-password-hash");
        verify(userRepository).save(user);
        assertNotNull(reset.getConsumedAt());
    }

    @Test
    void invalidatesCodeAfterFifthFailedAttempt() {
        Instant now = Instant.now();
        PasswordResetCode reset = new PasswordResetCode(
                userId,
                "code-hash",
                now.plusSeconds(900),
                now.minusSeconds(60));
        reset.registerFailedAttempt(now.minusSeconds(4));
        reset.registerFailedAttempt(now.minusSeconds(3));
        reset.registerFailedAttempt(now.minusSeconds(2));
        reset.registerFailedAttempt(now.minusSeconds(1));

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(resetCodeRepository.findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(reset));
        when(passwordEncoder.matches("000000", "code-hash")).thenReturn(false);

        assertThrows(
                InvalidPasswordResetCodeException.class,
                () -> service.confirmReset(new PasswordResetConfirmRequest(
                        "user@example.com",
                        "000000",
                        "NewStrongPass123!")));

        assertNotNull(reset.getConsumedAt());
        verify(resetCodeRepository).save(reset);
        verify(user, never()).changePasswordHash(any());
    }

    @Test
    void rejectsExpiredCode() {
        Instant now = Instant.now();
        PasswordResetCode reset = new PasswordResetCode(
                userId,
                "code-hash",
                now.minusSeconds(1),
                now.minusSeconds(901));

        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);
        when(resetCodeRepository.findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(reset));

        assertThrows(
                InvalidPasswordResetCodeException.class,
                () -> service.confirmReset(new PasswordResetConfirmRequest(
                        "user@example.com",
                        "123456",
                        "NewStrongPass123!")));

        assertNotNull(reset.getConsumedAt());
    }
}
