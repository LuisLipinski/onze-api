package com.onze.api.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import com.onze.api.auth.AuthModels.PasswordResetConfirmRequest;
import com.onze.api.auth.AuthModels.PasswordResetRequest;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration CODE_TTL = Duration.ofMinutes(15);
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository resetCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetCodeRepository resetCodeRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetEmailSender emailSender) {
        this.userRepository = userRepository;
        this.resetCodeRepository = resetCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
    }

    @Transactional
    public void requestReset(PasswordResetRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            return;
        }

        Instant now = Instant.now();
        var latestCode = resetCodeRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId());
        if (latestCode.isPresent()
                && latestCode.get().getCreatedAt().plus(RESEND_INTERVAL).isAfter(now)) {
            return;
        }

        resetCodeRepository.consumeActiveCodes(user.getId(), now);

        String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        PasswordResetCode resetCode = new PasswordResetCode(
                user.getId(),
                passwordEncoder.encode(code),
                now.plus(CODE_TTL),
                now);
        resetCodeRepository.save(resetCode);

        try {
            emailSender.sendResetCode(email, code);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not send password reset email", exception);
        }
    }

    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(InvalidPasswordResetCodeException::new);

        PasswordResetCode resetCode = resetCodeRepository
                .findTopByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(user.getId())
                .orElseThrow(InvalidPasswordResetCodeException::new);

        Instant now = Instant.now();
        if (resetCode.isExpiredAt(now) || resetCode.getAttemptCount() >= 5) {
            resetCode.consume(now);
            resetCodeRepository.save(resetCode);
            throw new InvalidPasswordResetCodeException();
        }

        if (!passwordEncoder.matches(request.code(), resetCode.getCodeHash())) {
            resetCode.registerFailedAttempt(now);
            resetCodeRepository.save(resetCode);
            throw new InvalidPasswordResetCodeException();
        }

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        resetCode.consume(now);
        resetCodeRepository.save(resetCode);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static final class InvalidPasswordResetCodeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
