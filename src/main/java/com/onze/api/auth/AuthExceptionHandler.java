package com.onze.api.auth;

import com.onze.api.auth.AuthModels.ErrorResponse;
import com.onze.api.auth.AuthService.EmailAlreadyInUseException;
import com.onze.api.auth.AuthService.InvalidCredentialsException;
import com.onze.api.auth.PasswordResetService.InvalidPasswordResetCodeException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(EmailAlreadyInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse emailAlreadyInUse() {
        return new ErrorResponse("EMAIL_ALREADY_IN_USE", "Este e-mail já está cadastrado.");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse invalidCredentials() {
        return new ErrorResponse("INVALID_CREDENTIALS", "E-mail ou senha inválidos.");
    }

    @ExceptionHandler(InvalidPasswordResetCodeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidPasswordResetCode() {
        return new ErrorResponse(
                "INVALID_OR_EXPIRED_RESET_CODE",
                "Código inválido ou expirado. Solicite um novo código.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse validationError() {
        return new ErrorResponse("VALIDATION_ERROR", "Verifique os dados informados.");
    }
}
