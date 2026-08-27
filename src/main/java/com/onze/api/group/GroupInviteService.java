package com.onze.api.group;

import java.security.SecureRandom;
import java.util.UUID;

import com.onze.api.group.GroupInviteModels.InviteResponse;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupNotFoundException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;

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

    public GroupInviteService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupInviteRepository groupInviteRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupInviteRepository = groupInviteRepository;
    }

    @Transactional
    public InviteResponse getOrCreate(String authenticatedUserId, UUID groupId) {
        UUID userId = parseUserId(authenticatedUserId);
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException();
        }

        GroupMember membership = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupAccessDeniedException::new);
        if (membership.getRole() != GroupRole.ADMIN) {
            throw new GroupAccessDeniedException();
        }

        GroupInvite invite = groupInviteRepository.findByGroupId(groupId)
                .orElseGet(() -> groupInviteRepository.save(
                        new GroupInvite(groupId, generateUniqueCode(), userId)));
        return toResponse(invite);
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
        return new InviteResponse(
                invite.getGroupId(),
                invite.getCode(),
                "onze://join/" + invite.getCode());
    }

    private UUID parseUserId(String authenticatedUserId) {
        try {
            return UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupUserNotFoundException();
        }
    }
}
