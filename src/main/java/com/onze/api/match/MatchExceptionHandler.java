package com.onze.api.match;

import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupNotFoundException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.match.MatchModels.ErrorResponse;
import com.onze.api.match.MatchService.AttendanceClosedException;
import com.onze.api.match.MatchService.InvalidTimeZoneException;
import com.onze.api.match.MatchService.MatchAlreadyStartedException;
import com.onze.api.match.MatchService.MatchCancelledException;
import com.onze.api.match.MatchService.MatchFullException;
import com.onze.api.match.MatchService.MatchMustBeInFutureException;
import com.onze.api.match.MatchService.MatchNotFoundException;
import com.onze.api.match.MatchService.MatchSeriesNotFoundException;
import com.onze.api.match.MatchService.InvalidPaymentConfigurationException;
import com.onze.api.match.MatchService.InvalidPaymentSettlementResolutionException;
import com.onze.api.match.MatchService.PaymentNotRequiredException;
import com.onze.api.match.MatchService.PaymentRequiresAttendanceException;
import com.onze.api.match.MatchService.PaymentSettlementNotOpenException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {MatchController.class, PushDeviceController.class})
public class MatchExceptionHandler {

    @ExceptionHandler(MatchNotFoundException.class)
    ResponseEntity<ErrorResponse> matchNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("MATCH_NOT_FOUND", "Jogo não encontrado."));
    }

    @ExceptionHandler(MatchSeriesNotFoundException.class)
    ResponseEntity<ErrorResponse> seriesNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("MATCH_SERIES_NOT_FOUND", "Sequência semanal não encontrada."));
    }

    @ExceptionHandler(GroupNotFoundException.class)
    ResponseEntity<ErrorResponse> groupNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("GROUP_NOT_FOUND", "Grupo não encontrado."));
    }

    @ExceptionHandler(GroupAccessDeniedException.class)
    ResponseEntity<ErrorResponse> accessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("GROUP_ACCESS_DENIED", "Você não tem permissão para realizar esta ação."));
    }

    @ExceptionHandler(GroupUserNotFoundException.class)
    ResponseEntity<ErrorResponse> invalidSession() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_SESSION", "Sessão inválida."));
    }

    @ExceptionHandler(MatchMustBeInFutureException.class)
    ResponseEntity<ErrorResponse> matchMustBeInFuture() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("MATCH_MUST_BE_IN_FUTURE", "Escolha uma data e um horário futuros."));
    }

    @ExceptionHandler(InvalidTimeZoneException.class)
    ResponseEntity<ErrorResponse> invalidTimeZone() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_TIME_ZONE", "O fuso horário informado não é válido."));
    }

    @ExceptionHandler(InvalidPaymentConfigurationException.class)
    ResponseEntity<ErrorResponse> invalidPaymentConfiguration() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_PAYMENT_CONFIGURATION",
                        "Informe um valor e uma chave PIX válidos para esta partida."));
    }

    @ExceptionHandler(AttendanceClosedException.class)
    ResponseEntity<ErrorResponse> attendanceClosed() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "ATTENDANCE_CLOSED",
                        "A confirmação de presença ainda não abriu ou este jogo já começou."));
    }

    @ExceptionHandler(MatchCancelledException.class)
    ResponseEntity<ErrorResponse> matchCancelled() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("MATCH_CANCELLED", "Este jogo foi cancelado."));
    }

    @ExceptionHandler(MatchFullException.class)
    ResponseEntity<ErrorResponse> matchFull() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("MATCH_FULL", "Todas as vagas deste jogo já foram preenchidas."));
    }

    @ExceptionHandler(MatchAlreadyStartedException.class)
    ResponseEntity<ErrorResponse> matchAlreadyStarted() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("MATCH_ALREADY_STARTED", "Não é possível cancelar um jogo que já começou."));
    }

    @ExceptionHandler(PaymentNotRequiredException.class)
    ResponseEntity<ErrorResponse> paymentNotRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "PAYMENT_NOT_REQUIRED",
                        "Esta partida não possui cobrança configurada."));
    }

    @ExceptionHandler(PaymentRequiresAttendanceException.class)
    ResponseEntity<ErrorResponse> paymentRequiresAttendance() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "PAYMENT_REQUIRES_ATTENDANCE",
                        "Confirme que vai jogar antes de informar ou validar o pagamento."));
    }

    @ExceptionHandler(PaymentSettlementNotOpenException.class)
    ResponseEntity<ErrorResponse> paymentSettlementNotOpen() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "PAYMENT_SETTLEMENT_NOT_OPEN",
                        "Este pagamento não possui um acerto pendente."));
    }

    @ExceptionHandler(InvalidPaymentSettlementResolutionException.class)
    ResponseEntity<ErrorResponse> invalidPaymentSettlementResolution() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "INVALID_PAYMENT_SETTLEMENT_RESOLUTION",
                        "Um pagamento já confirmado não pode ser marcado como não recebido."));
    }
}
