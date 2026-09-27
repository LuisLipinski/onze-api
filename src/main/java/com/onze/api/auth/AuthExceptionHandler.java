package com.onze.api.auth;

import com.onze.api.auth.AuthService.EmailAlreadyInUseException;
import com.onze.api.auth.AuthService.InvalidCredentialsException;
import com.onze.api.auth.LoginProtectionService.LoginRateLimitExceededException;
import com.onze.api.auth.PasswordResetService.InvalidPasswordResetCodeException;
import com.onze.api.web.ApiErrorResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(EmailAlreadyInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiErrorResponse emailAlreadyInUse() {
        return new ApiErrorResponse("EMAIL_ALREADY_IN_USE", "Este e-mail já está cadastrado.");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ApiErrorResponse invalidCredentials() {
        return new ApiErrorResponse("INVALID_CREDENTIALS", "E-mail ou senha inválidos.");
    }

    @ExceptionHandler(LoginRateLimitExceededException.class)
    ResponseEntity<ApiErrorResponse> loginRateLimitExceeded(LoginRateLimitExceededException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(new ApiErrorResponse(
                        "TOO_MANY_LOGIN_ATTEMPTS",
                        "Muitas tentativas de acesso. Aguarde alguns minutos e tente novamente."));
    }

    @ExceptionHandler(InvalidPasswordResetCodeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiErrorResponse invalidPasswordResetCode() {
        return new ApiErrorResponse(
                "INVALID_OR_EXPIRED_RESET_CODE",
                "Código inválido ou expirado. Solicite um novo código.");
    }
}
