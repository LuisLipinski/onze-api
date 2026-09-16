package com.onze.api.match;

import com.onze.api.match.MatchMinimumPlayerDecisionService.InvalidSignupDeadlineExtensionException;
import com.onze.api.match.MatchMinimumPlayerDecisionService.MinimumPlayerDecisionNotRequiredException;
import com.onze.api.match.MatchMinimumPlayerDecisionService.PaymentDeadlineReviewRequiredException;
import com.onze.api.match.MatchModels.ErrorResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MatchMinimumPlayerDecisionController.class)
public class MatchMinimumPlayerDecisionExceptionHandler {

    @ExceptionHandler(MinimumPlayerDecisionNotRequiredException.class)
    public ResponseEntity<ErrorResponse> decisionNotRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                "MINIMUM_PLAYER_DECISION_NOT_REQUIRED",
                "Esta partida não está aguardando uma decisão por falta do mínimo de jogadores."));
    }

    @ExceptionHandler(PaymentDeadlineReviewRequiredException.class)
    public ResponseEntity<ErrorResponse> paymentDeadlineReviewRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
                "PAYMENT_DEADLINE_REVIEW_REQUIRED",
                "O novo prazo de inscrição ultrapassa o prazo de pagamento. Revise também a data de pagamento."));
    }

    @ExceptionHandler(InvalidSignupDeadlineExtensionException.class)
    public ResponseEntity<ErrorResponse> invalidDeadlineExtension() {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "INVALID_SIGNUP_DEADLINE_EXTENSION",
                "Informe novos prazos válidos, futuros e anteriores ao início da partida."));
    }
}
