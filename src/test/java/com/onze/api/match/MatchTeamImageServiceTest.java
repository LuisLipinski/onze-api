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
import com.onze.api.group.GroupRole;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.match.MatchTeamImageService.InvalidTeamImageException;
import com.onze.api.match.MatchTeamImageService.TeamImageLockedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

class MatchTeamImageServiceTest {
    private FootballMatchRepository matches;
    private GroupMemberRepository members;
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
        images = mock(MatchTeamImageRepository.class);
        storage = mock(MatchTeamImageStorage.class);
        events = mock(ApplicationEventPublisher.class);
        service = new MatchTeamImageService(matches, members, images, storage, events);
        match = mock(FootballMatch.class);
        matchId = UUID.randomUUID();
        groupId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        when(match.getGroupId()).thenReturn(groupId);
        when(match.getStatus()).thenReturn(MatchStatus.SCHEDULED);
        when(match.getMatchType()).thenReturn(MatchType.INTERNAL);
        when(match.getTeamCount()).thenReturn(2);
        when(matches.findByIdForUpdate(matchId)).thenReturn(Optional.of(match));
        when(matches.findById(matchId)).thenReturn(Optional.of(match));
        when(members.findByGroupIdAndUserId(groupId, adminId))
                .thenReturn(Optional.of(new GroupMember(groupId, adminId, GroupRole.PRIMARY_ADMIN)));
        when(images.findByMatchIdAndTeamNumber(matchId, 1)).thenReturn(Optional.empty());
        when(images.save(any(MatchTeamImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.upload(eq(matchId), eq(1), any(byte[].class)))
                .thenReturn("https://cdn.example/time-1.jpg");
    }

    @Test
    void administratorUploadsAnImageForOneTeam() {
        var response = service.upload(adminId.toString(), matchId, 1, validImage());

        assertEquals(1, response.teamNumber());
        assertEquals("https://cdn.example/time-1.jpg", response.imageUrl());
        verify(images).save(any(MatchTeamImage.class));
        verify(match, never()).liveStateChanged();
    }

    @Test
    void memberCanListImagesButCannotUpload() {
        UUID memberId = UUID.randomUUID();
        MatchTeamImage image = new MatchTeamImage(matchId, 1, "https://cdn.example/time-1.jpg");
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

    private MockMultipartFile validImage() {
        return new MockMultipartFile("image", "time.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }
}
