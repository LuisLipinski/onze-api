package com.onze.api.auth;

import java.util.UUID;

import com.onze.api.user.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthModels {

    private AuthModels() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 120) String displayName) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank String password) {
    }

    public record UserResponse(
            UUID id,
            String email,
            String displayName,
            boolean emailVerified) {

        public static UserResponse from(User user) {
            return new UserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.isEmailVerified());
        }
    }

    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserResponse user) {
    }

    public record ErrorResponse(String code, String message) {
    }
}
