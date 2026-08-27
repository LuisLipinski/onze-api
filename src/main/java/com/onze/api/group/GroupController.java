package com.onze.api.group;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.onze.api.group.GroupInviteModels.InviteResponse;
import com.onze.api.group.GroupInviteModels.JoinGroupRequest;
import com.onze.api.group.GroupInviteModels.JoinGroupResponse;
import com.onze.api.group.GroupModels.CreateGroupRequest;
import com.onze.api.group.GroupModels.GroupResponse;
import com.onze.api.group.GroupModels.UpdateGroupDetailsRequest;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final GroupInviteService groupInviteService;

    public GroupController(GroupService groupService, GroupInviteService groupInviteService) {
        this.groupService = groupService;
        this.groupInviteService = groupInviteService;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateGroupRequest request) {
        GroupResponse response = groupService.create(authentication.getName(), request);
        return ResponseEntity.created(URI.create("/api/groups/" + response.id())).body(response);
    }

    @PostMapping(value = "/{groupId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GroupResponse uploadPhoto(
            Authentication authentication,
            @PathVariable UUID groupId,
            @RequestPart("photo") MultipartFile photo) {
        return groupService.updatePhoto(authentication.getName(), groupId, photo);
    }

    @PostMapping("/{groupId}/invite")
    public InviteResponse createInvite(
            Authentication authentication,
            @PathVariable UUID groupId) {
        return groupInviteService.getOrCreate(authentication.getName(), groupId);
    }

    @PostMapping("/{groupId}/invite/regenerate")
    public InviteResponse regenerateInvite(
            Authentication authentication,
            @PathVariable UUID groupId) {
        return groupInviteService.regenerate(authentication.getName(), groupId);
    }

    @PostMapping("/join")
    public JoinGroupResponse join(
            Authentication authentication,
            @Valid @RequestBody JoinGroupRequest request) {
        return groupInviteService.join(authentication.getName(), request.code());
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
