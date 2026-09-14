package com.onze.api.group;

import java.util.Set;
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
        return toResponse(requireOwnMembership(authenticatedUserId, groupId));
    }

    @Transactional
    public SportsProfileResponse updateOwn(
            String authenticatedUserId,
            UUID groupId,
            Set<PlayerPosition> positions,
            boolean canPlayGoalkeeper,
            DominantFoot dominantFoot) {
        validatePlayingRole(positions, canPlayGoalkeeper);
        GroupMember membership = requireOwnMembership(authenticatedUserId, groupId);
        membership.updateOwnSportsProfile(positions, canPlayGoalkeeper, dominantFoot);
        return toResponse(membership);
    }

    @Transactional(readOnly = true)
    public SportsProfileResponse getMember(String authenticatedUserId, UUID groupId, UUID memberId) {
        requireProfilePermission(authenticatedUserId, groupId);
        return toResponse(requireMember(groupId, memberId));
    }

    @Transactional
    public SportsProfileResponse updateMember(
            String authenticatedUserId,
            UUID groupId,
            UUID memberId,
            Set<PlayerPosition> positions,
            boolean canPlayGoalkeeper,
            DominantFoot dominantFoot,
            Integer technicalLevel) {
        requireProfilePermission(authenticatedUserId, groupId);
        validatePlayingRole(positions, canPlayGoalkeeper);
        GroupMember member = requireMember(groupId, memberId);
        member.updateSportsProfile(positions, canPlayGoalkeeper, dominantFoot, technicalLevel);
        return toResponse(member);
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

    private SportsProfileResponse toResponse(GroupMember member) {
        User user = userRepository.findById(member.getUserId())
                .orElseThrow(GroupAdminService.GroupMemberNotFoundException::new);
        return new SportsProfileResponse(
                member.getId(),
                member.getUserId(),
                user.getDisplayName(),
                member.getPositions(),
                member.canPlayGoalkeeper(),
                member.getDominantFoot(),
                member.getTechnicalLevel(),
                member.isSportsProfileComplete());
    }

    private void validatePlayingRole(Set<PlayerPosition> positions, boolean canPlayGoalkeeper) {
        if (positions.isEmpty() && !canPlayGoalkeeper) {
            throw new InvalidSportsProfileException();
        }
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
}
