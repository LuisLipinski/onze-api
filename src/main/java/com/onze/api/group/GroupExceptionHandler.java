package com.onze.api.group;

import com.onze.api.group.GroupModels.ErrorResponse;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupNotFoundException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;

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
}
