package com.onze.api.group;

import java.util.List;
import java.util.UUID;

import com.onze.api.group.GroupModels.SportsProfileResponse;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupSportsProfileService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    public GroupSportsProfileService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public SportsProfileResponse getOwn(String authenticatedUserId, UUID groupId) {
        return toResponse(requireOwnMembership(authenticatedUserId, groupId), false);
    }

    @Transactional
    public SportsProfileResponse updateOwn(
            String authenticatedUserId,
            UUID groupId,
            PlayerPosition primaryPosition,
            PlayerPosition secondaryPosition,
            List<PlayerPosition> legacyPositions,
            boolean canPlayGoalkeeper,
            DominantFoot dominantFoot) {
        PositionSelection selection = resolvePositions(
                primaryPosition,
                secondaryPosition,
                legacyPositions);
        GroupMember membership = requireOwnMembership(authenticatedUserId, groupId);
        membership.updateOwnSportsProfile(
                selection.primaryPosition(),
                selection.secondaryPosition(),
                canPlayGoalkeeper,
                dominantFoot);
        return toResponse(membership, false);
    }

    @Transactional(readOnly = true)
    public SportsProfileResponse getMember(String authenticatedUserId, UUID groupId, UUID memberId) {
        requireProfilePermission(authenticatedUserId, groupId);
        return toResponse(requireMember(groupId, memberId), true);
    }

    @Transactional
    public SportsProfileResponse updateMember(
            String authenticatedUserId,
            UUID groupId,
            UUID memberId,
            PlayerPosition primaryPosition,
            PlayerPosition secondaryPosition,
            List<PlayerPosition> legacyPositions,
            boolean canPlayGoalkeeper,
            DominantFoot dominantFoot,
            Integer technicalLevel) {
        requireProfilePermission(authenticatedUserId, groupId);
        PositionSelection selection = resolvePositions(
                primaryPosition,
                secondaryPosition,
                legacyPositions);
        GroupMember member = requireMember(groupId, memberId);
        member.updateSportsProfile(
                selection.primaryPosition(),
                selection.secondaryPosition(),
                canPlayGoalkeeper,
                dominantFoot,
                technicalLevel);
        return toResponse(member, true);
    }

    private GroupMember requireOwnMembership(String authenticatedUserId, UUID groupId) {
        UUID userId = parseUserId(authenticatedUserId);
        requireGroup(groupId);
        return groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupService.GroupAccessDeniedException::new);
    }

    private void requireProfilePermission(String authenticatedUserId, UUID groupId) {
        GroupMember actor = requireOwnMembership(authenticatedUserId, groupId);
        if (!actor.hasPermission(GroupAdminPermission.EDIT_PLAYER_PROFILES)) {
            throw new GroupService.GroupAccessDeniedException();
        }
    }

    private GroupMember requireMember(UUID groupId, UUID memberId) {
        GroupMember member = groupMemberRepository.findById(memberId)
                .orElseThrow(GroupAdminService.GroupMemberNotFoundException::new);
        if (!member.getGroupId().equals(groupId)) {
            throw new GroupAdminService.GroupMemberNotFoundException();
        }
        return member;
    }

    private void requireGroup(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupService.GroupNotFoundException();
        }
    }

    private SportsProfileResponse toResponse(GroupMember member, boolean technicalDetailsVisible) {
        User user = userRepository.findById(member.getUserId())
                .orElseThrow(GroupAdminService.GroupMemberNotFoundException::new);
        return new SportsProfileResponse(
                member.getId(),
                member.getUserId(),
                user.getDisplayName(),
                member.getPrimaryPosition(),
                member.getSecondaryPosition(),
                member.getPositions(),
                member.canPlayGoalkeeper(),
                member.getDominantFoot(),
                technicalDetailsVisible ? member.getTechnicalLevel() : null,
                member.isSportsProfileComplete());
    }

    private PositionSelection resolvePositions(
            PlayerPosition primaryPosition,
            PlayerPosition secondaryPosition,
            List<PlayerPosition> legacyPositions) {
        PlayerPosition resolvedPrimary = primaryPosition;
        PlayerPosition resolvedSecondary = secondaryPosition;

        if (resolvedPrimary == null && legacyPositions != null && !legacyPositions.isEmpty()) {
            resolvedPrimary = legacyPositions.get(0);
            resolvedSecondary = legacyPositions.size() > 1 ? legacyPositions.get(1) : null;
        }
        if (resolvedPrimary == null
                || (resolvedSecondary != null && resolvedSecondary == resolvedPrimary)) {
            throw new InvalidSportsProfileException();
        }
        return new PositionSelection(resolvedPrimary, resolvedSecondary);
    }

    private UUID parseUserId(String authenticatedUserId) {
        try {
            return UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupService.GroupUserNotFoundException();
        }
    }

    public static final class InvalidSportsProfileException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private record PositionSelection(
            PlayerPosition primaryPosition,
            PlayerPosition secondaryPosition) {
    }
}
