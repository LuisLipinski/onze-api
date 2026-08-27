package com.onze.api.group;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.onze.api.group.GroupModels.CreateGroupRequest;
import com.onze.api.group.GroupModels.GroupResponse;
import com.onze.api.group.GroupModels.UpdateGroupDetailsRequest;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateGroupRequest request) {
        GroupResponse response = groupService.create(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/groups/" + response.id())).body(response);
    }

    @PutMapping("/{groupId}/details")
    public GroupResponse updateDetails(
            Authentication authentication,
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupDetailsRequest request) {
        return groupService.updateDetails(authentication.getName(), groupId, request);
    }

    @GetMapping
    public List<GroupResponse> list(Authentication authentication) {
        return groupService.listForUser(authentication.getName());
    }
}
