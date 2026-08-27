package com.onze.api.group;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

import com.onze.api.group.GroupInviteModels.InviteResponse;
import com.onze.api.group.GroupInviteModels.JoinGroupResponse;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupNotFoundException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.user.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupInviteService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final SecureRandom secureRandom = new SecureRandom();
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInviteRepository groupInviteRepository;
    private final UserRepository userRepository;
    private final String publicBaseUrl;

    public GroupInviteService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupInviteRepository groupInviteRepository,
            UserRepository userRepository,
            @Value("${app.public-base-url}") String publicBaseUrl) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupInviteRepository = groupInviteRepository;
        this.userRepository = userRepository;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public InviteResponse getOrCreate(String authenticatedUserId, UUID groupId) {
        UUID userId = requireAdmin(authenticatedUserId, groupId);
        GroupInvite invite = groupInviteRepository.findByGroupId(groupId)
                .orElseGet(() -> groupInviteRepository.save(
                        new GroupInvite(groupId, generateUniqueCode(), userId)));
        return toResponse(invite);
    }

    @Transactional
    public InviteResponse regenerate(String authenticatedUserId, UUID groupId) {
        UUID userId = requireAdmin(authenticatedUserId, groupId);
        GroupInvite invite = groupInviteRepository.findByGroupId(groupId)
                .orElseGet(() -> new GroupInvite(groupId, generateUniqueCode(), userId));

        if (invite.getId() == null) {
            return toResponse(groupInviteRepository.save(invite));
        }

        invite.regenerate(generateUniqueCode(), userId);
        return toResponse(groupInviteRepository.save(invite));
    }

    @Transactional
    public JoinGroupResponse join(String authenticatedUserId, String rawCode) {
        UUID userId = parseUserId(authenticatedUserId);
        if (!userRepository.existsById(userId)) {
            throw new GroupUserNotFoundException();
        }

        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        GroupInvite invite = groupInviteRepository.findByCodeIgnoreCase(code)
                .orElseThrow(InvalidGroupInviteException::new);
        Group group = groupRepository.findById(invite.getGroupId())
                .orElseThrow(InvalidGroupInviteException::new);

        var existingMembership = groupMemberRepository.findByGroupIdAndUserId(group.getId(), userId);
        if (existingMembership.isPresent()) {
            GroupMember membership = existingMembership.get();
            return new JoinGroupResponse(group.getId(), group.getName(), membership.getRole(), true);
        }

        GroupMember membership = groupMemberRepository.save(
                new GroupMember(group.getId(), userId, GroupRole.MEMBER));
        return new JoinGroupResponse(group.getId(), group.getName(), membership.getRole(), false);
    }

    boolean inviteExists(String rawCode) {
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        return groupInviteRepository.findByCodeIgnoreCase(code).isPresent();
    }

    private UUID requireAdmin(String authenticatedUserId, UUID groupId) {
        UUID userId = parseUserId(authenticatedUserId);
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException();
        }

        GroupMember membership = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupAccessDeniedException::new);
        if (membership.getRole() != GroupRole.ADMIN) {
            throw new GroupAccessDeniedException();
        }
        return userId;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int index = 0; index < CODE_LENGTH; index++) {
                code.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
            }
            String value = code.toString();
            if (!groupInviteRepository.existsByCode(value)) {
                return value;
            }
        }
        throw new IllegalStateException("Could not generate a unique group invite code");
    }

    private InviteResponse toResponse(GroupInvite invite) {
        String code = invite.getCode();
        return new InviteResponse(
                invite.getGroupId(),
                code,
                "onze://join/" + code,
                publicBaseUrl + "/join/" + code);
    }

    private UUID parseUserId(String authenticatedUserId) {
        try {
            return UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupUserNotFoundException();
        }
    }

    public static final class InvalidGroupInviteException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
