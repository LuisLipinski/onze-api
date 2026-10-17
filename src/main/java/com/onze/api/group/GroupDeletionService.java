package com.onze.api.group;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.onze.api.group.GroupAdminService.PrimaryAdminRequiredException;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupDeletionService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupDeletionNoticeRepository noticeRepository;
    private final Clock clock;

    public GroupDeletionService(
            GroupRepository groupRepository,
            GroupMemberRepository memberRepository,
            GroupDeletionNoticeRepository noticeRepository,
            Clock clock) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.noticeRepository = noticeRepository;
        this.clock = clock;
    }

    @Transactional
    public void delete(String authenticatedUserId, UUID groupId) {
        UUID userId = UUID.fromString(authenticatedUserId);
        Group group = groupRepository.findByIdForUpdate(groupId).orElseThrow(GroupNotFoundException::new);
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupAccessDeniedException::new);
        if (member.getRole() != GroupRole.PRIMARY_ADMIN) {
            throw new PrimaryAdminRequiredException();
        }

        Instant now = clock.instant();
        noticeRepository.saveAll(memberRepository.findAllByGroupIdOrderByCreatedAtAsc(groupId).stream()
                .map(recipient -> new GroupDeletionNotice(groupId, group.getName(), recipient.getUserId(), now))
                .toList());
        // PostgreSQL removes the group's dependants. Notices have no FK to the deleted group.
        groupRepository.delete(group);
        groupRepository.flush();
    }

    @Transactional(readOnly = true)
    public List<DeletionNoticeResponse> unreadNotices(String authenticatedUserId) {
        return noticeRepository.findTop50ByRecipientUserIdAndAcknowledgedAtIsNullOrderByCreatedAtAsc(
                        UUID.fromString(authenticatedUserId)).stream()
                .map(notice -> new DeletionNoticeResponse(
                        notice.getId(), notice.getGroupId(), notice.getGroupName(), notice.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void acknowledge(String authenticatedUserId, UUID noticeId) {
        GroupDeletionNotice notice = noticeRepository.findByIdAndRecipientUserId(
                        noticeId, UUID.fromString(authenticatedUserId))
                .orElseThrow(GroupDeletionNoticeNotFoundException::new);
        notice.acknowledge(clock.instant());
    }

    public record DeletionNoticeResponse(UUID id, UUID groupId, String groupName, Instant deletedAt) { }

    public static final class GroupDeletionNoticeNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
