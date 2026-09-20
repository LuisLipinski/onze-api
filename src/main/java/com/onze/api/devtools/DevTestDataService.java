package com.onze.api.devtools;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.onze.api.devtools.DevTestDataModels.ApplyScenarioResponse;
import com.onze.api.devtools.DevTestDataModels.GeneratePlayersResponse;
import com.onze.api.devtools.DevTestDataModels.MatchAttendanceResponse;
import com.onze.api.devtools.DevTestDataModels.StatusResponse;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRepository;
import com.onze.api.group.GroupRole;
import com.onze.api.match.AttendanceStatus;
import com.onze.api.match.FootballMatch;
import com.onze.api.match.FootballMatchRepository;
import com.onze.api.match.MatchAttendance;
import com.onze.api.match.MatchAttendanceRepository;
import com.onze.api.match.MatchCapacityService;
import com.onze.api.match.MatchStatus;
import com.onze.api.match.MatchTeamAssignmentRepository;
import com.onze.api.match.MatchType;
import com.onze.api.match.TeamParticipantType;
import com.onze.api.group.PlayerPosition;
import com.onze.api.technical.GroupMemberSkillRating;
import com.onze.api.technical.GroupMemberSkillRatingRepository;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DevTestDataService {

    private static final String TEST_EMAIL_PREFIX = "onze-test-";
    private static final String TEST_EMAIL_DOMAIN = "@example.invalid";

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupMemberSkillRatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final FootballMatchRepository matchRepository;
    private final MatchAttendanceRepository attendanceRepository;
    private final MatchCapacityService capacityService;
    private final MatchTeamAssignmentRepository teamAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DevTestDataService(
            GroupRepository groupRepository,
            GroupMemberRepository memberRepository,
            GroupMemberSkillRatingRepository ratingRepository,
            UserRepository userRepository,
            FootballMatchRepository matchRepository,
            MatchAttendanceRepository attendanceRepository,
            MatchCapacityService capacityService,
            MatchTeamAssignmentRepository teamAssignmentRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.ratingRepository = ratingRepository;
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.attendanceRepository = attendanceRepository;
        this.capacityService = capacityService;
        this.teamAssignmentRepository = teamAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StatusResponse status(String authenticatedUserId, UUID matchId) {
        FootballMatch match = requireMatch(matchId);
        requirePrimaryAdmin(authenticatedUserId, match.getGroupId());
        List<TestMember> testMembers = testMembers(match.getGroupId());
        Set<UUID> testUserIds = testUserIds(testMembers);
        int going = (int) attendanceRepository.findAllByMatchIdOrderByCreatedAtAsc(matchId).stream()
                .filter(attendance -> attendance.getStatus() == AttendanceStatus.GOING)
                .filter(attendance -> testUserIds.contains(attendance.getUserId()))
                .count();
        return new StatusResponse(
                matchId,
                match.getGroupId(),
                testMembers.size(),
                going,
                capacityService.occupiedSpots(matchId),
                match.getMaxPlayers(),
                List.of(DevTestDataScenario.values()));
    }

    @Transactional
    public GeneratePlayersResponse generate(
            String authenticatedUserId,
            UUID matchId,
            int requestedCount) {
        FootballMatch match = requireMatch(matchId);
        requirePrimaryAdmin(authenticatedUserId, match.getGroupId());

        int created = 0;
        int reused = 0;
        int idealPlayers = idealPlayers(match);

        removeExistingTestPlayers(match.getGroupId());

        for (int number = 1; number <= requestedCount; number++) {
            String email = testEmail(match.getGroupId(), number);
            User user = userRepository.save(new User(
                    email,
                    passwordEncoder.encode(UUID.randomUUID().toString()),
                    testDisplayName(number)));
            GroupMember member = memberRepository.save(new GroupMember(
                    match.getGroupId(), user.getId(), GroupRole.MEMBER));
            applyProfile(member, DevTestDataScenarioPolicy.profile(
                    number, idealPlayers, match.getModality(), DevTestDataScenario.BALANCED));
            created++;
        }

        return new GeneratePlayersResponse(
                created,
                reused,
                testMembers(match.getGroupId()).size());
    }

    @Transactional
    public ApplyScenarioResponse applyScenario(
            String authenticatedUserId,
            UUID matchId,
            DevTestDataScenario scenario) {
        FootballMatch match = requireMatch(matchId);
        requirePrimaryAdmin(authenticatedUserId, match.getGroupId());
        List<TestMember> testMembers = testMembers(match.getGroupId());
        int idealPlayers = idealPlayers(match);

        for (int index = 0; index < testMembers.size(); index++) {
            applyProfile(
                    testMembers.get(index).member(),
                    DevTestDataScenarioPolicy.profile(
                            index + 1,
                            idealPlayers,
                            match.getModality(),
                            scenario));
        }
        if (!testMembers.isEmpty()) {
            teamAssignmentRepository.deleteAllByMatchId(matchId);
        }
        return new ApplyScenarioResponse(scenario, testMembers.size());
    }

    @Transactional
    public MatchAttendanceResponse addToMatch(String authenticatedUserId, UUID matchId) {
        FootballMatch match = requireScheduledMatch(matchId);
        requirePrimaryAdmin(authenticatedUserId, match.getGroupId());
        List<TestMember> testMembers = testMembers(match.getGroupId());
        long occupied = capacityService.occupiedSpots(matchId);
        int added = 0;
        int alreadyGoing = 0;
        int skippedCapacity = 0;

        for (TestMember testMember : testMembers) {
            MatchAttendance attendance = attendanceRepository
                    .findByMatchIdAndUserId(matchId, testMember.user().getId())
                    .orElse(null);
            if (attendance != null && attendance.getStatus() == AttendanceStatus.GOING) {
                alreadyGoing++;
                continue;
            }
            if (occupied >= match.getMaxPlayers()) {
                skippedCapacity++;
                continue;
            }

            if (attendance == null) {
                attendance = new MatchAttendance(
                        matchId,
                        testMember.user().getId(),
                        AttendanceStatus.GOING,
                        match.getPaymentAmount());
            } else {
                attendance.changeStatus(
                        AttendanceStatus.GOING,
                        match.getPaymentAmount(),
                        clock.instant());
            }
            attendance.setGoalkeeper(
                    testMember.member().getPrimaryPosition() == PlayerPosition.GOALKEEPER);
            attendanceRepository.save(attendance);
            added++;
            occupied++;
        }

        if (added > 0) {
            teamAssignmentRepository.deleteAllByMatchId(matchId);
        }
        return new MatchAttendanceResponse(
                added,
                alreadyGoing,
                0,
                skippedCapacity,
                capacityService.occupiedSpots(matchId),
                match.getMaxPlayers());
    }

    @Transactional
    public MatchAttendanceResponse removeFromMatch(String authenticatedUserId, UUID matchId) {
        FootballMatch match = requireMatch(matchId);
        requirePrimaryAdmin(authenticatedUserId, match.getGroupId());
        Set<UUID> testUserIds = testUserIds(testMembers(match.getGroupId()));
        int removed = 0;

        for (MatchAttendance attendance : attendanceRepository.findAllByMatchIdOrderByCreatedAtAsc(matchId)) {
            if (!testUserIds.contains(attendance.getUserId())
                    || attendance.getStatus() != AttendanceStatus.GOING) {
                continue;
            }
            attendance.changeStatus(
                    AttendanceStatus.NOT_GOING,
                    match.getPaymentAmount(),
                    clock.instant());
            attendanceRepository.save(attendance);
            removed++;
        }

        if (removed > 0) {
            teamAssignmentRepository.deleteAllByMatchId(matchId);
        }
        return new MatchAttendanceResponse(
                0,
                0,
                removed,
                0,
                capacityService.occupiedSpots(matchId),
                match.getMaxPlayers());
    }

    private void applyProfile(
            GroupMember member,
            DevTestDataScenarioPolicy.TestProfile profile) {
        member.updateSportsProfile(
                profile.primaryPosition(),
                profile.secondaryPosition(),
                profile.canPlayGoalkeeper(),
                profile.dominantFoot(),
                null);
        member.markTechnicalProfileUpdated(clock.instant());
        memberRepository.save(member);

        List<GroupMemberSkillRating> existingRatings = ratingRepository.findAllByGroupMemberId(member.getId());
        if (!existingRatings.isEmpty()) {
            ratingRepository.deleteAll(existingRatings);
        }
        ratingRepository.saveAll(profile.ratings().entrySet().stream()
                .map(entry -> new GroupMemberSkillRating(
                        member.getId(), entry.getKey(), entry.getValue()))
                .toList());
    }

    private void removeExistingTestPlayers(UUID groupId) {
        List<TestMember> existing = testMembers(groupId);
        if (existing.isEmpty()) return;
        List<UUID> userIds = existing.stream().map(item -> item.user().getId()).toList();
        teamAssignmentRepository.deleteAllByParticipantTypeAndParticipantIdIn(
                TeamParticipantType.MEMBER, userIds);
        userRepository.deleteAllInBatch(existing.stream().map(TestMember::user).toList());
        userRepository.flush();
    }

    private int idealPlayers(FootballMatch match) {
        int sides = match.getMatchType() == MatchType.INTERNAL && match.getTeamCount() != null
                ? match.getTeamCount()
                : 1;
        return match.getModality().playersPerTeam() * sides;
    }

    private List<TestMember> testMembers(UUID groupId) {
        String prefix = testEmailPrefix(groupId);
        List<TestMember> result = new ArrayList<>();
        for (GroupMember member : memberRepository.findAllByGroupIdOrderByCreatedAtAsc(groupId)) {
            User user = userRepository.findById(member.getUserId()).orElse(null);
            if (user != null
                    && user.getEmail().startsWith(prefix)
                    && user.getEmail().endsWith(TEST_EMAIL_DOMAIN)) {
                result.add(new TestMember(member, user));
            }
        }
        result.sort(Comparator.comparing(testMember -> testMember.user().getDisplayName()));
        return result;
    }

    private Set<UUID> testUserIds(List<TestMember> testMembers) {
        Set<UUID> result = new HashSet<>();
        testMembers.forEach(testMember -> result.add(testMember.user().getId()));
        return result;
    }

    private FootballMatch requireMatch(UUID matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Partida não encontrada."));
    }

    private FootballMatch requireScheduledMatch(UUID matchId) {
        FootballMatch match = requireMatch(matchId);
        if (match.getStatus() != MatchStatus.SCHEDULED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A partida não está agendada.");
        }
        return match;
    }

    private void requirePrimaryAdmin(String authenticatedUserId, UUID groupId) {
        UUID userId = parseUserId(authenticatedUserId);
        if (!groupRepository.existsById(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não encontrado.");
        }
        GroupMember actor = memberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Acesso negado."));
        if (actor.getRole() != GroupRole.PRIMARY_ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Ferramentas de teste são exclusivas do Administrador Principal.");
        }
    }

    private UUID parseUserId(String authenticatedUserId) {
        try {
            return UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida.");
        }
    }

    private String testEmail(UUID groupId, int number) {
        return testEmailPrefix(groupId) + String.format("%02d", number) + TEST_EMAIL_DOMAIN;
    }

    private String testEmailPrefix(UUID groupId) {
        return TEST_EMAIL_PREFIX + groupId + "-";
    }

    private String testDisplayName(int number) {
        return String.format("Teste %02d", number);
    }

    private record TestMember(GroupMember member, User user) {
    }
}
