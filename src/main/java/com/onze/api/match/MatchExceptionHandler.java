package com.onze.api.match;

import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupNotFoundException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.match.MatchModels.ErrorResponse;
import com.onze.api.match.MatchService.AttendanceClosedException;
import com.onze.api.match.MatchService.AdministratorReentryRequiredException;
import com.onze.api.match.MatchService.InvalidTimeZoneException;
import com.onze.api.match.MatchService.InvalidRentalGoalkeeperNameException;
import com.onze.api.match.MatchService.GoalkeeperPaymentExemptException;
import com.onze.api.match.MatchService.GoalkeeperRequiresAttendanceException;
import com.onze.api.match.MatchService.MatchAlreadyStartedException;
import com.onze.api.match.MatchService.MatchCancelledException;
import com.onze.api.match.MatchService.MatchFullException;
import com.onze.api.match.MatchService.MatchMustBeInFutureException;
import com.onze.api.match.MatchService.MatchNotFoundException;
import com.onze.api.match.MatchService.MatchSeriesNotFoundException;
import com.onze.api.match.MatchService.InvalidPaymentConfigurationException;
import com.onze.api.match.MatchService.InvalidPaymentSettlementResolutionException;
import com.onze.api.match.MatchService.InvalidMatchDeadlinesException;
import com.onze.api.match.MatchService.PaymentDeadlinePassedException;
import com.onze.api.match.MatchService.PaymentNotRequiredException;
import com.onze.api.match.MatchService.PaymentRequiresAttendanceException;
import com.onze.api.match.MatchService.PaymentSettlementNotOpenException;
import com.onze.api.match.MatchService.SignupDeadlinePassedException;
import com.onze.api.match.MatchService.ReplacementPlayerUnavailableException;
import com.onze.api.match.MatchService.ReplacementRequiredForSettlementException;
import com.onze.api.match.MatchService.ReplacementVacancyNotOpenException;
import com.onze.api.match.MatchService.RentalGoalkeeperNotFoundException;
import com.onze.api.match.MatchService.InvalidGuestException;
import com.onze.api.match.MatchService.GuestNotFoundException;
import com.onze.api.match.MatchFormatPolicy.InvalidMatchFormatException;
import com.onze.api.match.MatchPlayerPolicy.InvalidMinimumPlayersException;
import com.onze.api.technical.TechnicalRatings.InvalidTechnicalRatingException;
import com.onze.api.match.MatchTeamService.InternalMatchRequiredException;
import com.onze.api.match.MatchTeamService.MinimumPlayersNotReachedException;
import com.onze.api.match.MatchTeamService.GoalkeepersNotReadyException;
import com.onze.api.match.MatchTeamService.InvalidTeamAssignmentException;
import com.onze.api.match.MatchTeamService.TeamAssignmentNotFoundException;
import com.onze.api.match.MatchTeamService.IneligibleGoalkeeperException;
import com.onze.api.match.MatchTeamImageService.InvalidTeamImageException;
import com.onze.api.match.MatchTeamImageService.TeamImageLockedException;
import com.onze.api.match.MatchTeamImageService.TeamImageStorageNotConfiguredException;
import com.onze.api.match.MatchTeamImageService.TeamImageUploadFailedException;
import com.onze.api.match.MatchGoalkeeperService.GoalkeeperCandidateRequiredException;
import com.onze.api.match.MatchGoalkeeperService.GoalkeeperPaymentAlreadyRecordedException;
import com.onze.api.match.MatchGoalkeeperService.PrimaryGoalkeeperCannotBeUnassignedException;
import com.onze.api.match.LiveMatchService.InvalidLiveMatchTransitionException;
import com.onze.api.match.LiveMatchService.InvalidLiveMatchScoreException;
import com.onze.api.match.LiveMatchService.InvalidGoalEventException;
import com.onze.api.match.LiveMatchService.InvalidCardEventException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {MatchController.class, PushDeviceController.class})
public class MatchExceptionHandler {

    @ExceptionHandler(InvalidLiveMatchTransitionException.class)
    ResponseEntity<ErrorResponse> invalidLiveTransition() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("INVALID_LIVE_MATCH_TRANSITION", "A partida não está no estado correto para esta ação."));
    }

    @ExceptionHandler(InvalidLiveMatchScoreException.class)
    ResponseEntity<ErrorResponse> invalidLiveScore() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_LIVE_MATCH_SCORE", "O placar informado não é válido para esta partida."));
    }

    @ExceptionHandler(InvalidGoalEventException.class)
    ResponseEntity<ErrorResponse> invalidGoalEvent() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_GOAL_EVENT", "Confira o autor, a assistência e o time do gol."));
    }

    @ExceptionHandler(InvalidCardEventException.class)
    ResponseEntity<ErrorResponse> invalidCardEvent() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_CARD_EVENT", "Confira o jogador e o time do cartão."));
    }

    @ExceptionHandler(InvalidTeamImageException.class)
    ResponseEntity<ErrorResponse> invalidTeamImage() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_TEAM_IMAGE",
                        "Escolha uma imagem válida de até 5 MB para um time desta partida."));
    }

    @ExceptionHandler(TeamImageLockedException.class)
    ResponseEntity<ErrorResponse> teamImageLocked() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "TEAM_IMAGE_LOCKED",
                        "As imagens dos times não podem ser alteradas após o encerramento da partida."));
    }

    @ExceptionHandler(TeamImageStorageNotConfiguredException.class)
    ResponseEntity<ErrorResponse> teamImageStorageNotConfigured() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(
                        "TEAM_IMAGE_STORAGE_NOT_CONFIGURED",
                        "O envio de imagens ainda não está disponível."));
    }

    @ExceptionHandler(TeamImageUploadFailedException.class)
    ResponseEntity<ErrorResponse> teamImageUploadFailed() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(
                        "TEAM_IMAGE_UPLOAD_FAILED",
                        "Não foi possível enviar a imagem agora. Tente novamente."));
    }

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

    @ExceptionHandler(InvalidRentalGoalkeeperNameException.class)
    ResponseEntity<ErrorResponse> invalidRentalGoalkeeperName() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_RENTAL_GOALKEEPER_NAME",
                        "Informe o nome do goleiro de aluguel."));
    }

    @ExceptionHandler(InvalidMatchDeadlinesException.class)
    ResponseEntity<ErrorResponse> invalidMatchDeadlines() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_MATCH_DEADLINES",
                        "Defina prazos futuros, na ordem correta e antes do início do jogo."));
    }

    @ExceptionHandler(InvalidMatchFormatException.class)
    ResponseEntity<ErrorResponse> invalidMatchFormat() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_MATCH_FORMAT",
                        "Entre membros exige ao menos 2 times e goleiros em quantidade igual ou maior; contra outro time exige ao menos 1 goleiro e não usa quantidade de times."));
    }

    @ExceptionHandler(InvalidMinimumPlayersException.class)
    ResponseEntity<ErrorResponse> invalidMinimumPlayers() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_MINIMUM_PLAYERS",
                        "A quantidade mínima deve ser maior que zero e não pode ultrapassar o limite de jogadores."));
    }

    @ExceptionHandler(InvalidTechnicalRatingException.class)
    ResponseEntity<ErrorResponse> invalidTechnicalRating() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_TECHNICAL_RATING",
                        "Cada habilidade avaliada deve ter um valor inteiro de 1 a 10."));
    }

    @ExceptionHandler(InvalidGuestException.class)
    ResponseEntity<ErrorResponse> invalidGuest() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_GUEST",
                        "Informe nome, posição principal e uma posição secundária diferente."));
    }

    @ExceptionHandler(GuestNotFoundException.class)
    ResponseEntity<ErrorResponse> guestNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("GUEST_NOT_FOUND", "Convidado não encontrado nesta partida."));
    }

    @ExceptionHandler(InternalMatchRequiredException.class)
    ResponseEntity<ErrorResponse> internalMatchRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "INTERNAL_MATCH_REQUIRED",
                        "A formação automática de times está disponível para partidas entre membros."));
    }

    @ExceptionHandler(MinimumPlayersNotReachedException.class)
    ResponseEntity<ErrorResponse> minimumPlayersNotReached(MinimumPlayersNotReachedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "MINIMUM_PLAYERS_NOT_REACHED",
                        "Ainda faltam " + exception.getMissingPlayers()
                                + " jogador(es) para formar os times."));
    }

    @ExceptionHandler(GoalkeepersNotReadyException.class)
    ResponseEntity<ErrorResponse> goalkeepersNotReady(GoalkeepersNotReadyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "GOALKEEPERS_NOT_READY",
                        "Ainda faltam " + exception.getMissingGoalkeepers()
                                + " goleiro(s) para formar os times."));
    }

    @ExceptionHandler(InvalidTeamAssignmentException.class)
    ResponseEntity<ErrorResponse> invalidTeamAssignment() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_TEAM_ASSIGNMENT",
                        "Informe um time e uma função válidos para a modalidade da partida."));
    }

    @ExceptionHandler(TeamAssignmentNotFoundException.class)
    ResponseEntity<ErrorResponse> teamAssignmentNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        "TEAM_ASSIGNMENT_NOT_FOUND",
                        "Escalação não encontrada nesta partida."));
    }

    @ExceptionHandler(IneligibleGoalkeeperException.class)
    ResponseEntity<ErrorResponse> ineligibleGoalkeeper() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "INELIGIBLE_GOALKEEPER",
                        "Este participante não pode ser escalado automaticamente como goleiro."));
    }

    @ExceptionHandler(AttendanceClosedException.class)
    ResponseEntity<ErrorResponse> attendanceClosed() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "ATTENDANCE_CLOSED",
                        "A confirmação de presença ainda não abriu ou este jogo já começou."));
    }

    @ExceptionHandler(SignupDeadlinePassedException.class)
    ResponseEntity<ErrorResponse> signupDeadlinePassed() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "SIGNUP_DEADLINE_PASSED",
                        "O prazo para entrar na lista terminou."));
    }

    @ExceptionHandler(PaymentDeadlinePassedException.class)
    ResponseEntity<ErrorResponse> paymentDeadlinePassed() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "PAYMENT_DEADLINE_PASSED",
                        "O prazo para informar o pagamento terminou."));
    }

    @ExceptionHandler(AdministratorReentryRequiredException.class)
    ResponseEntity<ErrorResponse> administratorReentryRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "ADMINISTRATOR_REENTRY_REQUIRED",
                        "Depois de sair com pagamento registrado, somente um administrador pode colocar você novamente na lista."));
    }

    @ExceptionHandler(ReplacementVacancyNotOpenException.class)
    ResponseEntity<ErrorResponse> replacementVacancyNotOpen() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "REPLACEMENT_VACANCY_NOT_OPEN",
                        "Esta saída não possui uma vaga aguardando reposição."));
    }

    @ExceptionHandler(ReplacementPlayerUnavailableException.class)
    ResponseEntity<ErrorResponse> replacementPlayerUnavailable() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "REPLACEMENT_PLAYER_UNAVAILABLE",
                        "Escolha um membro que ainda não esteja confirmado nem aguardando outro acerto nesta partida."));
    }

    @ExceptionHandler(ReplacementRequiredForSettlementException.class)
    ResponseEntity<ErrorResponse> replacementRequiredForSettlement() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "REPLACEMENT_REQUIRED_FOR_SETTLEMENT",
                        "O acerto ficará bloqueado até um administrador preencher a vaga deste jogador."));
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

    @ExceptionHandler(GoalkeeperRequiresAttendanceException.class)
    ResponseEntity<ErrorResponse> goalkeeperRequiresAttendance() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "GOALKEEPER_REQUIRES_ATTENDANCE",
                        "Somente um jogador confirmado pode ser definido como goleiro desta partida."));
    }

    @ExceptionHandler(GoalkeeperCandidateRequiredException.class)
    ResponseEntity<ErrorResponse> goalkeeperCandidateRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "GOALKEEPER_CANDIDATE_REQUIRED",
                        "Escolha um jogador confirmado que tenha goleiro como posição ou que aceite jogar no gol."));
    }

    @ExceptionHandler(PrimaryGoalkeeperCannotBeUnassignedException.class)
    ResponseEntity<ErrorResponse> primaryGoalkeeperCannotBeUnassigned() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "PRIMARY_GOALKEEPER_CANNOT_BE_UNASSIGNED",
                        "Um jogador confirmado com goleiro como posição principal permanece goleiro nesta partida."));
    }

    @ExceptionHandler(GoalkeeperPaymentExemptException.class)
    ResponseEntity<ErrorResponse> goalkeeperPaymentExempt() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "GOALKEEPER_PAYMENT_EXEMPT",
                        "Este goleiro está isento do pagamento nesta partida."));
    }

    @ExceptionHandler(GoalkeeperPaymentAlreadyRecordedException.class)
    ResponseEntity<ErrorResponse> goalkeeperPaymentAlreadyRecorded() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "GOALKEEPER_PAYMENT_ALREADY_RECORDED",
                        "Não é possível isentar o goleiro porque já existe pagamento em dinheiro informado ou confirmado."));
    }

    @ExceptionHandler(RentalGoalkeeperNotFoundException.class)
    ResponseEntity<ErrorResponse> rentalGoalkeeperNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        "RENTAL_GOALKEEPER_NOT_FOUND",
                        "Goleiro de aluguel não encontrado nesta partida."));
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
