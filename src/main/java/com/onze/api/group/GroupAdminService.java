package com.onze.api.group;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.onze.api.group.GroupModels.GroupMemberResponse;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupAdminService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    public GroupAdminService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> listMembers(String authenticatedUserId, UUID groupId) {
        UUID actorUserId = requireAdmin(authenticatedUserId, groupId).getUserId();
        return groupMemberRepository.findAllByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .map(member -> toResponse(member, actorUserId))
                .toList();
    }

    @Transactional
    public GroupMemberResponse promote(String authenticatedUserId, UUID groupId, UUID memberId) {
        UUID actorUserId = requirePermission(
                authenticatedUserId,
                groupId,
                GroupAdminPermission.PROMOTE_MEMBERS).getUserId();
        GroupMember target = requireMember(groupId, memberId);
        if (target.getRole() == GroupRole.MEMBER) {
            target.changeRole(GroupRole.ADMIN);
        }
        return toResponse(target, actorUserId);
    }

    @Transactional
    public GroupMemberResponse demote(String authenticatedUserId, UUID groupId, UUID memberId) {
        GroupMember actor = requirePrimaryAdmin(authenticatedUserId, groupId);
        GroupMember target = requireMember(groupId, memberId);

        if (target.getRole() == GroupRole.PRIMARY_ADMIN) {
            throw new PrimaryAdminTransferRequiredException();
        }
        if (target.getRole() == GroupRole.ADMIN) {
            target.changeRole(GroupRole.MEMBER);
        }
        return toResponse(target, actor.getUserId());
    }

    @Transactional
    public GroupMemberResponse updatePermissions(
            String authenticatedUserId,
            UUID groupId,
            UUID memberId,
            Set<GroupAdminPermission> permissions) {
        GroupMember actor = requirePrimaryAdmin(authenticatedUserId, groupId);
        GroupMember target = requireMember(groupId, memberId);
        if (target.getRole() != GroupRole.ADMIN) {
            throw new AdminRoleRequiredException();
        }

        target.replacePermissions(permissions);
        return toResponse(target, actor.getUserId());
    }

    @Transactional
    public void removeMember(String authenticatedUserId, UUID groupId, UUID memberId) {
        requirePermission(authenticatedUserId, groupId, GroupAdminPermission.REMOVE_MEMBERS);
        GroupMember target = requireMember(groupId, memberId);
        if (target.getRole() != GroupRole.MEMBER) {
            throw new MemberRoleRequiredException();
        }
        groupMemberRepository.delete(target);
    }

    @Transactional
    public List<GroupMemberResponse> transferPrimaryAndStepDown(
            String authenticatedUserId,
            UUID groupId,
            UUID replacementMemberId) {
        GroupMember currentPrimary = requirePrimaryAdmin(authenticatedUserId, groupId);
        GroupMember replacement = requireMember(groupId, replacementMemberId);

        if (replacement.getId().equals(currentPrimary.getId()) || replacement.getRole() != GroupRole.ADMIN) {
            throw new ReplacementMustBeAdminException();
        }

        currentPrimary.changeRole(GroupRole.ADMIN);
        currentPrimary.replacePermissions(Set.of());
        groupMemberRepository.save(currentPrimary);
        groupMemberRepository.flush();

        replacement.changeRole(GroupRole.PRIMARY_ADMIN);
        groupMemberRepository.save(replacement);
        groupMemberRepository.flush();

        UUID actorUserId = currentPrimary.getUserId();
        return groupMemberRepository.findAllByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .map(member -> toResponse(member, actorUserId))
                .toList();
    }

    @Transactional
    public void leave(String authenticatedUserId, UUID groupId) {
        UUID userId = parseUserId(authenticatedUserId);
        requireGroup(groupId);
        GroupMember membership = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupService.GroupAccessDeniedException::new);

        if (membership.getRole() == GroupRole.PRIMARY_ADMIN) {
            throw new PrimaryAdminTransferRequiredException();
        }

        groupMemberRepository.delete(membership);
    }

    private GroupMember requireAdmin(String authenticatedUserId, UUID groupId) {
        UUID userId = parseUserId(authenticatedUserId);
        requireGroup(groupId);
        GroupMember membership = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupService.GroupAccessDeniedException::new);
        if (membership.getRole() == GroupRole.MEMBER) {
            throw new GroupService.GroupAccessDeniedException();
        }
        return membership;
    }

    private GroupMember requirePrimaryAdmin(String authenticatedUserId, UUID groupId) {
        GroupMember membership = requireAdmin(authenticatedUserId, groupId);
        if (membership.getRole() != GroupRole.PRIMARY_ADMIN) {
            throw new PrimaryAdminRequiredException();
        }
        return membership;
    }

    private GroupMember requirePermission(
            String authenticatedUserId,
            UUID groupId,
            GroupAdminPermission permission) {
        GroupMember membership = requireAdmin(authenticatedUserId, groupId);
        if (!membership.hasPermission(permission)) {
            throw new GroupService.GroupAccessDeniedException();
        }
        return membership;
    }

    private GroupMember requireMember(UUID groupId, UUID memberId) {
        GroupMember member = groupMemberRepository.findById(memberId)
                .orElseThrow(GroupMemberNotFoundException::new);
        if (!member.getGroupId().equals(groupId)) {
            throw new GroupMemberNotFoundException();
        }
        return member;
    }

    private void requireGroup(UUID groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupService.GroupNotFoundException();
        }
    }

    private GroupMemberResponse toResponse(GroupMember member, UUID actorUserId) {
        User user = userRepository.findById(member.getUserId())
                .orElseThrow(GroupMemberNotFoundException::new);
        return new GroupMemberResponse(
                member.getId(),
                member.getUserId(),
                user.getDisplayName(),
                member.getRole(),
                member.getPermissions(),
                member.getUserId().equals(actorUserId));
    }

    private UUID parseUserId(String authenticatedUserId) {
        try {
            return UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupService.GroupUserNotFoundException();
        }
    }

    public static final class GroupMemberNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class PrimaryAdminRequiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class PrimaryAdminTransferRequiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class ReplacementMustBeAdminException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class AdminRoleRequiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class MemberRoleRequiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
