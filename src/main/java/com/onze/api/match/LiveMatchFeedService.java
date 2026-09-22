package com.onze.api.match;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.onze.api.group.Group;
import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRepository;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.match.LiveMatchModels.LiveMatchSummaryResponse;
import com.onze.api.match.LiveMatchModels.LiveScoreSideResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LiveMatchFeedService {
    private final FootballMatchRepository matchRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupRepository groupRepository;
    private final LiveMatchScoreRepository scoreRepository;

    public LiveMatchFeedService(
            FootballMatchRepository matchRepository,
            GroupMemberRepository memberRepository,
            GroupRepository groupRepository,
            LiveMatchScoreRepository scoreRepository) {
        this.matchRepository = matchRepository;
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
        this.scoreRepository = scoreRepository;
    }

    @Transactional(readOnly = true)
    public List<LiveMatchSummaryResponse> list(String authenticatedUserId) {
        UUID userId = parseUserId(authenticatedUserId);
        List<GroupMember> memberships = memberRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
        if (memberships.isEmpty()) return List.of();

        Map<UUID, GroupMember> membershipsByGroup = memberships.stream()
                .collect(Collectors.toMap(GroupMember::getGroupId, Function.identity()));
        List<FootballMatch> matches = matchRepository
                .findAllByGroupIdInAndStatusInOrderByStartsAtAsc(
                        membershipsByGroup.keySet(), List.of(MatchStatus.IN_PROGRESS));
        if (matches.isEmpty()) return List.of();

        List<UUID> matchIds = matches.stream().map(FootballMatch::getId).toList();
        Map<UUID, Group> groupsById = groupRepository.findAllById(membershipsByGroup.keySet()).stream()
                .collect(Collectors.toMap(Group::getId, Function.identity()));
        Map<UUID, List<LiveScoreSideResponse>> scoresByMatch = scoreRepository
                .findAllByMatchIdInOrderByMatchIdAscSideNumberAsc(matchIds).stream()
                .collect(Collectors.groupingBy(
                        LiveMatchScore::getMatchId,
                        Collectors.mapping(
                                score -> new LiveScoreSideResponse(score.getSideNumber(), score.getScore()),
                                Collectors.toList())));

        return matches.stream()
                .filter(match -> groupsById.containsKey(match.getGroupId()))
                .sorted(Comparator.comparing(FootballMatch::getStartedAt))
                .map(match -> {
                    GroupMember membership = membershipsByGroup.get(match.getGroupId());
                    Group group = groupsById.get(match.getGroupId());
                    return new LiveMatchSummaryResponse(
                            match.getId(),
                            match.getGroupId(),
                            group.getName(),
                            match.getStartsAt(),
                            match.getTimeZone(),
                            match.getVenue(),
                            match.getStartedAt(),
                            match.getMatchType(),
                            match.getTeamCount(),
                            match.getLiveVersion(),
                            scoresByMatch.getOrDefault(match.getId(), List.of()),
                            membership.hasPermission(GroupAdminPermission.SCHEDULE_GAMES));
                })
                .toList();
    }

    private UUID parseUserId(String authenticatedUserId) {
        try {
            return UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupUserNotFoundException();
        }
    }
}
