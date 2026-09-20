package com.onze.api.devtools;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRepository;
import com.onze.api.group.GroupRole;
import com.onze.api.group.PlayerPosition;
import com.onze.api.match.FootballMatch;
import com.onze.api.match.FootballMatchRepository;
import com.onze.api.match.MatchAttendance;
import com.onze.api.match.MatchAttendanceRepository;
import com.onze.api.match.MatchCapacityService;
import com.onze.api.match.MatchModality;
import com.onze.api.match.MatchStatus;
import com.onze.api.match.MatchTeamAssignmentRepository;
import com.onze.api.match.MatchType;
import com.onze.api.match.TeamParticipantType;
import com.onze.api.technical.GroupMemberSkillRatingRepository;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class DevTestDataServiceTest {
    private final UUID matchId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID testUserId = UUID.randomUUID();
    private GroupRepository groups;
    private GroupMemberRepository members;
    private GroupMemberSkillRatingRepository ratings;
    private UserRepository users;
    private FootballMatchRepository matches;
    private MatchAttendanceRepository attendances;
    private MatchCapacityService capacity;
    private MatchTeamAssignmentRepository assignments;
    private DevTestDataService service;
    private FootballMatch match;
    private GroupMember testMember;
    private User testUser;

    @BeforeEach
    void setUp() {
        groups = mock(GroupRepository.class);
        members = mock(GroupMemberRepository.class);
        ratings = mock(GroupMemberSkillRatingRepository.class);
        users = mock(UserRepository.class);
        matches = mock(FootballMatchRepository.class);
        attendances = mock(MatchAttendanceRepository.class);
        capacity = mock(MatchCapacityService.class);
        assignments = mock(MatchTeamAssignmentRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        service = new DevTestDataService(groups, members, ratings, users, matches, attendances,
                capacity, assignments, encoder,
                Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC));

        match = mock(FootballMatch.class);
        when(match.getId()).thenReturn(matchId);
        when(match.getGroupId()).thenReturn(groupId);
        when(match.getStatus()).thenReturn(MatchStatus.SCHEDULED);
        when(match.getMatchType()).thenReturn(MatchType.INTERNAL);
        when(match.getTeamCount()).thenReturn(2);
        when(match.getModality()).thenReturn(MatchModality.FUT7);
        when(match.getMaxPlayers()).thenReturn(14);
        when(matches.findById(matchId)).thenReturn(Optional.of(match));
        when(groups.existsById(groupId)).thenReturn(true);
        when(members.findByGroupIdAndUserId(groupId, adminId))
                .thenReturn(Optional.of(new GroupMember(groupId, adminId, GroupRole.PRIMARY_ADMIN)));

        testMember = mock(GroupMember.class);
        when(testMember.getUserId()).thenReturn(testUserId);
        when(testMember.getPrimaryPosition()).thenReturn(PlayerPosition.GOALKEEPER);
        testUser = mock(User.class);
        when(testUser.getId()).thenReturn(testUserId);
        when(testUser.getEmail()).thenReturn("onze-test-" + groupId + "-01@example.invalid");
        when(testUser.getDisplayName()).thenReturn("Teste 01");
        when(members.findAllByGroupIdOrderByCreatedAtAsc(groupId)).thenReturn(List.of(testMember));
        when(users.findById(testUserId)).thenReturn(Optional.of(testUser));
    }

    @Test
    void generationRemovesPreviousArtificialUsersBeforeCreatingTheNewPool() {
        service.generate(adminId.toString(), matchId, 0);

        verify(assignments).deleteAllByParticipantTypeAndParticipantIdIn(
                TeamParticipantType.MEMBER, List.of(testUserId));
        verify(users).deleteAllInBatch(List.of(testUser));
        verify(users).flush();
    }

    @Test
    void addingArtificialPrimaryGoalkeeperMarksTheEffectiveMatchRole() {
        when(capacity.occupiedSpots(matchId)).thenReturn(0L);
        when(attendances.findByMatchIdAndUserId(matchId, testUserId)).thenReturn(Optional.empty());

        service.addToMatch(adminId.toString(), matchId);

        ArgumentCaptor<MatchAttendance> saved = ArgumentCaptor.forClass(MatchAttendance.class);
        verify(attendances).save(saved.capture());
        assertTrue(saved.getValue().isGoalkeeper());
    }
}
