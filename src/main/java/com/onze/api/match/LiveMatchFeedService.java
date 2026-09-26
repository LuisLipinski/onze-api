package com.onze.api.match;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
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
    private final MatchTeamImageRepository teamImageRepository;

    public LiveMatchFeedService(
            FootballMatchRepository matchRepository,
            GroupMemberRepository memberRepository,
            GroupRepository groupRepository,
            LiveMatchScoreRepository scoreRepository,
            MatchTeamImageRepository teamImageRepository) {
        this.matchRepository = matchRepository;
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
        this.scoreRepository = scoreRepository;
        this.teamImageRepository = teamImageRepository;
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
        Map<UUID, Map<Integer, MatchTeamImage>> identitiesByMatch = teamImageRepository
                .findAllByMatchIdInOrderByMatchIdAscTeamNumberAsc(matchIds)
                .stream()
                .collect(Collectors.groupingBy(
                        MatchTeamImage::getMatchId,
                        Collectors.toMap(MatchTeamImage::getTeamNumber, item -> item)));
        Map<UUID, List<LiveScoreSideResponse>> scoresByMatch = scoreRepository
                .findAllByMatchIdInOrderByMatchIdAscSideNumberAsc(matchIds).stream()
                .collect(Collectors.groupingBy(
                        LiveMatchScore::getMatchId,
                        Collectors.mapping(
                                score -> {
                                    MatchTeamImage identity = identitiesByMatch
                                            .getOrDefault(score.getMatchId(), Map.of())
                                            .get(score.getSideNumber());
                                    return new LiveScoreSideResponse(
                                            score.getSideNumber(),
                                            score.getScore(),
                                            identity == null
                                                    ? "Time " + score.getSideNumber()
                                                    : identity.getTeamName(),
                                            identity == null ? null : identity.getImageUrl());
                                },
                                Collectors.toList())));

        return matches.stream()
                .filter(match -> groupsById.containsKey(match.getGroupId()))
                .sorted(Comparator.comparing(FootballMatch::getStartedAt))
                .map(match -> {
                    GroupMember membership = membershipsByGroup.get(match.getGroupId());
                    Group group = groupsById.get(match.getGroupId());
                    return summary(match, group,
                            scoresByMatch.getOrDefault(match.getId(), List.of()),
                            membership.hasPermission(GroupAdminPermission.SCHEDULE_GAMES));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<LiveMatchSummaryResponse> getSummary(UUID matchId) {
        return matchRepository.findById(matchId).flatMap(match -> groupRepository
                .findById(match.getGroupId())
                .map(group -> {
                    Map<Integer, MatchTeamImage> identitiesByTeam = teamImageRepository
                            .findAllByMatchIdOrderByTeamNumberAsc(matchId)
                            .stream()
                            .collect(Collectors.toMap(
                                    MatchTeamImage::getTeamNumber,
                                    item -> item));
                    return summary(
                            match,
                            group,
                            scoreRepository.findAllByMatchIdOrderBySideNumberAsc(matchId).stream()
                                    .map(score -> {
                                        MatchTeamImage identity = identitiesByTeam.get(score.getSideNumber());
                                        return new LiveScoreSideResponse(
                                                score.getSideNumber(),
                                                score.getScore(),
                                                identity == null
                                                        ? "Time " + score.getSideNumber()
                                                        : identity.getTeamName(),
                                                identity == null ? null : identity.getImageUrl());
                                    })
                                    .toList(),
                            false);
                }));
    }

    private LiveMatchSummaryResponse summary(
            FootballMatch match,
            Group group,
            List<LiveScoreSideResponse> scores,
            boolean canManage) {
        return new LiveMatchSummaryResponse(
                match.getId(),
                match.getGroupId(),
                group.getName(),
                match.getStartsAt(),
                match.getTimeZone(),
                match.getVenue(),
                match.getStatus(),
                match.getStartedAt(),
                match.getMatchType(),
                match.getTeamCount(),
                match.getLiveVersion(),
                scores,
                canManage);
    }

    private UUID parseUserId(String authenticatedUserId) {
        try {
            return UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupUserNotFoundException();
        }
    }
}
