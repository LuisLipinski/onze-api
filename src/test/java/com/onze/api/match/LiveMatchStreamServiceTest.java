package com.onze.api.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRole;
import com.onze.api.group.GroupService.GroupUserNotFoundException;

import org.junit.jupiter.api.Test;

class LiveMatchStreamServiceTest {

    @Test
    void subscribesOnceAndKeepsHeartbeatOutOfTheDatabase() {
        GroupMemberRepository members = mock(GroupMemberRepository.class);
        UUID userId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        when(members.findAllByUserIdOrderByCreatedAtAsc(userId))
                .thenReturn(List.of(new GroupMember(groupId, userId, GroupRole.MEMBER)));
        LiveMatchStreamService service = new LiveMatchStreamService(members);

        service.subscribe(userId.toString(), null);
        service.heartbeat();

        assertEquals(1, service.activeConnectionCount());
        verify(members, times(1)).findAllByUserIdOrderByCreatedAtAsc(userId);
    }

    @Test
    void rejectsInvalidAuthenticatedUserId() {
        LiveMatchStreamService service = new LiveMatchStreamService(mock(GroupMemberRepository.class));

        assertThrows(GroupUserNotFoundException.class, () -> service.subscribe("invalid", null));
    }
}
