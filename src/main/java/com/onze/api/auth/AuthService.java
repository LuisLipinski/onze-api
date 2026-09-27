package com.onze.api.auth;

import java.util.Locale;

import com.onze.api.auth.AuthModels.AuthResponse;
import com.onze.api.auth.AuthModels.LoginRequest;
import com.onze.api.auth.AuthModels.RegisterRequest;
import com.onze.api.auth.AuthModels.UserResponse;
import com.onze.api.auth.LoginProtectionService.LoginRateLimitExceededException;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5xsgbTVdyv7i3LJtQ6e13pZ.EyU5I0a";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginProtectionService loginProtectionService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            LoginProtectionService loginProtectionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginProtectionService = loginProtectionService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyInUseException();
        }

        User user = new User(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim());
        User savedUser = userRepository.save(user);
        return authenticated(savedUser);
    }

    @Transactional(noRollbackFor = {
            InvalidCredentialsException.class,
            LoginRateLimitExceededException.class
    })
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailForLogin(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_PASSWORD_HASH);
            throw new InvalidCredentialsException();
        }

        try {
            loginProtectionService.requireLoginAllowed(user);
        } catch (LoginRateLimitExceededException exception) {
            LOGGER.warn("Rejected login while user {} is temporarily blocked", user.getId());
            throw exception;
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            try {
                loginProtectionService.registerFailedAttempt(user);
            } catch (LoginRateLimitExceededException exception) {
                LOGGER.warn("Temporarily blocked login for user {} after repeated failures", user.getId());
                throw exception;
            }
            throw new InvalidCredentialsException();
        }

        loginProtectionService.clear(user);
        return authenticated(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String userId) {
        try {
            return userRepository.findById(java.util.UUID.fromString(userId))
                    .map(UserResponse::from)
                    .orElseThrow(InvalidCredentialsException::new);
        } catch (IllegalArgumentException exception) {
            throw new InvalidCredentialsException();
        }
    }

    private AuthResponse authenticated(User user) {
        TokenService.IssuedToken issuedToken = tokenService.issueToken(user);
        return new AuthResponse(
                issuedToken.value(),
                "Bearer",
                issuedToken.expiresInSeconds(),
                UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static final class EmailAlreadyInUseException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class InvalidCredentialsException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
