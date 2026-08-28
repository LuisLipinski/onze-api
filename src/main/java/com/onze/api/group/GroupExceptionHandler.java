package com.onze.api.group;

import com.onze.api.group.GroupAdminService.AdminRoleRequiredException;
import com.onze.api.group.GroupAdminService.GroupMemberNotFoundException;
import com.onze.api.group.GroupAdminService.MemberRoleRequiredException;
import com.onze.api.group.GroupAdminService.PrimaryAdminRequiredException;
import com.onze.api.group.GroupAdminService.PrimaryAdminTransferRequiredException;
import com.onze.api.group.GroupAdminService.ReplacementMustBeAdminException;
import com.onze.api.group.GroupInviteService.InvalidGroupInviteException;
import com.onze.api.group.GroupModels.ErrorResponse;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupNotFoundException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.group.GroupService.InvalidGroupPhotoException;
import com.onze.api.group.GroupService.PhotoStorageNotConfiguredException;
import com.onze.api.group.GroupService.PhotoUploadFailedException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = GroupController.class)
public class GroupExceptionHandler {

    @ExceptionHandler(GroupNotFoundException.class)
    ResponseEntity<ErrorResponse> groupNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("GROUP_NOT_FOUND", "Grupo não encontrado."));
    }

    @ExceptionHandler(GroupMemberNotFoundException.class)
    ResponseEntity<ErrorResponse> memberNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("GROUP_MEMBER_NOT_FOUND", "Jogador não encontrado neste grupo."));
    }

    @ExceptionHandler(GroupAccessDeniedException.class)
    ResponseEntity<ErrorResponse> accessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("GROUP_ACCESS_DENIED", "Você não tem permissão para alterar este grupo."));
    }

    @ExceptionHandler(PrimaryAdminRequiredException.class)
    ResponseEntity<ErrorResponse> primaryAdminRequired() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(
                        "PRIMARY_ADMIN_REQUIRED",
                        "Somente o administrador principal pode realizar esta ação."));
    }

    @ExceptionHandler(PrimaryAdminTransferRequiredException.class)
    ResponseEntity<ErrorResponse> primaryTransferRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "PRIMARY_ADMIN_TRANSFER_REQUIRED",
                        "Escolha outro administrador principal antes de deixar o cargo."));
    }

    @ExceptionHandler(ReplacementMustBeAdminException.class)
    ResponseEntity<ErrorResponse> replacementMustBeAdmin() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "REPLACEMENT_MUST_BE_ADMIN",
                        "O novo administrador principal precisa já ser administrador do grupo."));
    }

    @ExceptionHandler(AdminRoleRequiredException.class)
    ResponseEntity<ErrorResponse> adminRoleRequired() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "ADMIN_ROLE_REQUIRED",
                        "As permissões só podem ser editadas para um administrador comum."));
    }

    @ExceptionHandler(MemberRoleRequiredException.class)
    ResponseEntity<ErrorResponse> memberRoleRequired() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "MEMBER_ROLE_REQUIRED",
                        "Rebaixe o administrador para membro antes de removê-lo do grupo."));
    }

    @ExceptionHandler(GroupUserNotFoundException.class)
    ResponseEntity<ErrorResponse> userNotFound() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_SESSION", "Sessão inválida."));
    }

    @ExceptionHandler(InvalidGroupInviteException.class)
    ResponseEntity<ErrorResponse> invalidInvite() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_GROUP_INVITE",
                        "Este código de convite não é válido."));
    }

    @ExceptionHandler(InvalidGroupPhotoException.class)
    ResponseEntity<ErrorResponse> invalidPhoto() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_GROUP_PHOTO",
                        "Escolha uma imagem válida de até 5 MB."));
    }

    @ExceptionHandler(PhotoStorageNotConfiguredException.class)
    ResponseEntity<ErrorResponse> photoStorageNotConfigured() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(
                        "PHOTO_STORAGE_NOT_CONFIGURED",
                        "O envio de fotos ainda não está disponível."));
    }

    @ExceptionHandler(PhotoUploadFailedException.class)
    ResponseEntity<ErrorResponse> photoUploadFailed() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse(
                        "PHOTO_UPLOAD_FAILED",
                        "Não foi possível enviar a foto agora. Tente novamente."));
    }
}
