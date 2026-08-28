package com.onze.api.group;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.onze.api.group.GroupInviteModels.InviteResponse;
import com.onze.api.group.GroupInviteModels.JoinGroupRequest;
import com.onze.api.group.GroupInviteModels.JoinGroupResponse;
import com.onze.api.group.GroupModels.CreateGroupRequest;
import com.onze.api.group.GroupModels.GroupMemberResponse;
import com.onze.api.group.GroupModels.GroupResponse;
import com.onze.api.group.GroupModels.TransferPrimaryAdminRequest;
import com.onze.api.group.GroupModels.UpdateGroupDetailsRequest;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final GroupAdminService groupAdminService;

    public GroupController(
            GroupService groupService,
            GroupInviteService groupInviteService,
            GroupAdminService groupAdminService) {
        this.groupService = groupService;
        this.groupInviteService = groupInviteService;
        this.groupAdminService = groupAdminService;
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

    @GetMapping("/{groupId}/members")
    public List<GroupMemberResponse> listMembers(
            Authentication authentication,
            @PathVariable UUID groupId) {
        return groupAdminService.listMembers(authentication.getName(), groupId);
    }

    @PutMapping("/{groupId}/members/{memberId}/promote")
    public GroupMemberResponse promoteMember(
            Authentication authentication,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId) {
        return groupAdminService.promote(authentication.getName(), groupId, memberId);
    }

    @PutMapping("/{groupId}/members/{memberId}/demote")
    public GroupMemberResponse demoteAdmin(
            Authentication authentication,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId) {
        return groupAdminService.demote(authentication.getName(), groupId, memberId);
    }

    @PutMapping("/{groupId}/primary-admin")
    public List<GroupMemberResponse> transferPrimaryAdmin(
            Authentication authentication,
            @PathVariable UUID groupId,
            @Valid @RequestBody TransferPrimaryAdminRequest request) {
        return groupAdminService.transferPrimaryAndStepDown(
                authentication.getName(),
                groupId,
                request.replacementMemberId());
    }

    @DeleteMapping("/{groupId}/members/me")
    public ResponseEntity<Void> leave(
            Authentication authentication,
            @PathVariable UUID groupId) {
        groupAdminService.leave(authentication.getName(), groupId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<GroupResponse> list(Authentication authentication) {
        return groupService.listForUser(authentication.getName());
    }
}
