package com.onze.api.auth;

import java.util.Locale;

import com.onze.api.auth.AuthModels.AuthResponse;
import com.onze.api.auth.AuthModels.LoginRequest;
import com.onze.api.auth.AuthModels.RegisterRequest;
import com.onze.api.auth.AuthModels.UserResponse;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
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

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

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
