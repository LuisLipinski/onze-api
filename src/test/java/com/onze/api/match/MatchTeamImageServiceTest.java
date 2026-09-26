package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.Group;
import com.onze.api.group.GroupRepository;
import com.onze.api.group.GroupRole;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.match.MatchTeamImageService.InvalidTeamImageException;
import com.onze.api.match.MatchTeamImageService.TeamImageLockedException;
import com.onze.api.match.LiveMatchModels.TeamIdentityNameRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

class MatchTeamImageServiceTest {
    private FootballMatchRepository matches;
    private GroupMemberRepository members;
    private GroupRepository groups;
    private GroupTeamIdentityRepository groupIdentities;
    private MatchTeamImageRepository images;
    private MatchTeamImageStorage storage;
    private ApplicationEventPublisher events;
    private MatchTeamImageService service;
    private FootballMatch match;
    private UUID matchId;
    private UUID groupId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        matches = mock(FootballMatchRepository.class);
        members = mock(GroupMemberRepository.class);
        groups = mock(GroupRepository.class);
        groupIdentities = mock(GroupTeamIdentityRepository.class);
        images = mock(MatchTeamImageRepository.class);
        storage = mock(MatchTeamImageStorage.class);
        events = mock(ApplicationEventPublisher.class);
        service = new MatchTeamImageService(
                matches, members, groups, groupIdentities, images, storage, events);
        match = mock(FootballMatch.class);
        matchId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        when(match.getId()).thenReturn(matchId);
        when(match.getGroupId()).thenReturn(groupId);
        when(match.getStatus()).thenReturn(MatchStatus.SCHEDULED);
        when(match.getMatchType()).thenReturn(MatchType.INTERNAL);
        when(match.getTeamCount()).thenReturn(2);
        Group group = mock(Group.class);
        when(group.getId()).thenReturn(groupId);
        when(group.getName()).thenReturn("Grupo Onze");
        when(group.getPhotoUrl()).thenReturn("https://cdn.example/grupo.jpg");
        when(groups.findById(groupId)).thenReturn(Optional.of(group));
        when(matches.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));
        when(matches.findById(matchId)).thenReturn(Optional.of(match));
        when(members.findByGroupIdAndUserId(groupId, adminId))
                .thenReturn(Optional.of(new GroupMember(groupId, adminId, GroupRole.PRIMARY_ADMIN)));
        when(images.findByMatchIdAndTeamNumber(matchId, 1)).thenReturn(Optional.empty());
        when(images.save(any(MatchTeamImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(groupIdentities.findByGroupIdAndMatchTypeAndTeamNumber(
                groupId, MatchType.INTERNAL, 1)).thenReturn(Optional.empty());
        when(groupIdentities.findAllByGroupIdAndMatchTypeOrderByTeamNumberAsc(
                groupId, MatchType.INTERNAL)).thenReturn(List.of());
        when(groupIdentities.save(any(GroupTeamIdentity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.upload(eq(groupId), eq(MatchType.INTERNAL), eq(1), any(byte[].class)))
                .thenReturn("https://cdn.example/time-1.jpg");
    }

    @Test
    void administratorUploadsAnImageForOneTeam() {
        var response = service.upload(adminId.toString(), matchId, 1, validImage());

        assertEquals(1, response.teamNumber());
        assertEquals("Time 1", response.name());
        assertEquals("https://cdn.example/time-1.jpg", response.imageUrl());
        verify(groupIdentities).save(any(GroupTeamIdentity.class));
        verify(images, never()).save(any(MatchTeamImage.class));
        verify(match, never()).liveStateChanged();
    }

    @Test
    void memberCanListImagesButCannotUpload() {
        UUID memberId = UUID.randomUUID();
        MatchTeamImage image = new MatchTeamImage(matchId, 1, "https://cdn.example/time-1.jpg");
        when(match.getStatus()).thenReturn(MatchStatus.IN_PROGRESS);
        when(members.findByGroupIdAndUserId(groupId, memberId))
                .thenReturn(Optional.of(new GroupMember(groupId, memberId, GroupRole.MEMBER)));
        when(images.findAllByMatchIdOrderByTeamNumberAsc(matchId)).thenReturn(List.of(image));

        assertEquals("https://cdn.example/time-1.jpg",
                service.list(memberId.toString(), matchId).getFirst().imageUrl());
        assertThrows(GroupAccessDeniedException.class,
                () -> service.upload(memberId.toString(), matchId, 1, validImage()));
    }

    @Test
    void rejectsInvalidTeamOrFile() {
        assertThrows(InvalidTeamImageException.class,
                () -> service.upload(adminId.toString(), matchId, 3, validImage()));
        assertThrows(InvalidTeamImageException.class,
                () -> service.upload(adminId.toString(), matchId, 1,
                        new MockMultipartFile("image", "time.txt", "text/plain", new byte[] {1})));
    }

    @Test
    void rejectsChangesAfterMatchEnds() {
        when(match.getStatus()).thenReturn(MatchStatus.FINISHED);

        assertThrows(TeamImageLockedException.class,
                () -> service.upload(adminId.toString(), matchId, 1, validImage()));
    }

    @Test
    void imageChangedDuringLiveMatchPublishesRealtimeUpdate() {
        when(match.getStatus()).thenReturn(MatchStatus.IN_PROGRESS);
        when(match.getLiveVersion()).thenReturn(9L);

        service.upload(adminId.toString(), matchId, 1, validImage());

        verify(match).liveStateChanged();
        ArgumentCaptor<LiveMatchChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(LiveMatchChangedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertEquals(matchId, eventCaptor.getValue().matchId());
        assertEquals(groupId, eventCaptor.getValue().groupId());
        assertEquals(9L, eventCaptor.getValue().version());
        assertEquals(LiveMatchChangeType.TEAM_IMAGE_UPDATED, eventCaptor.getValue().type());
    }

    @Test
    void scheduledMatchUsesSavedIdentityAndGroupPhotoAsFallback() {
        GroupTeamIdentity saved = new GroupTeamIdentity(
                groupId,
                MatchType.INTERNAL,
                1,
                "Time Preto",
                "https://cdn.example/preto.jpg");
        when(groupIdentities.findAllByGroupIdAndMatchTypeOrderByTeamNumberAsc(
                groupId, MatchType.INTERNAL)).thenReturn(List.of(saved));

        var result = service.list(adminId.toString(), matchId);

        assertEquals("Time Preto", result.getFirst().name());
        assertEquals("https://cdn.example/preto.jpg", result.getFirst().imageUrl());
        assertEquals("Time 2", result.get(1).name());
        assertEquals("https://cdn.example/grupo.jpg", result.get(1).imageUrl());
    }

    @Test
    void startingMatchPersistsNamesAndSnapshotsEffectiveImages() {
        service.snapshotForStart(match, List.of(
                new TeamIdentityNameRequest(1, "Time Preto"),
                new TeamIdentityNameRequest(2, "Time Branco")));

        ArgumentCaptor<MatchTeamImage> snapshotCaptor = ArgumentCaptor.forClass(MatchTeamImage.class);
        verify(images, org.mockito.Mockito.times(2)).save(snapshotCaptor.capture());
        assertEquals(List.of("Time Preto", "Time Branco"), snapshotCaptor.getAllValues().stream()
                .map(MatchTeamImage::getTeamName)
                .toList());
        assertEquals(List.of(
                "https://cdn.example/grupo.jpg",
                "https://cdn.example/grupo.jpg"), snapshotCaptor.getAllValues().stream()
                .map(MatchTeamImage::getImageUrl)
                .toList());
    }

    private MockMultipartFile validImage() {
        return new MockMultipartFile("image", "time.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }
}
