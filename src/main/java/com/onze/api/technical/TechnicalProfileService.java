package com.onze.api.technical;

import java.time.Clock;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupAdminService;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRepository;
import com.onze.api.group.GroupService;
import com.onze.api.group.PlayerPosition;
import com.onze.api.technical.TechnicalProfileModels.OverallResponse;
import com.onze.api.technical.TechnicalProfileModels.PositionOverallResponse;
import com.onze.api.technical.TechnicalProfileModels.TechnicalProfileResponse;
import com.onze.api.technical.TechnicalRatingPolicy.OverallResult;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TechnicalProfileService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupMemberSkillRatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public TechnicalProfileService(
            GroupRepository groupRepository,
            GroupMemberRepository memberRepository,
            GroupMemberSkillRatingRepository ratingRepository,
            UserRepository userRepository,
            Clock clock) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.ratingRepository = ratingRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TechnicalProfileResponse get(
            String authenticatedUserId,
            UUID groupId,
            UUID membershipId) {
        requirePermission(authenticatedUserId, groupId);
        return response(requireMember(groupId, membershipId));
    }

    @Transactional
    public TechnicalProfileResponse replace(
            String authenticatedUserId,
            UUID groupId,
            UUID membershipId,
            Map<PlayerSkill, Integer> requestedRatings) {
        requirePermission(authenticatedUserId, groupId);
        GroupMember member = requireMember(groupId, membershipId);
        Map<PlayerSkill, Integer> normalized = TechnicalRatings.normalize(requestedRatings);

        ratingRepository.deleteAll(ratingRepository.findAllByGroupMemberId(member.getId()));
        ratingRepository.saveAll(normalized.entrySet().stream()
                .map(entry -> new GroupMemberSkillRating(
                        member.getId(), entry.getKey(), entry.getValue()))
                .toList());
        member.markTechnicalProfileUpdated(clock.instant());
        return response(member, normalized);
    }

    public Map<PlayerSkill, Integer> ratingsFor(GroupMember member) {
        return toMap(ratingRepository.findAllByGroupMemberId(member.getId()));
    }

    private TechnicalProfileResponse response(GroupMember member) {
        return response(member, ratingsFor(member));
    }

    private TechnicalProfileResponse response(
            GroupMember member,
            Map<PlayerSkill, Integer> ratings) {
        User user = userRepository.findById(member.getUserId())
                .orElseThrow(GroupAdminService.GroupMemberNotFoundException::new);
        List<PositionOverallResponse> positions = List.of(PlayerPosition.values()).stream()
                .map(position -> positionResponse(position, TechnicalRatingPolicy.position(ratings, position)))
                .toList();
        Map<PlayerPosition, List<PlayerSkill>> important = new LinkedHashMap<>();
        if (member.getPrimaryPosition() != null) {
            important.put(member.getPrimaryPosition(), TechnicalRatingPolicy
                    .essentialSkills(member.getPrimaryPosition()).stream().sorted().toList());
        }
        if (member.getSecondaryPosition() != null) {
            important.put(member.getSecondaryPosition(), TechnicalRatingPolicy
                    .essentialSkills(member.getSecondaryPosition()).stream().sorted().toList());
        }
        return new TechnicalProfileResponse(
                member.getId(),
                member.getUserId(),
                user.getDisplayName(),
                member.getPrimaryPosition(),
                member.getSecondaryPosition(),
                Map.copyOf(ratings),
                overallResponse(TechnicalRatingPolicy.general(ratings)),
                positions,
                Map.copyOf(important),
                member.getTechnicalProfileUpdatedAt());
    }

    private PositionOverallResponse positionResponse(
            PlayerPosition position,
            OverallResult result) {
        return new PositionOverallResponse(
                position,
                result.overall(),
                result.coverage(),
                result.reliable(),
                result.estimated(),
                result.resolvedPosition(),
                result.missingEssentialSkills());
    }

    private OverallResponse overallResponse(OverallResult result) {
        return new OverallResponse(
                result.overall(),
                result.coverage(),
                result.reliable(),
                result.estimated(),
                result.resolvedPosition(),
                result.missingEssentialSkills());
    }

    private Map<PlayerSkill, Integer> toMap(List<GroupMemberSkillRating> ratings) {
        Map<PlayerSkill, Integer> result = new EnumMap<>(PlayerSkill.class);
        ratings.forEach(rating -> result.put(rating.getSkill(), rating.getRating()));
        return result;
    }

    private void requirePermission(String authenticatedUserId, UUID groupId) {
        UUID userId = parseUserId(authenticatedUserId);
        if (!groupRepository.existsById(groupId)) {
            throw new GroupService.GroupNotFoundException();
        }
        GroupMember actor = memberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupService.GroupAccessDeniedException::new);
        if (!actor.hasPermission(GroupAdminPermission.EDIT_PLAYER_PROFILES)) {
            throw new GroupService.GroupAccessDeniedException();
        }
    }

    private GroupMember requireMember(UUID groupId, UUID membershipId) {
        GroupMember member = memberRepository.findById(membershipId)
                .orElseThrow(GroupAdminService.GroupMemberNotFoundException::new);
        if (!member.getGroupId().equals(groupId)) {
            throw new GroupAdminService.GroupMemberNotFoundException();
        }
        return member;
    }

    private UUID parseUserId(String authenticatedUserId) {
        try {
            return UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupService.GroupUserNotFoundException();
        }
    }

}
