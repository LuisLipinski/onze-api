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
import com.onze.api.group.GroupModels.SportsProfileResponse;
import com.onze.api.group.GroupModels.TransferPrimaryAdminRequest;
import com.onze.api.group.GroupModels.UpdateAdminPermissionsRequest;
import com.onze.api.group.GroupModels.UpdateGroupDetailsRequest;
import com.onze.api.group.GroupModels.UpdateMemberSportsProfileRequest;
import com.onze.api.group.GroupModels.UpdateOwnSportsProfileRequest;

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
    private final GroupSportsProfileService groupSportsProfileService;

    public GroupController(
            GroupService groupService,
            GroupInviteService groupInviteService,
            GroupAdminService groupAdminService,
            GroupSportsProfileService groupSportsProfileService) {
        this.groupService = groupService;
        this.groupInviteService = groupInviteService;
        this.groupAdminService = groupAdminService;
        this.groupSportsProfileService = groupSportsProfileService;
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

    @GetMapping("/{groupId}/members/me/sports-profile")
    public SportsProfileResponse getOwnSportsProfile(
            Authentication authentication,
            @PathVariable UUID groupId) {
        return groupSportsProfileService.getOwn(authentication.getName(), groupId);
    }

    @PutMapping("/{groupId}/members/me/sports-profile")
    public SportsProfileResponse updateOwnSportsProfile(
            Authentication authentication,
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateOwnSportsProfileRequest request) {
        return groupSportsProfileService.updateOwn(
                authentication.getName(),
                groupId,
                request.primaryPosition(),
                request.secondaryPosition(),
                request.positions(),
                request.canPlayGoalkeeper(),
                request.dominantFoot());
    }

    @GetMapping("/{groupId}/members/{memberId}/sports-profile")
    public SportsProfileResponse getMemberSportsProfile(
            Authentication authentication,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId) {
        return groupSportsProfileService.getMember(authentication.getName(), groupId, memberId);
    }

    @PutMapping("/{groupId}/members/{memberId}/sports-profile")
    public SportsProfileResponse updateMemberSportsProfile(
            Authentication authentication,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberSportsProfileRequest request) {
        return groupSportsProfileService.updateMember(
                authentication.getName(),
                groupId,
                memberId,
                request.primaryPosition(),
                request.secondaryPosition(),
                request.positions(),
                request.canPlayGoalkeeper(),
                request.dominantFoot(),
                request.technicalLevel());
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

    @PutMapping("/{groupId}/members/{memberId}/permissions")
    public GroupMemberResponse updateAdminPermissions(
            Authentication authentication,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateAdminPermissionsRequest request) {
        return groupAdminService.updatePermissions(
                authentication.getName(),
                groupId,
                memberId,
                request.permissions());
    }

    @DeleteMapping("/{groupId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(
            Authentication authentication,
            @PathVariable UUID groupId,
            @PathVariable UUID memberId) {
        groupAdminService.removeMember(authentication.getName(), groupId, memberId);
        return ResponseEntity.noContent().build();
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
