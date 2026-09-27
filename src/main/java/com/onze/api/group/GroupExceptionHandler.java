package com.onze.api.group;

import com.onze.api.group.GroupAdminService.AdminRoleRequiredException;
import com.onze.api.group.GroupAdminService.GroupMemberNotFoundException;
import com.onze.api.group.GroupAdminService.MemberRoleRequiredException;
import com.onze.api.group.GroupAdminService.PrimaryAdminRequiredException;
import com.onze.api.group.GroupAdminService.PrimaryAdminTransferRequiredException;
import com.onze.api.group.GroupAdminService.ReplacementMustBeAdminException;
import com.onze.api.group.GroupInviteService.InvalidGroupInviteException;
import com.onze.api.group.GroupSportsProfileService.InvalidSportsProfileException;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupNotFoundException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.group.GroupService.InvalidGroupPhotoException;
import com.onze.api.group.GroupService.InvalidPaymentConfigurationException;
import com.onze.api.group.GroupService.PhotoStorageNotConfiguredException;
import com.onze.api.group.GroupService.PhotoUploadFailedException;
import com.onze.api.technical.TechnicalRatings.InvalidTechnicalRatingException;
import com.onze.api.web.ApiErrorResponse;
import com.onze.api.statistics.GroupStatisticsService.StatisticsPlayerNotFoundException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = GroupController.class)
public class GroupExceptionHandler {

    @ExceptionHandler(GroupNotFoundException.class)
    ResponseEntity<ApiErrorResponse> groupNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("GROUP_NOT_FOUND", "Grupo não encontrado."));
    }

    @ExceptionHandler(GroupMemberNotFoundException.class)
    ResponseEntity<ApiErrorResponse> memberNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("GROUP_MEMBER_NOT_FOUND", "Jogador não encontrado neste grupo."));
    }

    @ExceptionHandler(StatisticsPlayerNotFoundException.class)
    ResponseEntity<ApiErrorResponse> statisticsPlayerNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(
                        "STATISTICS_PLAYER_NOT_FOUND",
                        "Este jogador não possui histórico neste grupo."));
    }

    @ExceptionHandler(GroupAccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> accessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse("GROUP_ACCESS_DENIED", "Você não tem permissão para alterar este grupo."));
    }

    @ExceptionHandler(PrimaryAdminRequiredException.class)
    ResponseEntity<ApiErrorResponse> primaryAdminRequired() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(
                        "PRIMARY_ADMIN_REQUIRED",
                        "Somente o administrador principal pode realizar esta ação."));
    }

    @ExceptionHandler(PrimaryAdminTransferRequiredException.class)
    ResponseEntity<ApiErrorResponse> primaryTransferRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "PRIMARY_ADMIN_TRANSFER_REQUIRED",
                        "Escolha outro administrador principal antes de deixar o cargo."));
    }

    @ExceptionHandler(ReplacementMustBeAdminException.class)
    ResponseEntity<ApiErrorResponse> replacementMustBeAdmin() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "REPLACEMENT_MUST_BE_ADMIN",
                        "O novo administrador principal precisa já ser administrador do grupo."));
    }

    @ExceptionHandler(AdminRoleRequiredException.class)
    ResponseEntity<ApiErrorResponse> adminRoleRequired() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "ADMIN_ROLE_REQUIRED",
                        "As permissões só podem ser editadas para um administrador comum."));
    }

    @ExceptionHandler(MemberRoleRequiredException.class)
    ResponseEntity<ApiErrorResponse> memberRoleRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "MEMBER_ROLE_REQUIRED",
                        "Rebaixe o administrador para membro antes de removê-lo do grupo."));
    }

    @ExceptionHandler(InvalidSportsProfileException.class)
    ResponseEntity<ApiErrorResponse> invalidSportsProfile() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_SPORTS_PROFILE",
                        "Escolha uma posição principal e, se informar a secundária, use uma posição diferente."));
    }

    @ExceptionHandler(InvalidTechnicalRatingException.class)
    ResponseEntity<ApiErrorResponse> invalidTechnicalRating() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_TECHNICAL_RATING",
                        "Cada habilidade avaliada deve ter um valor inteiro de 1 a 10."));
    }

    @ExceptionHandler(GroupUserNotFoundException.class)
    ResponseEntity<ApiErrorResponse> userNotFound() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse("INVALID_SESSION", "Sessão inválida."));
    }

    @ExceptionHandler(InvalidGroupInviteException.class)
    ResponseEntity<ApiErrorResponse> invalidInvite() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_GROUP_INVITE",
                        "Este código de convite não é válido."));
    }

    @ExceptionHandler(InvalidGroupPhotoException.class)
    ResponseEntity<ApiErrorResponse> invalidPhoto() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_GROUP_PHOTO",
                        "Escolha uma imagem válida de até 5 MB."));
    }

    @ExceptionHandler(InvalidPaymentConfigurationException.class)
    ResponseEntity<ApiErrorResponse> invalidPaymentConfiguration() {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        "INVALID_PAYMENT_CONFIGURATION",
                        "Informe o valor e a chave PIX juntos, ou deixe os dois campos vazios."));
    }

    @ExceptionHandler(PhotoStorageNotConfiguredException.class)
    ResponseEntity<ApiErrorResponse> photoStorageNotConfigured() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse(
                        "PHOTO_STORAGE_NOT_CONFIGURED",
                        "O envio de fotos ainda não está disponível."));
    }

    @ExceptionHandler(PhotoUploadFailedException.class)
    ResponseEntity<ApiErrorResponse> photoUploadFailed() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse(
                        "PHOTO_UPLOAD_FAILED",
                        "Não foi possível enviar a foto agora. Tente novamente."));
    }
}
