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
            "DEFENDER",
            "RIGHT_DEFENDER",
            "LEFT_DEFENDER",
            "CENTER_DEFENDER",
            "RIGHT_BACK",
            "LEFT_BACK",
            "FIXO");

    private static final Set<String> MIDFIELD_ROLES = Set.of(
            "DEFENSIVE_MIDFIELDER",
            "MIDFIELDER",
            "RIGHT_MIDFIELDER",
            "LEFT_MIDFIELDER",
            "CENTRAL_MIDFIELDER",
            "PLAYMAKER",
            "RIGHT_WINGER_FUTSAL",
            "LEFT_WINGER_FUTSAL");

    private static final Set<String> ATTACK_ROLES = Set.of(
            "ATTACKER",
            "RIGHT_WINGER",
            "LEFT_WINGER",
            "CENTER_FORWARD",
            "PIVOT");

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
        Map<String, Integer> sectorCapacity = sectorCapacities(teams.modality());
        int fieldCapacity = fieldCapacity(teams.modality());

        for (TeamResponse team : teams.teams()) {
            int reserveCount = Math.max(0, team.assignments().size() - fieldCapacity);
            if (reserveCount == 0) {
                continue;
            }

            Map<String, List<TeamAssignmentResponse>> bySector = team.assignments().stream()
                    .collect(Collectors.groupingBy(
                            assignment -> sectorForRole(assignment.assignedRole()),
                            java.util.LinkedHashMap::new,
                            Collectors.toCollection(ArrayList::new)));

            List<TeamAssignmentResponse> surplusCandidates = new ArrayList<>();
            for (Map.Entry<String, List<TeamAssignmentResponse>> entry : bySector.entrySet()) {
                int capacity = sectorCapacity.getOrDefault(entry.getKey(), Integer.MAX_VALUE);
                int surplus = Math.max(0, entry.getValue().size() - capacity);
                if (surplus == 0) {
                    continue;
                }

                List<TeamAssignmentResponse> weakestInSector = entry.getValue().stream()
                        .sorted(WEAKEST_FIRST)
                        .limit(surplus)
                        .toList();
                surplusCandidates.addAll(weakestInSector);
            }

            surplusCandidates.sort(WEAKEST_FIRST);
            List<TeamAssignmentResponse> selected = new ArrayList<>();
            for (TeamAssignmentResponse candidate : surplusCandidates) {
                if (selected.size() >= reserveCount) {
                    break;
                }
                selected.add(candidate);
            }

            if (selected.size() < reserveCount) {
                Set<UUID> alreadySelected = selected.stream()
                        .map(TeamAssignmentResponse::id)
                        .collect(Collectors.toSet());
                team.assignments().stream()
                        .filter(assignment -> !alreadySelected.contains(assignment.id()))
                        .filter(assignment -> !"GOALKEEPER".equals(assignment.assignedRole()))
                        .sorted(WEAKEST_FIRST)
                        .limit(reserveCount - selected.size())
                        .forEach(selected::add);
            }

            selected.stream()
                    .map(TeamAssignmentResponse::id)
                    .forEach(result::add);
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
        if ("GOALKEEPER".equals(role)) {
            return "GOALKEEPER";
        }
        if (DEFENSE_ROLES.contains(role)) {
            return "DEFENSE";
        }
        if (MIDFIELD_ROLES.contains(role)) {
            return "MIDFIELD";
        }
        if (ATTACK_ROLES.contains(role)) {
            return "ATTACK";
        }
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
