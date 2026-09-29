package com.onze.api.match;

import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupNotFoundException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
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
import com.onze.api.match.MatchTimingPolicy.InvalidMatchTimingConfigurationException;
import com.onze.api.match.MatchPlayerPolicy.InvalidMinimumPlayersException;
import com.onze.api.technical.TechnicalRatings.InvalidTechnicalRatingException;
import com.onze.api.match.MatchTeamService.InternalMatchRequiredException;
import com.onze.api.match.MatchTeamService.MinimumPlayersNotReachedException;
import com.onze.api.match.MatchTeamService.GoalkeepersNotReadyException;
import com.onze.api.match.MatchTeamService.InvalidTeamAssignmentException;
import com.onze.api.match.MatchTeamService.TeamAssignmentNotFoundException;
import com.onze.api.match.MatchTeamService.IneligibleGoalkeeperException;
import com.onze.api.match.MatchTeamService.MultipleActiveGoalkeepersException;
import com.onze.api.match.MatchTeamImageService.InvalidTeamImageException;
import com.onze.api.match.MatchTeamImageService.InvalidTeamIdentityException;
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
import com.onze.api.match.LiveMatchService.InvalidPeriodConfigurationException;
import com.onze.api.match.LiveMatchService.MatchPeriodNotReadyException;
import com.onze.api.match.LiveMatchService.InvalidPenaltyShootoutException;
import com.onze.api.web.ApiErrorResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        MatchController.class, MatchTeamReserveController.class, PushDeviceController.class})
public class MatchExceptionHandler {

    @ExceptionHandler(InvalidLiveMatchTransitionException.class)
    ResponseEntity<ApiErrorResponse> invalidLiveTransition() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("INVALID_LIVE_MATCH_TRANSITION", "O jogo não está no estado correto para esta ação."));
    }

    @ExceptionHandler(InvalidLiveMatchScoreException.class)
    ResponseEntity<ApiErrorResponse> invalidLiveScore() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("INVALID_LIVE_MATCH_SCORE", "O placar informado não é válido para este jogo."));
    }

    @ExceptionHandler(InvalidGoalEventException.class)
    ResponseEntity<ApiErrorResponse> invalidGoalEvent() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("INVALID_GOAL_EVENT", "Confira o autor, a assistência e o time do gol."));
    }

    @ExceptionHandler(InvalidCardEventException.class)
    ResponseEntity<ApiErrorResponse> invalidCardEvent() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("INVALID_CARD_EVENT", "Confira o jogador e o time do cartão."));
    }

    @ExceptionHandler(InvalidPeriodConfigurationException.class)
    ResponseEntity<ApiErrorResponse> invalidPeriodConfiguration() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_PERIOD_CONFIGURATION",
                        "Informe uma quantidade válida de minutos para os acréscimos."));
    }

    @ExceptionHandler(MatchPeriodNotReadyException.class)
    ResponseEntity<ApiErrorResponse> matchPeriodNotReady() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "MATCH_PERIOD_NOT_READY",
                        "Este tempo só poderá ser encerrado depois do tempo normal e dos acréscimos definidos."));
    }

    @ExceptionHandler(InvalidPenaltyShootoutException.class)
    ResponseEntity<ApiErrorResponse> invalidPenaltyShootout() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_PENALTY_SHOOTOUT",
                        "Confira a ordem dos batedores e a próxima cobrança da disputa por pênaltis."));
    }

    @ExceptionHandler(InvalidTeamImageException.class)
    ResponseEntity<ApiErrorResponse> invalidTeamImage() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_TEAM_IMAGE",
                        "Escolha uma imagem válida de até 5 MB para um time deste jogo."));
    }

    @ExceptionHandler(InvalidTeamIdentityException.class)
    ResponseEntity<ApiErrorResponse> invalidTeamIdentity() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_TEAM_IDENTITY",
                        "Informe um nome válido para cada time deste jogo."));
    }

    @ExceptionHandler(TeamImageLockedException.class)
    ResponseEntity<ApiErrorResponse> teamImageLocked() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "TEAM_IMAGE_LOCKED",
                        "As imagens dos times não podem ser alteradas após o encerramento do jogo."));
    }

    @ExceptionHandler(TeamImageStorageNotConfiguredException.class)
    ResponseEntity<ApiErrorResponse> teamImageStorageNotConfigured() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse(
                        "TEAM_IMAGE_STORAGE_NOT_CONFIGURED",
                        "O envio de imagens ainda não está disponível."));
    }

    @ExceptionHandler(TeamImageUploadFailedException.class)
    ResponseEntity<ApiErrorResponse> teamImageUploadFailed() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse(
                        "TEAM_IMAGE_UPLOAD_FAILED",
                        "Não foi possível enviar a imagem agora. Tente novamente."));
    }

    @ExceptionHandler(MatchNotFoundException.class)
    ResponseEntity<ApiErrorResponse> matchNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("MATCH_NOT_FOUND", "Jogo não encontrado."));
    }

    @ExceptionHandler(MatchSeriesNotFoundException.class)
    ResponseEntity<ApiErrorResponse> seriesNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("MATCH_SERIES_NOT_FOUND", "Sequência semanal não encontrada."));
    }

    @ExceptionHandler(GroupNotFoundException.class)
    ResponseEntity<ApiErrorResponse> groupNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("GROUP_NOT_FOUND", "Grupo não encontrado."));
    }

    @ExceptionHandler(GroupAccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> accessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse("GROUP_ACCESS_DENIED", "Você não tem permissão para realizar esta ação."));
    }

    @ExceptionHandler(GroupUserNotFoundException.class)
    ResponseEntity<ApiErrorResponse> invalidSession() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse("INVALID_SESSION", "Sessão inválida."));
    }

    @ExceptionHandler(MatchMustBeInFutureException.class)
    ResponseEntity<ApiErrorResponse> matchMustBeInFuture() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("MATCH_MUST_BE_IN_FUTURE", "Escolha uma data e um horário futuros."));
    }

    @ExceptionHandler(InvalidTimeZoneException.class)
    ResponseEntity<ApiErrorResponse> invalidTimeZone() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("INVALID_TIME_ZONE", "O fuso horário informado não é válido."));
    }

    @ExceptionHandler(InvalidPaymentConfigurationException.class)
    ResponseEntity<ApiErrorResponse> invalidPaymentConfiguration() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_PAYMENT_CONFIGURATION",
                        "Informe um valor e uma chave PIX válidos para este jogo."));
    }

    @ExceptionHandler(InvalidRentalGoalkeeperNameException.class)
    ResponseEntity<ApiErrorResponse> invalidRentalGoalkeeperName() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_RENTAL_GOALKEEPER_NAME",
                        "Informe o nome do goleiro de aluguel."));
    }

    @ExceptionHandler(InvalidMatchDeadlinesException.class)
    ResponseEntity<ApiErrorResponse> invalidMatchDeadlines() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_MATCH_DEADLINES",
                        "Defina prazos futuros, na ordem correta e antes do início do jogo."));
    }

    @ExceptionHandler(InvalidMatchFormatException.class)
    ResponseEntity<ApiErrorResponse> invalidMatchFormat() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_MATCH_FORMAT",
                        "Entre membros exige ao menos 2 times e goleiros em quantidade igual ou maior; contra outro time exige ao menos 1 goleiro e não usa quantidade de times."));
    }

    @ExceptionHandler(InvalidMatchTimingConfigurationException.class)
    ResponseEntity<ApiErrorResponse> invalidMatchTimingConfiguration() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_MATCH_TIMING_CONFIGURATION",
                        "Ative entre 1 e 4 tempos com duração válida. Prorrogação e pênaltis exigem exatamente dois times."));
    }

    @ExceptionHandler(InvalidMinimumPlayersException.class)
    ResponseEntity<ApiErrorResponse> invalidMinimumPlayers() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_MINIMUM_PLAYERS",
                        "A quantidade mínima deve ser maior que zero e não pode ultrapassar o limite de jogadores."));
    }

    @ExceptionHandler(InvalidTechnicalRatingException.class)
    ResponseEntity<ApiErrorResponse> invalidTechnicalRating() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_TECHNICAL_RATING",
                        "Cada habilidade avaliada deve ter um valor inteiro de 1 a 10."));
    }

    @ExceptionHandler(InvalidGuestException.class)
    ResponseEntity<ApiErrorResponse> invalidGuest() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_GUEST",
                        "Informe nome, posição principal e uma posição secundária diferente."));
    }

    @ExceptionHandler(GuestNotFoundException.class)
    ResponseEntity<ApiErrorResponse> guestNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("GUEST_NOT_FOUND", "Convidado não encontrado neste jogo."));
    }

    @ExceptionHandler(InternalMatchRequiredException.class)
    ResponseEntity<ApiErrorResponse> internalMatchRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "INTERNAL_MATCH_REQUIRED",
                        "A formação automática de times está disponível para jogos entre membros."));
    }

    @ExceptionHandler(MinimumPlayersNotReachedException.class)
    ResponseEntity<ApiErrorResponse> minimumPlayersNotReached(MinimumPlayersNotReachedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "MINIMUM_PLAYERS_NOT_REACHED",
                        "Ainda faltam " + exception.getMissingPlayers()
                                + " jogador(es) para formar os times."));
    }

    @ExceptionHandler(GoalkeepersNotReadyException.class)
    ResponseEntity<ApiErrorResponse> goalkeepersNotReady(GoalkeepersNotReadyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "GOALKEEPERS_NOT_READY",
                        "Ainda faltam " + exception.getMissingGoalkeepers()
                                + " goleiro(s) para formar os times."));
    }

    @ExceptionHandler(InvalidTeamAssignmentException.class)
    ResponseEntity<ApiErrorResponse> invalidTeamAssignment() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_TEAM_ASSIGNMENT",
                        "Informe um time e uma função válidos para a modalidade do jogo."));
    }

    @ExceptionHandler(TeamAssignmentNotFoundException.class)
    ResponseEntity<ApiErrorResponse> teamAssignmentNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        "TEAM_ASSIGNMENT_NOT_FOUND",
                        "Escalação não encontrada neste jogo."));
    }

    @ExceptionHandler(IneligibleGoalkeeperException.class)
    ResponseEntity<ApiErrorResponse> ineligibleGoalkeeper() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "INELIGIBLE_GOALKEEPER",
                        "Este participante não pode ser escalado automaticamente como goleiro."));
    }

    @ExceptionHandler(MultipleActiveGoalkeepersException.class)
    ResponseEntity<ApiErrorResponse> multipleActiveGoalkeepers() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "MULTIPLE_ACTIVE_GOALKEEPERS",
                        "Cada time pode ter apenas um goleiro em campo. Coloque o goleiro extra na reserva."));
    }

    @ExceptionHandler(AttendanceClosedException.class)
    ResponseEntity<ApiErrorResponse> attendanceClosed() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "ATTENDANCE_CLOSED",
                        "A confirmação de presença ainda não abriu ou este jogo já começou."));
    }

    @ExceptionHandler(SignupDeadlinePassedException.class)
    ResponseEntity<ApiErrorResponse> signupDeadlinePassed() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "SIGNUP_DEADLINE_PASSED",
                        "O prazo para entrar na lista terminou."));
    }

    @ExceptionHandler(PaymentDeadlinePassedException.class)
    ResponseEntity<ApiErrorResponse> paymentDeadlinePassed() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "PAYMENT_DEADLINE_PASSED",
                        "O prazo para informar o pagamento terminou."));
    }

    @ExceptionHandler(AdministratorReentryRequiredException.class)
    ResponseEntity<ApiErrorResponse> administratorReentryRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "ADMINISTRATOR_REENTRY_REQUIRED",
                        "Depois de sair com pagamento registrado, somente um administrador pode colocar você novamente na lista."));
    }

    @ExceptionHandler(ReplacementVacancyNotOpenException.class)
    ResponseEntity<ApiErrorResponse> replacementVacancyNotOpen() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "REPLACEMENT_VACANCY_NOT_OPEN",
                        "Esta saída não possui uma vaga aguardando reposição."));
    }

    @ExceptionHandler(ReplacementPlayerUnavailableException.class)
    ResponseEntity<ApiErrorResponse> replacementPlayerUnavailable() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "REPLACEMENT_PLAYER_UNAVAILABLE",
                        "Escolha um membro que ainda não esteja confirmado nem aguardando outro acerto neste jogo."));
    }

    @ExceptionHandler(ReplacementRequiredForSettlementException.class)
    ResponseEntity<ApiErrorResponse> replacementRequiredForSettlement() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "REPLACEMENT_REQUIRED_FOR_SETTLEMENT",
                        "O acerto ficará bloqueado até um administrador preencher a vaga deste jogador."));
    }

    @ExceptionHandler(MatchCancelledException.class)
    ResponseEntity<ApiErrorResponse> matchCancelled() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("MATCH_CANCELLED", "Este jogo foi cancelado."));
    }

    @ExceptionHandler(MatchFullException.class)
    ResponseEntity<ApiErrorResponse> matchFull() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("MATCH_FULL", "Todas as vagas deste jogo já foram preenchidas."));
    }

    @ExceptionHandler(MatchAlreadyStartedException.class)
    ResponseEntity<ApiErrorResponse> matchAlreadyStarted() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("MATCH_ALREADY_STARTED", "Não é possível cancelar um jogo que já começou."));
    }

    @ExceptionHandler(GoalkeeperRequiresAttendanceException.class)
    ResponseEntity<ApiErrorResponse> goalkeeperRequiresAttendance() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "GOALKEEPER_REQUIRES_ATTENDANCE",
                        "Somente um jogador confirmado pode ser definido como goleiro deste jogo."));
    }

    @ExceptionHandler(GoalkeeperCandidateRequiredException.class)
    ResponseEntity<ApiErrorResponse> goalkeeperCandidateRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "GOALKEEPER_CANDIDATE_REQUIRED",
                        "Escolha um jogador confirmado que tenha goleiro como posição ou que aceite jogar no gol."));
    }

    @ExceptionHandler(PrimaryGoalkeeperCannotBeUnassignedException.class)
    ResponseEntity<ApiErrorResponse> primaryGoalkeeperCannotBeUnassigned() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "PRIMARY_GOALKEEPER_CANNOT_BE_UNASSIGNED",
                        "Um jogador confirmado com goleiro como posição principal permanece goleiro neste jogo."));
    }

    @ExceptionHandler(GoalkeeperPaymentExemptException.class)
    ResponseEntity<ApiErrorResponse> goalkeeperPaymentExempt() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "GOALKEEPER_PAYMENT_EXEMPT",
                        "Este goleiro está isento do pagamento neste jogo."));
    }

    @ExceptionHandler(GoalkeeperPaymentAlreadyRecordedException.class)
    ResponseEntity<ApiErrorResponse> goalkeeperPaymentAlreadyRecorded() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "GOALKEEPER_PAYMENT_ALREADY_RECORDED",
                        "Não é possível isentar o goleiro porque já existe pagamento em dinheiro informado ou confirmado."));
    }

    @ExceptionHandler(RentalGoalkeeperNotFoundException.class)
    ResponseEntity<ApiErrorResponse> rentalGoalkeeperNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        "RENTAL_GOALKEEPER_NOT_FOUND",
                        "Goleiro de aluguel não encontrado neste jogo."));
    }

    @ExceptionHandler(PaymentNotRequiredException.class)
    ResponseEntity<ApiErrorResponse> paymentNotRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "PAYMENT_NOT_REQUIRED",
                        "Este jogo não possui cobrança configurada."));
    }

    @ExceptionHandler(PaymentRequiresAttendanceException.class)
    ResponseEntity<ApiErrorResponse> paymentRequiresAttendance() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "PAYMENT_REQUIRES_ATTENDANCE",
                        "Confirme que vai jogar antes de informar ou validar o pagamento."));
    }

    @ExceptionHandler(PaymentSettlementNotOpenException.class)
    ResponseEntity<ApiErrorResponse> paymentSettlementNotOpen() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "PAYMENT_SETTLEMENT_NOT_OPEN",
                        "Este pagamento não possui um acerto pendente."));
    }

    @ExceptionHandler(InvalidPaymentSettlementResolutionException.class)
    ResponseEntity<ApiErrorResponse> invalidPaymentSettlementResolution() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "INVALID_PAYMENT_SETTLEMENT_RESOLUTION",
                        "Um pagamento já confirmado não pode ser marcado como não recebido."));
    }
}
