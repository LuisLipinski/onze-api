package com.onze.api.group;

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

    @ExceptionHandler(GroupAccessDeniedException.class)
    ResponseEntity<ErrorResponse> accessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("GROUP_ACCESS_DENIED", "Você não tem permissão para alterar este grupo."));
    }

    @ExceptionHandler(GroupUserNotFoundException.class)
    ResponseEntity<ErrorResponse> userNotFound() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("INVALID_SESSION", "Sessão inválida."));
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
