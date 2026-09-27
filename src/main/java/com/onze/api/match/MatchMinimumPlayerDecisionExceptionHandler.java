package com.onze.api.match;

import com.onze.api.match.MatchMinimumPlayerDecisionService.InvalidSignupDeadlineExtensionException;
import com.onze.api.match.MatchMinimumPlayerDecisionService.MinimumPlayerDecisionNotRequiredException;
import com.onze.api.match.MatchMinimumPlayerDecisionService.PaymentDeadlineReviewRequiredException;
import com.onze.api.web.ApiErrorResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = MatchMinimumPlayerDecisionController.class)
public class MatchMinimumPlayerDecisionExceptionHandler {

    @ExceptionHandler(MinimumPlayerDecisionNotRequiredException.class)
    public ResponseEntity<ApiErrorResponse> decisionNotRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(
                "MINIMUM_PLAYER_DECISION_NOT_REQUIRED",
                "Este jogo não está aguardando uma decisão por falta do mínimo de jogadores."));
    }

    @ExceptionHandler(PaymentDeadlineReviewRequiredException.class)
    public ResponseEntity<ApiErrorResponse> paymentDeadlineReviewRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(
                "PAYMENT_DEADLINE_REVIEW_REQUIRED",
                "O novo prazo de inscrição ultrapassa o prazo de pagamento. Revise também a data de pagamento."));
    }

    @ExceptionHandler(InvalidSignupDeadlineExtensionException.class)
    public ResponseEntity<ApiErrorResponse> invalidDeadlineExtension() {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "INVALID_SIGNUP_DEADLINE_EXTENSION",
                "Informe novos prazos válidos, futuros e anteriores ao início do jogo."));
    }
}
