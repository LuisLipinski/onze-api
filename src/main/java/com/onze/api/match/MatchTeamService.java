package com.onze.api.match;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupService;
import com.onze.api.group.PlayerPosition;
import com.onze.api.match.TeamModels.MatchTeamsResponse;
import com.onze.api.match.TeamModels.TeamAssignmentResponse;
import com.onze.api.match.TeamModels.TeamResponse;
import com.onze.api.technical.FutsalRole;
import com.onze.api.technical.GroupMemberSkillRating;
import com.onze.api.technical.GroupMemberSkillRatingRepository;
import com.onze.api.technical.PlayerSkill;
import com.onze.api.technical.ScoreSource;
import com.onze.api.technical.TechnicalRatingPolicy;
import com.onze.api.technical.TechnicalRatingPolicy.OverallResult;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchTeamService {

    private static final String GOALKEEPER = "GOALKEEPER";
    private static final String FORCED_REPEAT_NOTICE =
            "Não foi possível variar os times sem piorar o equilíbrio técnico. "
                    + "A melhor formação disponível foi mantida.";
    private static final int RECENT_MATCH_HISTORY_LIMIT = 5;

    private static final Set<PlayerPosition> DEFENSIVE_POSITIONS = Set.of(
            PlayerPosition.DEFENDER, PlayerPosition.RIGHT_DEFENDER,
            PlayerPosition.LEFT_DEFENDER, PlayerPosition.CENTER_DEFENDER,
            PlayerPosition.RIGHT_BACK, PlayerPosition.LEFT_BACK,
            PlayerPosition.DEFENSIVE_MIDFIELDER);
    private static final Set<PlayerPosition> MIDFIELD_POSITIONS = Set.of(
            PlayerPosition.MIDFIELDER, PlayerPosition.DEFENSIVE_MIDFIELDER,
            PlayerPosition.RIGHT_MIDFIELDER, PlayerPosition.LEFT_MIDFIELDER,
            PlayerPosition.CENTRAL_MIDFIELDER, PlayerPosition.PLAYMAKER,
            PlayerPosition.RIGHT_BACK, PlayerPosition.LEFT_BACK);
    private static final Set<PlayerPosition> ATTACK_POSITIONS = Set.of(
            PlayerPosition.ATTACKER, PlayerPosition.RIGHT_WINGER,
            PlayerPosition.LEFT_WINGER, PlayerPosition.CENTER_FORWARD);

    private final FootballMatchRepository matchRepository;
    private final MatchAttendanceRepository attendanceRepository;
    private final MatchGuestRepository guestRepository;
    private final MatchGuestSkillRatingRepository guestRatingRepository;
    private final MatchRentalGoalkeeperRepository rentalRepository;
    private final MatchTeamAssignmentRepository assignmentRepository;
    private final MatchTeamGenerationHistoryRepository generationHistoryRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupMemberSkillRatingRepository memberRatingRepository;
    private final UserRepository userRepository;

    public MatchTeamService(
            FootballMatchRepository matchRepository,
            MatchAttendanceRepository attendanceRepository,
            MatchGuestRepository guestRepository,
            MatchGuestSkillRatingRepository guestRatingRepository,
            MatchRentalGoalkeeperRepository rentalRepository,
            MatchTeamAssignmentRepository assignmentRepository,
            MatchTeamGenerationHistoryRepository generationHistoryRepository,
            GroupMemberRepository memberRepository,
            GroupMemberSkillRatingRepository memberRatingRepository,
            UserRepository userRepository) {
        this.matchRepository = matchRepository;
        this.attendanceRepository = attendanceRepository;
        this.guestRepository = guestRepository;
        this.guestRatingRepository = guestRatingRepository;
        this.rentalRepository = rentalRepository;
        this.assignmentRepository = assignmentRepository;
        this.generationHistoryRepository = generationHistoryRepository;
        this.memberRepository = memberRepository;
        this.memberRatingRepository = memberRatingRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public MatchTeamsResponse generate(String authenticatedUserId, UUID matchId) {
        FootballMatch match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchService.MatchNotFoundException::new);
        GroupMember actor = requireMembership(authenticatedUserId, match.getGroupId());
        requireTechnicalPermission(actor);
        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw new MatchService.MatchCancelledException();
        }
        if (match.getMatchType() != MatchType.INTERNAL || match.getTeamCount() == null) {
            throw new InternalMatchRequiredException();
        }

        List<Participant> participants = participants(match);
        if (!minimumPlayersSatisfied(match, participants.size())) {
            throw new MinimumPlayersNotReachedException(
                    match.getMinimumPlayers() - participants.size());
        }
        long goalkeepers = participants.stream().filter(Participant::goalkeeper).count();
        if (goalkeepers < match.getRequiredGoalkeepers()) {
            throw new GoalkeepersNotReadyException(
                    match.getRequiredGoalkeepers() - (int) goalkeepers);
        }

        GroupAverages averages = groupAverages(match.getGroupId());
        List<TeamDraft> drafts = new ArrayList<>();
        for (int number = 1; number <= match.getTeamCount(); number++) {
            drafts.add(new TeamDraft(number));
        }

        List<Participant> remaining = new ArrayList<>(participants);
        List<Participant> goalkeeperParticipants = remaining.stream()
                .filter(Participant::goalkeeper)
                .sorted(goalkeeperOrder(match, averages))
                .toList();
        remaining.removeAll(goalkeeperParticipants);
        List<MatchTeamAssignment> generated = new ArrayList<>();
        for (Participant goalkeeper : goalkeeperParticipants) {
            TeamDraft team = drafts.stream().min(teamOrder()).orElseThrow();
            ResolvedScore resolved = score(goalkeeper, GOALKEEPER, match, averages);
            generated.add(assignment(
                    match, team, goalkeeper, GOALKEEPER, resolved,
                    TeamPositionOrigin.GOALKEEPER,
                    TeamAssignmentReason.GOALKEEPER_REQUIRED));
        }

        List<String> roles = outfieldTemplate(match.getModality());
        int round = 0;
        while (!remaining.isEmpty()) {
            List<TeamDraft> orderedTeams = drafts.stream().sorted(teamOrder()).toList();
            for (TeamDraft team : orderedTeams) {
                if (remaining.isEmpty()) {
                    break;
                }
                String role = roles.get(round % roles.size());
                Candidate selected = remaining.stream()
                        .map(participant -> candidate(participant, role, match, averages))
                        .max(candidateOrder())
                        .orElseThrow();
                remaining.remove(selected.participant());
                TeamAssignmentReason reason = reasonFor(
                        selected.origin(), round >= roles.size());
                generated.add(assignment(
                        match, team, selected.participant(), role, selected.score(),
                        selected.origin(), reason));
            }
            round++;
        }

        TeamDiversityOptimizer.Result diversity = rebalanceGeneratedTeams(
                match, participants, generated, averages);
        assignmentRepository.deleteAllByMatchId(matchId);
        assignmentRepository.saveAll(generated);
        rememberGeneration(match, generated);
        return response(
                match,
                actor,
                participants,
                generated,
                averages,
                diversity.forcedRepeat() ? FORCED_REPEAT_NOTICE : null);
    }

    @Transactional(readOnly = true)
    public MatchTeamsResponse get(String authenticatedUserId, UUID matchId) {
        FootballMatch match = matchRepository.findById(matchId)
                .orElseThrow(MatchService.MatchNotFoundException::new);
        GroupMember actor = requireMembership(authenticatedUserId, match.getGroupId());
        List<Participant> participants = participants(match);
        return response(
                match,
                actor,
                participants,
                assignmentRepository.findAllByMatchIdOrderByTeamNumberAscCreatedAtAsc(matchId),
                groupAverages(match.getGroupId()));
    }

    @Transactional
    public MatchTeamsResponse updateAssignment(
            String authenticatedUserId,
            UUID matchId,
            UUID assignmentId,
            int teamNumber,
            String assignedRole) {
        FootballMatch match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchService.MatchNotFoundException::new);
        GroupMember actor = requireMembership(authenticatedUserId, match.getGroupId());
        requireTechnicalPermission(actor);
        if (match.getMatchType() != MatchType.INTERNAL
                || teamNumber < 1
                || teamNumber > match.getTeamCount()) {
            throw new InvalidTeamAssignmentException();
        }
        String normalizedRole = normalizeRole(match.getModality(), assignedRole);
        MatchTeamAssignment assignment = assignmentRepository.findByIdAndMatchId(assignmentId, matchId)
                .orElseThrow(TeamAssignmentNotFoundException::new);
        Participant participant = participants(match).stream()
                .filter(item -> item.type() == assignment.getParticipantType()
                        && item.id().equals(assignment.getParticipantId()))
                .findFirst()
                .orElseThrow(TeamAssignmentNotFoundException::new);
        if (GOALKEEPER.equals(normalizedRole) && !participant.goalkeeper()) {
            throw new IneligibleGoalkeeperException();
        }
        assignment.changeByAdministrator(teamNumber, normalizedRole);
        List<Participant> participants = participants(match);
        return response(
                match,
                actor,
                participants,
                assignmentRepository.findAllByMatchIdOrderByTeamNumberAscCreatedAtAsc(matchId),
                groupAverages(match.getGroupId()));
    }

    @Transactional
    public void invalidate(UUID matchId) {
        assignmentRepository.deleteAllByMatchId(matchId);
    }

    private MatchTeamAssignment assignment(
            FootballMatch match,
            TeamDraft team,
            Participant participant,
            String role,
            ResolvedScore score,
            TeamPositionOrigin origin,
            TeamAssignmentReason reason) {
        team.add(score);
        return new MatchTeamAssignment(
                match.getId(), team.number(), participant.type(), participant.id(), role,
                reason, origin);
    }

    private TeamDiversityOptimizer.Result rebalanceGeneratedTeams(
            FootballMatch match,
            List<Participant> participants,
            List<MatchTeamAssignment> assignments,
            GroupAverages averages) {
        Map<String, Participant> participantsByKey = participants.stream().collect(Collectors.toMap(
                participant -> participant.type() + ":" + participant.id(), Function.identity()));
        List<TeamBalanceOptimizer.Slot> slots = new ArrayList<>();

        for (int index = 0; index < assignments.size(); index++) {
            MatchTeamAssignment assignment = assignments.get(index);
            String participantKey = assignment.getParticipantType() + ":" + assignment.getParticipantId();
            Participant participant = participantsByKey.get(participantKey);
            if (participant == null) {
                continue;
            }
            ResolvedScore resolved = score(
                    participant, assignment.getAssignedRole(), match, averages);
            slots.add(new TeamBalanceOptimizer.Slot(
                    index,
                    assignment.getTeamNumber(),
                    assignment.getAssignedRole(),
                    resolved.value(),
                    resolved.source() == ScoreSource.ESTIMATED,
                    participantKey));
        }

        List<TeamBalanceOptimizer.Slot> technicalBest = TeamBalanceOptimizer.optimize(
                slots, match.getTeamCount());
        TeamDiversityOptimizer.Result diversity = TeamDiversityOptimizer.diversify(
                technicalBest,
                match.getTeamCount(),
                diversityContext(match));

        for (TeamBalanceOptimizer.Slot optimized : diversity.slots()) {
            MatchTeamAssignment assignment = assignments.get(optimized.index());
            if (assignment.getTeamNumber() != optimized.teamNumber()) {
                assignment.rebalanceToTeam(optimized.teamNumber());
            }
        }
        return diversity;
    }

    private TeamDiversityOptimizer.Context diversityContext(FootballMatch match) {
        Set<String> blockedDivisions = new HashSet<>();
        generationHistoryRepository.findAllByMatchIdOrderByCreatedAtAsc(match.getId())
                .forEach(history -> blockedDivisions.add(history.getNormalizedSignature()));

        List<MatchTeamAssignment> currentAssignments = assignmentRepository
                .findAllByMatchIdOrderByTeamNumberAscCreatedAtAsc(match.getId());
        if (!currentAssignments.isEmpty()) {
            blockedDivisions.add(TeamDiversityOptimizer.normalizedDivision(
                    historySlots(currentAssignments), match.getTeamCount()));
        }

        Map<String, Double> teammateWeights = new HashMap<>();
        List<FootballMatch> recentMatches = matchRepository
                .findAllByGroupIdAndStatusAndStartsAtAfterOrderByStartsAtAsc(
                        match.getGroupId(), MatchStatus.SCHEDULED, Instant.EPOCH)
                .stream()
                .filter(item -> !item.getId().equals(match.getId()))
                .filter(item -> item.getMatchType() == MatchType.INTERNAL && item.getTeamCount() != null)
                .filter(item -> item.getStartsAt().isBefore(match.getStartsAt()))
                .sorted(Comparator.comparing(FootballMatch::getStartsAt).reversed())
                .limit(RECENT_MATCH_HISTORY_LIMIT)
                .toList();

        double weight = RECENT_MATCH_HISTORY_LIMIT;
        for (FootballMatch recent : recentMatches) {
            List<MatchTeamAssignment> recentAssignments = assignmentRepository
                    .findAllByMatchIdOrderByTeamNumberAscCreatedAtAsc(recent.getId());
            if (recentAssignments.isEmpty()) {
                weight = Math.max(1, weight - 1);
                continue;
            }
            List<TeamBalanceOptimizer.Slot> recentSlots = historySlots(recentAssignments);
            blockedDivisions.add(TeamDiversityOptimizer.normalizedDivision(
                    recentSlots, recent.getTeamCount()));
            TeamDiversityOptimizer.accumulateTeammateWeights(
                    teammateWeights, recentSlots, weight);
            weight = Math.max(1, weight - 1);
        }

        return new TeamDiversityOptimizer.Context(
                blockedDivisions,
                teammateWeights,
                generationHistoryRepository.countByMatchId(match.getId()) + 1);
    }

    private List<TeamBalanceOptimizer.Slot> historySlots(
            List<MatchTeamAssignment> assignments) {
        List<TeamBalanceOptimizer.Slot> slots = new ArrayList<>(assignments.size());
        for (int index = 0; index < assignments.size(); index++) {
            MatchTeamAssignment assignment = assignments.get(index);
            slots.add(new TeamBalanceOptimizer.Slot(
                    index,
                    assignment.getTeamNumber(),
                    assignment.getAssignedRole(),
                    0,
                    false,
                    assignment.getParticipantType() + ":" + assignment.getParticipantId()));
        }
        return slots;
    }

    private void rememberGeneration(
            FootballMatch match,
            List<MatchTeamAssignment> assignments) {
        String signature = TeamDiversityOptimizer.normalizedDivision(
                historySlots(assignments), match.getTeamCount());
        if (!generationHistoryRepository.existsByMatchIdAndNormalizedSignature(
                match.getId(), signature)) {
            generationHistoryRepository.save(new MatchTeamGenerationHistory(
                    match.getId(), signature));
        }
    }

    private Candidate candidate(
            Participant participant,
            String role,
            FootballMatch match,
            GroupAverages averages) {
        return new Candidate(
                participant,
                score(participant, role, match, averages),
                origin(participant, role, match.getModality()));
    }

    private Comparator<Candidate> candidateOrder() {
        return Comparator.comparingInt((Candidate item) -> originPriority(item.origin()))
                .thenComparingInt(item -> item.score().value())
                .thenComparingInt(item -> item.score().source() == ScoreSource.REAL ? 1 : 0)
                .thenComparing(item -> item.participant().id().toString());
    }

    private Comparator<Participant> goalkeeperOrder(
            FootballMatch match,
            GroupAverages averages) {
        return Comparator
                .comparingInt((Participant participant) -> goalkeeperPriority(
                        participant.type(),
                        participant.primaryPosition(),
                        participant.secondaryPosition()))
                .thenComparing(Comparator.comparingInt(
                        (Participant participant) -> score(
                                participant, GOALKEEPER, match, averages).value())
                        .reversed())
                .thenComparing(participant -> participant.id().toString());
    }

    static int goalkeeperPriority(
            TeamParticipantType type,
            PlayerPosition primaryPosition,
            PlayerPosition secondaryPosition) {
        if (type == TeamParticipantType.RENTAL_GOALKEEPER) {
            return 0;
        }
        if (type == TeamParticipantType.MEMBER
                && primaryPosition != PlayerPosition.GOALKEEPER
                && secondaryPosition != PlayerPosition.GOALKEEPER) {
            return 1;
        }
        if (primaryPosition == PlayerPosition.GOALKEEPER) {
            return 2;
        }
        if (secondaryPosition == PlayerPosition.GOALKEEPER) {
            return 3;
        }
        return 4;
    }

    private Comparator<TeamDraft> teamOrder() {
        return Comparator.comparingInt(TeamDraft::estimatedCount)
                .thenComparingInt(TeamDraft::totalScore)
                .thenComparingInt(TeamDraft::size)
                .thenComparingInt(TeamDraft::number);
    }

    private ResolvedScore score(
            Participant participant,
            String role,
            FootballMatch match,
            GroupAverages averages) {
        OverallResult position = roleOverall(participant.ratings(), role, match.getModality());
        if (position.overall() != null && position.reliable()) {
            return new ResolvedScore(position.overall(), position.coverage(), ScoreSource.REAL);
        }
        OverallResult general = TechnicalRatingPolicy.general(participant.ratings());
        if (general.overall() != null) {
            return new ResolvedScore(general.overall(), general.coverage(), ScoreSource.REAL);
        }
        Integer groupPosition = averages.byRole().get(roleKey(match.getModality(), role));
        if (groupPosition != null) {
            return new ResolvedScore(groupPosition, 0, ScoreSource.ESTIMATED);
        }
        if (averages.general() != null) {
            return new ResolvedScore(averages.general(), 0, ScoreSource.ESTIMATED);
        }
        return new ResolvedScore(25, 0, ScoreSource.ESTIMATED);
    }

    private OverallResult roleOverall(
            Map<PlayerSkill, Integer> ratings,
            String role,
            MatchModality modality) {
        if (modality == MatchModality.FUTSAL) {
            return TechnicalRatingPolicy.futsal(ratings, FutsalRole.valueOf(role));
        }
        return TechnicalRatingPolicy.position(ratings, PlayerPosition.valueOf(role));
    }

    private GroupAverages groupAverages(UUID groupId) {
        List<GroupMember> members = memberRepository.findAllByGroupIdOrderByCreatedAtAsc(groupId);
        List<UUID> memberIds = members.stream().map(GroupMember::getId).toList();
        Map<UUID, Map<PlayerSkill, Integer>> ratings = memberRatings(memberIds);
        Map<String, List<Integer>> byRoleValues = new HashMap<>();
        List<Integer> generalValues = new ArrayList<>();

        for (GroupMember member : members) {
            Map<PlayerSkill, Integer> memberRatings = ratings.getOrDefault(member.getId(), Map.of());
            OverallResult general = TechnicalRatingPolicy.general(memberRatings);
            if (general.overall() != null) {
                generalValues.add(general.overall());
            }
            for (MatchModality modality : MatchModality.values()) {
                for (String role : allRoles(modality)) {
                    OverallResult result = roleOverall(memberRatings, role, modality);
                    if (result.overall() != null && result.reliable()) {
                        byRoleValues.computeIfAbsent(roleKey(modality, role), ignored -> new ArrayList<>())
                                .add(result.overall());
                    }
                }
            }
        }
        Map<String, Integer> roleAverages = new HashMap<>();
        byRoleValues.forEach((role, values) -> roleAverages.put(role, average(values)));
        return new GroupAverages(
                Map.copyOf(roleAverages),
                generalValues.isEmpty() ? null : average(generalValues));
    }

    private int average(List<Integer> values) {
        return (int) Math.round(values.stream().mapToInt(Integer::intValue).average().orElse(25));
    }

    private List<Participant> participants(FootballMatch match) {
        List<GroupMember> members = memberRepository
                .findAllByGroupIdOrderByCreatedAtAsc(match.getGroupId());
        Map<UUID, GroupMember> membersByUser = members.stream()
                .collect(Collectors.toMap(GroupMember::getUserId, Function.identity()));
        Map<UUID, Map<PlayerSkill, Integer>> memberRatings = memberRatings(
                members.stream().map(GroupMember::getId).toList());
        List<Participant> result = new ArrayList<>();

        for (MatchAttendance attendance : attendanceRepository
                .findAllByMatchIdOrderByCreatedAtAsc(match.getId())) {
            if (attendance.getStatus() != AttendanceStatus.GOING) {
                continue;
            }
            GroupMember member = membersByUser.get(attendance.getUserId());
            User user = userRepository.findById(attendance.getUserId()).orElse(null);
            if (member == null || user == null) {
                continue;
            }
            result.add(new Participant(
                    TeamParticipantType.MEMBER,
                    attendance.getUserId(),
                    user.getDisplayName(),
                    member.getPrimaryPosition(),
                    member.getSecondaryPosition(),
                    attendance.isGoalkeeper(),
                    memberRatings.getOrDefault(member.getId(), Map.of())));
        }

        List<MatchGuest> guests = guestRepository
                .findAllByMatchIdOrderByCreatedAtAsc(match.getId());
        Map<UUID, Map<PlayerSkill, Integer>> guestRatings = guestRatings(
                guests.stream().map(MatchGuest::getId).toList());
        guests.forEach(guest -> result.add(new Participant(
                TeamParticipantType.GUEST,
                guest.getId(),
                guest.getDisplayName(),
                guest.getPrimaryPosition(),
                guest.getSecondaryPosition(),
                guest.getPrimaryPosition() == PlayerPosition.GOALKEEPER,
                guestRatings.getOrDefault(guest.getId(), Map.of()))));

        rentalRepository.findAllByMatchIdOrderByCreatedAtAsc(match.getId())
                .forEach(rental -> result.add(new Participant(
                        TeamParticipantType.RENTAL_GOALKEEPER,
                        rental.getId(),
                        rental.getDisplayName(),
                        PlayerPosition.GOALKEEPER,
                        null,
                        true,
                        Map.of())));
        return result;
    }

    private Map<UUID, Map<PlayerSkill, Integer>> memberRatings(List<UUID> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<PlayerSkill, Integer>> result = new HashMap<>();
        for (GroupMemberSkillRating rating : memberRatingRepository
                .findAllByGroupMemberIdIn(memberIds)) {
            result.computeIfAbsent(rating.getGroupMemberId(), ignored -> new EnumMap<>(PlayerSkill.class))
                    .put(rating.getSkill(), rating.getRating());
        }
        return result;
    }

    private Map<UUID, Map<PlayerSkill, Integer>> guestRatings(List<UUID> guestIds) {
        if (guestIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<PlayerSkill, Integer>> result = new HashMap<>();
        for (MatchGuestSkillRating rating : guestRatingRepository.findAllByGuestIdIn(guestIds)) {
            result.computeIfAbsent(rating.getGuestId(), ignored -> new EnumMap<>(PlayerSkill.class))
                    .put(rating.getSkill(), rating.getRating());
        }
        return result;
    }

    private MatchTeamsResponse response(
            FootballMatch match,
            GroupMember actor,
            List<Participant> participants,
            List<MatchTeamAssignment> assignments,
            GroupAverages averages) {
        return response(match, actor, participants, assignments, averages, null);
    }

    private MatchTeamsResponse response(
            FootballMatch match,
            GroupMember actor,
            List<Participant> participants,
            List<MatchTeamAssignment> assignments,
            GroupAverages averages,
            String generationNotice) {
        boolean technicalVisible = actor.hasPermission(GroupAdminPermission.EDIT_PLAYER_PROFILES);
        Map<String, Participant> participantsByKey = participants.stream().collect(Collectors.toMap(
                participant -> participant.type() + ":" + participant.id(), Function.identity()));
        int teamCount = match.getTeamCount() == null ? 1 : match.getTeamCount();
        List<TeamResponse> teams = new ArrayList<>();
        for (int teamNumber = 1; teamNumber <= teamCount; teamNumber++) {
            int currentTeam = teamNumber;
            List<ScoredAssignment> scored = assignments.stream()
                    .filter(item -> item.getTeamNumber() == currentTeam)
                    .map(item -> {
                        Participant participant = participantsByKey.get(
                                item.getParticipantType() + ":" + item.getParticipantId());
                        if (participant == null) {
                            return null;
                        }
                        ResolvedScore resolved = score(
                                participant, item.getAssignedRole(), match, averages);
                        return new ScoredAssignment(item, participant, resolved);
                    })
                    .filter(java.util.Objects::nonNull)
                    .toList();
            int strength = scored.isEmpty()
                    ? 0
                    : (int) Math.round(scored.stream()
                            .mapToInt(item -> item.score().value()).average().orElse(0));
            int real = (int) scored.stream()
                    .filter(item -> item.score().source() == ScoreSource.REAL).count();
            int estimated = scored.size() - real;
            List<TeamAssignmentResponse> items = scored.stream()
                    .map(item -> new TeamAssignmentResponse(
                            item.assignment().getId(),
                            item.participant().type(),
                            item.participant().id(),
                            item.participant().displayName(),
                            item.assignment().getAssignedRole(),
                            technicalVisible ? item.score().value() : null,
                            technicalVisible ? item.score().coverage() : null,
                            technicalVisible ? item.score().source() : null,
                            technicalVisible ? item.assignment().getPositionOrigin() : null,
                            technicalVisible ? item.assignment().getAssignmentReason() : null,
                            item.assignment().isManuallyChanged()))
                    .toList();
            teams.add(new TeamResponse(
                    teamNumber,
                    technicalVisible ? strength : null,
                    technicalVisible ? real : null,
                    technicalVisible ? estimated : null,
                    items));
        }
        return new MatchTeamsResponse(
                match.getId(),
                match.getModality(),
                teamCount,
                participants.size(),
                match.getMinimumPlayers(),
                match.getIdealPlayers(),
                participants.size() < match.getIdealPlayers(),
                technicalVisible,
                generationNotice,
                teams);
    }

    private String normalizeRole(MatchModality modality, String requestedRole) {
        if (requestedRole == null) {
            throw new InvalidTeamAssignmentException();
        }
        String role = requestedRole.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            if (modality == MatchModality.FUTSAL) {
                FutsalRole.valueOf(role);
            } else {
                PlayerPosition.valueOf(role);
            }
            return role;
        } catch (IllegalArgumentException exception) {
            throw new InvalidTeamAssignmentException();
        }
    }

    private TeamPositionOrigin origin(
            Participant participant,
            String role,
            MatchModality modality) {
        if (GOALKEEPER.equals(role)) {
            return TeamPositionOrigin.GOALKEEPER;
        }
        if (compatible(participant.primaryPosition(), role, modality)) {
            return TeamPositionOrigin.PRIMARY;
        }
        if (compatible(participant.secondaryPosition(), role, modality)) {
            return TeamPositionOrigin.SECONDARY;
        }
        return TeamPositionOrigin.ALTERNATIVE;
    }

    private boolean compatible(PlayerPosition profile, String role, MatchModality modality) {
        if (profile == null) {
            return false;
        }
        if (modality != MatchModality.FUTSAL) {
            return PlayerPosition.canPlayPosition(profile, PlayerPosition.valueOf(role));
        }
        FutsalRole futsalRole = FutsalRole.valueOf(role);
        return switch (futsalRole) {
            case GOALKEEPER -> profile == PlayerPosition.GOALKEEPER;
            case FIXO -> DEFENSIVE_POSITIONS.contains(profile);
            case RIGHT_WINGER_FUTSAL, LEFT_WINGER_FUTSAL -> MIDFIELD_POSITIONS.contains(profile)
                    || profile == PlayerPosition.RIGHT_WINGER
                    || profile == PlayerPosition.LEFT_WINGER;
            case PIVOT -> ATTACK_POSITIONS.contains(profile);
        };
    }

    private TeamAssignmentReason reasonFor(TeamPositionOrigin origin, boolean extraRound) {
        if (extraRound && origin == TeamPositionOrigin.ALTERNATIVE) {
            return TeamAssignmentReason.TEAM_BALANCE;
        }
        return switch (origin) {
            case PRIMARY -> TeamAssignmentReason.PRIMARY_POSITION;
            case SECONDARY -> TeamAssignmentReason.SECONDARY_POSITION;
            case ALTERNATIVE -> TeamAssignmentReason.BEST_AVAILABLE_POSITION;
            case GOALKEEPER -> TeamAssignmentReason.GOALKEEPER_REQUIRED;
            case MANUAL -> TeamAssignmentReason.MANUAL_ADMIN_CHANGE;
        };
    }

    private int originPriority(TeamPositionOrigin origin) {
        return switch (origin) {
            case PRIMARY -> 3;
            case SECONDARY -> 2;
            case ALTERNATIVE -> 1;
            case GOALKEEPER -> 4;
            case MANUAL -> 0;
        };
    }

    static boolean minimumPlayersSatisfied(FootballMatch match, int participantCount) {
        return participantCount >= match.getMinimumPlayers()
                || match.isBelowMinimumApproved();
    }

    static List<String> outfieldTemplate(MatchModality modality) {
        return switch (modality) {
            case FIELD -> List.of(
                    "CENTER_DEFENDER", "CENTRAL_MIDFIELDER", "CENTER_FORWARD",
                    "RIGHT_BACK", "DEFENSIVE_MIDFIELDER", "RIGHT_WINGER",
                    "LEFT_BACK", "PLAYMAKER", "LEFT_WINGER", "CENTER_DEFENDER");
            case FUT7 -> List.of(
                    "RIGHT_DEFENDER", "RIGHT_MIDFIELDER", "CENTRAL_MIDFIELDER",
                    "LEFT_DEFENDER", "LEFT_MIDFIELDER", "CENTER_FORWARD");
            case FUTSAL -> List.of(
                    "FIXO", "RIGHT_WINGER_FUTSAL", "PIVOT", "LEFT_WINGER_FUTSAL");
        };
    }

    private List<String> allRoles(MatchModality modality) {
        List<String> roles = new ArrayList<>(outfieldTemplate(modality));
        roles.add(GOALKEEPER);
        return roles.stream().distinct().toList();
    }

    private String roleKey(MatchModality modality, String role) {
        return modality + ":" + role;
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

    private record Participant(
            TeamParticipantType type,
            UUID id,
            String displayName,
            PlayerPosition primaryPosition,
            PlayerPosition secondaryPosition,
            boolean goalkeeper,
            Map<PlayerSkill, Integer> ratings) {
    }

    private record ResolvedScore(int value, int coverage, ScoreSource source) {
    }

    private record Candidate(
            Participant participant,
            ResolvedScore score,
            TeamPositionOrigin origin) {
    }

    private record GroupAverages(Map<String, Integer> byRole, Integer general) {
    }

    private record ScoredAssignment(
            MatchTeamAssignment assignment,
            Participant participant,
            ResolvedScore score) {
    }

    private static final class TeamDraft {
        private final int number;
        private int totalScore;
        private int estimatedCount;
        private int size;

        TeamDraft(int number) { this.number = number; }
        int number() { return number; }
        int totalScore() { return totalScore; }
        int estimatedCount() { return estimatedCount; }
        int size() { return size; }

        void add(ResolvedScore score) {
            totalScore += score.value();
            if (score.source() == ScoreSource.ESTIMATED) {
                estimatedCount++;
            }
            size++;
        }
    }

    public static final class InternalMatchRequiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class MinimumPlayersNotReachedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int missingPlayers;
        MinimumPlayersNotReachedException(int missingPlayers) { this.missingPlayers = missingPlayers; }
        public int getMissingPlayers() { return missingPlayers; }
    }

    public static final class GoalkeepersNotReadyException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int missingGoalkeepers;
        GoalkeepersNotReadyException(int missingGoalkeepers) {
            this.missingGoalkeepers = missingGoalkeepers;
        }
        public int getMissingGoalkeepers() { return missingGoalkeepers; }
    }

    public static final class InvalidTeamAssignmentException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class TeamAssignmentNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class IneligibleGoalkeeperException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
