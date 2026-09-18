package com.onze.api.match;

import java.util.ArrayList;
import java.util.Comparator;
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

    private static final Map<String, Integer> FIELD_ROLE_CAPACITY = Map.ofEntries(
            Map.entry("GOALKEEPER", 1),
            Map.entry("RIGHT_BACK", 1),
            Map.entry("CENTER_DEFENDER", 2),
            Map.entry("LEFT_BACK", 1),
            Map.entry("DEFENSIVE_MIDFIELDER", 1),
            Map.entry("CENTRAL_MIDFIELDER", 1),
            Map.entry("PLAYMAKER", 1),
            Map.entry("RIGHT_WINGER", 1),
            Map.entry("CENTER_FORWARD", 1),
            Map.entry("LEFT_WINGER", 1));

    private static final Map<String, Integer> FUT7_ROLE_CAPACITY = Map.of(
            "GOALKEEPER", 1,
            "RIGHT_DEFENDER", 1,
            "LEFT_DEFENDER", 1,
            "RIGHT_MIDFIELDER", 1,
            "CENTRAL_MIDFIELDER", 1,
            "LEFT_MIDFIELDER", 1,
            "CENTER_FORWARD", 1);

    private static final Map<String, Integer> FUTSAL_ROLE_CAPACITY = Map.of(
            "GOALKEEPER", 1,
            "FIXO", 1,
            "RIGHT_WINGER_FUTSAL", 1,
            "LEFT_WINGER_FUTSAL", 1,
            "PIVOT", 1);

    private static final Map<String, Integer> FIELD_SECTOR_CAPACITY = Map.of(
            "GOALKEEPER", 1,
            "DEFENSE", 4,
            "MIDFIELD", 3,
            "ATTACK", 3);

    private static final Map<String, Integer> FUT7_SECTOR_CAPACITY = Map.of(
            "GOALKEEPER", 1,
            "DEFENSE", 2,
            "MIDFIELD", 3,
            "ATTACK", 1);

    private static final Map<String, Integer> FUTSAL_SECTOR_CAPACITY = Map.of(
            "GOALKEEPER", 1,
            "DEFENSE", 1,
            "MIDFIELD", 2,
            "ATTACK", 1);

    private static final Set<String> DEFENSE_ROLES = Set.of(
            "DEFENDER", "RIGHT_DEFENDER", "LEFT_DEFENDER", "CENTER_DEFENDER",
            "RIGHT_BACK", "LEFT_BACK", "FIXO");
    private static final Set<String> MIDFIELD_ROLES = Set.of(
            "DEFENSIVE_MIDFIELDER", "MIDFIELDER", "RIGHT_MIDFIELDER",
            "LEFT_MIDFIELDER", "CENTRAL_MIDFIELDER", "PLAYMAKER",
            "RIGHT_WINGER_FUTSAL", "LEFT_WINGER_FUTSAL");
    private static final Set<String> ATTACK_ROLES = Set.of(
            "ATTACKER", "RIGHT_WINGER", "LEFT_WINGER", "CENTER_FORWARD", "PIVOT");

    private static final Comparator<TeamAssignmentResponse> WEAKEST_FIRST = Comparator
            .comparingInt((TeamAssignmentResponse assignment) ->
                    assignment.overallUsed() == null ? Integer.MIN_VALUE : assignment.overallUsed())
            .thenComparing(TeamAssignmentResponse::displayName)
            .thenComparing(TeamAssignmentResponse::id);

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
        return replace(matchId, automaticReserveIds(teams));
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
        int capacity = fieldCapacity(teams.modality());

        for (TeamResponse team : teams.teams()) {
            int reserveCount = Math.max(0, team.assignments().size() - capacity);
            if (reserveCount == 0) {
                continue;
            }

            List<TeamAssignmentResponse> selected = new ArrayList<>();
            selectExactRoleSurplus(
                    team.assignments(), roleCapacities(teams.modality()), reserveCount, selected);
            selectSectorSurplus(
                    team.assignments(), sectorCapacities(teams.modality()), reserveCount, selected);
            selectWeakestFallback(team.assignments(), reserveCount, selected);

            selected.stream()
                    .map(TeamAssignmentResponse::id)
                    .forEach(result::add);
        }
        return result;
    }

    private static void selectExactRoleSurplus(
            List<TeamAssignmentResponse> assignments,
            Map<String, Integer> capacities,
            int reserveCount,
            List<TeamAssignmentResponse> selected) {
        Map<String, List<TeamAssignmentResponse>> byRole = assignments.stream()
                .collect(Collectors.groupingBy(
                        TeamAssignmentResponse::assignedRole,
                        java.util.LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)));
        List<TeamAssignmentResponse> candidates = new ArrayList<>();
        for (Map.Entry<String, List<TeamAssignmentResponse>> entry : byRole.entrySet()) {
            Integer roleCapacity = capacities.get(entry.getKey());
            if (roleCapacity == null) {
                continue;
            }
            int surplus = Math.max(0, entry.getValue().size() - roleCapacity);
            entry.getValue().stream()
                    .sorted(WEAKEST_FIRST)
                    .limit(surplus)
                    .forEach(candidates::add);
        }
        addWeakestCandidates(candidates, reserveCount, selected);
    }

    private static void selectSectorSurplus(
            List<TeamAssignmentResponse> assignments,
            Map<String, Integer> capacities,
            int reserveCount,
            List<TeamAssignmentResponse> selected) {
        if (selected.size() >= reserveCount) {
            return;
        }
        Set<UUID> selectedIds = selected.stream()
                .map(TeamAssignmentResponse::id)
                .collect(Collectors.toSet());
        Map<String, List<TeamAssignmentResponse>> bySector = assignments.stream()
                .filter(assignment -> !selectedIds.contains(assignment.id()))
                .collect(Collectors.groupingBy(
                        assignment -> sectorForRole(assignment.assignedRole()),
                        java.util.LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)));
        List<TeamAssignmentResponse> candidates = new ArrayList<>();
        for (Map.Entry<String, List<TeamAssignmentResponse>> entry : bySector.entrySet()) {
            int sectorCapacity = capacities.getOrDefault(entry.getKey(), Integer.MAX_VALUE);
            int surplus = Math.max(0, entry.getValue().size() - sectorCapacity);
            entry.getValue().stream()
                    .sorted(WEAKEST_FIRST)
                    .limit(surplus)
                    .forEach(candidates::add);
        }
        addWeakestCandidates(candidates, reserveCount, selected);
    }

    private static void selectWeakestFallback(
            List<TeamAssignmentResponse> assignments,
            int reserveCount,
            List<TeamAssignmentResponse> selected) {
        if (selected.size() >= reserveCount) {
            return;
        }
        Set<UUID> selectedIds = selected.stream()
                .map(TeamAssignmentResponse::id)
                .collect(Collectors.toSet());
        assignments.stream()
                .filter(assignment -> !selectedIds.contains(assignment.id()))
                .filter(assignment -> !"GOALKEEPER".equals(assignment.assignedRole()))
                .sorted(WEAKEST_FIRST)
                .limit(reserveCount - selected.size())
                .forEach(selected::add);
    }

    private static void addWeakestCandidates(
            List<TeamAssignmentResponse> candidates,
            int reserveCount,
            List<TeamAssignmentResponse> selected) {
        if (selected.size() >= reserveCount) {
            return;
        }
        Set<UUID> selectedIds = selected.stream()
                .map(TeamAssignmentResponse::id)
                .collect(Collectors.toSet());
        candidates.stream()
                .filter(candidate -> !selectedIds.contains(candidate.id()))
                .sorted(WEAKEST_FIRST)
                .limit(reserveCount - selected.size())
                .forEach(selected::add);
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
        return new MatchTeamReservesResponse(
                matchId,
                reserveRepository.findAllByMatchIdOrderByCreatedAtAsc(matchId).stream()
                        .map(MatchTeamReserve::getAssignmentId)
                        .toList());
    }

    private static Map<String, Integer> roleCapacities(MatchModality modality) {
        return switch (modality) {
            case FIELD -> FIELD_ROLE_CAPACITY;
            case FUT7 -> FUT7_ROLE_CAPACITY;
            case FUTSAL -> FUTSAL_ROLE_CAPACITY;
        };
    }

    private static Map<String, Integer> sectorCapacities(MatchModality modality) {
        return switch (modality) {
            case FIELD -> FIELD_SECTOR_CAPACITY;
            case FUT7 -> FUT7_SECTOR_CAPACITY;
            case FUTSAL -> FUTSAL_SECTOR_CAPACITY;
        };
    }

    private static int fieldCapacity(MatchModality modality) {
        return switch (modality) {
            case FIELD -> 11;
            case FUT7 -> 7;
            case FUTSAL -> 5;
        };
    }

    private static String sectorForRole(String role) {
        if ("GOALKEEPER".equals(role)) return "GOALKEEPER";
        if (DEFENSE_ROLES.contains(role)) return "DEFENSE";
        if (MIDFIELD_ROLES.contains(role)) return "MIDFIELD";
        if (ATTACK_ROLES.contains(role)) return "ATTACK";
        return "OTHER";
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
