package com.onze.api.match;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupService;
import com.onze.api.match.MatchTeamReserveModels.MatchTeamReservesResponse;
import com.onze.api.match.TeamModels.MatchTeamsResponse;
import com.onze.api.match.TeamModels.TeamAssignmentResponse;
import com.onze.api.match.TeamModels.TeamResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchTeamReserveService {

    private static final Map<String, Integer> FIELD_CAPACITY = Map.ofEntries(
            Map.entry("GOALKEEPER", 1),
            Map.entry("RIGHT_BACK", 1),
            Map.entry("CENTER_DEFENDER", 2),
            Map.entry("LEFT_BACK", 1),
            Map.entry("CENTRAL_MIDFIELDER", 1),
            Map.entry("DEFENSIVE_MIDFIELDER", 1),
            Map.entry("PLAYMAKER", 1),
            Map.entry("RIGHT_WINGER", 1),
            Map.entry("CENTER_FORWARD", 1),
            Map.entry("LEFT_WINGER", 1));

    private static final Map<String, Integer> FUT7_CAPACITY = Map.of(
            "GOALKEEPER", 1,
            "RIGHT_DEFENDER", 1,
            "LEFT_DEFENDER", 1,
            "RIGHT_MIDFIELDER", 1,
            "CENTRAL_MIDFIELDER", 1,
            "LEFT_MIDFIELDER", 1,
            "CENTER_FORWARD", 1);

    private static final Map<String, Integer> FUTSAL_CAPACITY = Map.of(
            "GOALKEEPER", 1,
            "FIXO", 1,
            "RIGHT_WINGER_FUTSAL", 1,
            "LEFT_WINGER_FUTSAL", 1,
            "PIVOT", 1);

    private final MatchTeamService teamService;
    private final FootballMatchRepository matchRepository;
    private final GroupMemberRepository memberRepository;
    private final MatchTeamAssignmentRepository assignmentRepository;
    private final MatchTeamReserveRepository reserveRepository;

    public MatchTeamReserveService(
            MatchTeamService teamService,
            FootballMatchRepository matchRepository,
            GroupMemberRepository memberRepository,
            MatchTeamAssignmentRepository assignmentRepository,
            MatchTeamReserveRepository reserveRepository) {
        this.teamService = teamService;
        this.matchRepository = matchRepository;
        this.memberRepository = memberRepository;
        this.assignmentRepository = assignmentRepository;
        this.reserveRepository = reserveRepository;
    }

    @Transactional(readOnly = true)
    public MatchTeamReservesResponse get(String authenticatedUserId, UUID matchId) {
        FootballMatch match = matchRepository.findById(matchId)
                .orElseThrow(MatchService.MatchNotFoundException::new);
        requireMembership(authenticatedUserId, match.getGroupId());
        return response(matchId);
    }

    @Transactional
    public MatchTeamReservesResponse autoAssign(String authenticatedUserId, UUID matchId) {
        FootballMatch match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchService.MatchNotFoundException::new);
        GroupMember actor = requireMembership(authenticatedUserId, match.getGroupId());
        requireTechnicalPermission(actor);

        MatchTeamsResponse teams = teamService.get(authenticatedUserId, matchId);
        Set<UUID> reserveIds = automaticReserveIds(teams);
        return replace(matchId, reserveIds);
    }

    @Transactional
    public MatchTeamReservesResponse update(
            String authenticatedUserId,
            UUID matchId,
            List<UUID> reserveAssignmentIds) {
        FootballMatch match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchService.MatchNotFoundException::new);
        GroupMember actor = requireMembership(authenticatedUserId, match.getGroupId());
        requireTechnicalPermission(actor);
        return replace(matchId, new LinkedHashSet<>(reserveAssignmentIds));
    }

    static Set<UUID> automaticReserveIds(MatchTeamsResponse teams) {
        Set<UUID> result = new LinkedHashSet<>();
        Map<String, Integer> capacities = capacities(teams.modality());

        for (TeamResponse team : teams.teams()) {
            Map<String, List<TeamAssignmentResponse>> byRole = team.assignments().stream()
                    .collect(Collectors.groupingBy(
                            TeamAssignmentResponse::assignedRole,
                            java.util.LinkedHashMap::new,
                            Collectors.toCollection(ArrayList::new)));

            for (Map.Entry<String, List<TeamAssignmentResponse>> entry : byRole.entrySet()) {
                List<TeamAssignmentResponse> sameRole = entry.getValue();
                sameRole.sort(Comparator
                        .comparingInt((TeamAssignmentResponse assignment) ->
                                assignment.overallUsed() == null ? Integer.MIN_VALUE : assignment.overallUsed())
                        .reversed()
                        .thenComparing(TeamAssignmentResponse::displayName)
                        .thenComparing(TeamAssignmentResponse::id));

                int fieldCapacity = capacities.getOrDefault(entry.getKey(), 1);
                for (int index = fieldCapacity; index < sameRole.size(); index++) {
                    result.add(sameRole.get(index).id());
                }
            }
        }
        return result;
    }

    private MatchTeamReservesResponse replace(UUID matchId, Set<UUID> reserveAssignmentIds) {
        List<MatchTeamAssignment> assignments = assignmentRepository
                .findAllByMatchIdOrderByTeamNumberAscCreatedAtAsc(matchId);
        Set<UUID> validAssignmentIds = assignments.stream()
                .map(MatchTeamAssignment::getId)
                .collect(Collectors.toSet());
        if (!validAssignmentIds.containsAll(reserveAssignmentIds)) {
            throw new MatchTeamService.InvalidTeamAssignmentException();
        }

        reserveRepository.deleteAllByMatchId(matchId);
        if (!reserveAssignmentIds.isEmpty()) {
            reserveRepository.saveAll(reserveAssignmentIds.stream()
                    .map(assignmentId -> new MatchTeamReserve(assignmentId, matchId))
                    .toList());
        }
        return response(matchId);
    }

    private MatchTeamReservesResponse response(UUID matchId) {
        List<UUID> reserveIds = reserveRepository.findAllByMatchIdOrderByCreatedAtAsc(matchId).stream()
                .map(MatchTeamReserve::getAssignmentId)
                .toList();
        return new MatchTeamReservesResponse(matchId, reserveIds);
    }

    private static Map<String, Integer> capacities(MatchModality modality) {
        return switch (modality) {
            case FIELD -> FIELD_CAPACITY;
            case FUT7 -> FUT7_CAPACITY;
            case FUTSAL -> FUTSAL_CAPACITY;
        };
    }

    private GroupMember requireMembership(String authenticatedUserId, UUID groupId) {
        UUID userId;
        try {
            userId = UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupService.GroupUserNotFoundException();
        }
        return memberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupService.GroupAccessDeniedException::new);
    }

    private void requireTechnicalPermission(GroupMember actor) {
        if (!actor.hasPermission(GroupAdminPermission.EDIT_PLAYER_PROFILES)) {
            throw new GroupService.GroupAccessDeniedException();
        }
    }
}
