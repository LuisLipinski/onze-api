package com.onze.api.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> validationError() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "VALIDATION_ERROR",
                        "Verifique os dados informados."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpectedError(
            Exception exception,
            HttpServletRequest request) {
        if (exception instanceof org.springframework.web.ErrorResponse webError
                && webError.getStatusCode().is4xxClientError()) {
            int status = webError.getStatusCode().value();
            String code = status == HttpStatus.NOT_FOUND.value()
                    ? "RESOURCE_NOT_FOUND"
                    : "REQUEST_ERROR";
            String message = status == HttpStatus.NOT_FOUND.value()
                    ? "Recurso não encontrado."
                    : "Não foi possível processar a solicitação.";
            return ResponseEntity.status(webError.getStatusCode())
                    .body(new ApiErrorResponse(code, message));
        }

        LOGGER.error(
                "Unhandled API error on {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        "INTERNAL_ERROR",
                        "Não foi possível concluir a operação. Tente novamente."));
    }
}
