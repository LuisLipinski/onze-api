package com.onze.api.group;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.onze.api.group.GroupModels.CreateGroupRequest;
import com.onze.api.group.GroupModels.GroupResponse;
import com.onze.api.group.GroupModels.ScheduleRequest;
import com.onze.api.group.GroupModels.ScheduleResponse;
import com.onze.api.group.GroupModels.UpdateGroupDetailsRequest;
import com.onze.api.user.User;
import com.onze.api.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupScheduleRepository groupScheduleRepository;
    private final UserRepository userRepository;

    public GroupService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupScheduleRepository groupScheduleRepository,
            UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupScheduleRepository = groupScheduleRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public GroupResponse create(String authenticatedUserId, CreateGroupRequest request) {
        User creator = requireUser(authenticatedUserId);
        Group group = groupRepository.save(new Group(
                request.name().trim(),
                normalizeOptional(request.description()),
                creator.getId()));

        groupMemberRepository.save(new GroupMember(group.getId(), creator.getId(), GroupRole.ADMIN));
        return toResponse(group, GroupRole.ADMIN, List.of());
    }

    @Transactional
    public GroupResponse updateDetails(
            String authenticatedUserId,
            UUID groupId,
            UpdateGroupDetailsRequest request) {
        UUID userId = parseUserId(authenticatedUserId);
        Group group = requireGroup(groupId);
        GroupMember membership = requireAdmin(groupId, userId);

        group.updateOptionalDetails(
                normalizeOptional(request.city()),
                normalizeOptional(request.mascot()),
                normalizeOptional(request.venue()));

        groupScheduleRepository.deleteAllByGroupId(groupId);
        List<GroupSchedule> schedules = uniqueSchedules(groupId, request.schedules());
        if (!schedules.isEmpty()) {
            groupScheduleRepository.saveAll(schedules);
        }

        return toResponse(group, membership.getRole(), schedules);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listForUser(String authenticatedUserId) {
        UUID userId = parseUserId(authenticatedUserId);
        List<GroupMember> memberships = groupMemberRepository.findAllByUserIdOrderByCreatedAtAsc(userId);
        List<GroupResponse> result = new ArrayList<>(memberships.size());

        for (GroupMember membership : memberships) {
            Group group = groupRepository.findById(membership.getGroupId()).orElse(null);
            if (group == null) {
                continue;
            }
            result.add(toResponse(
                    group,
                    membership.getRole(),
                    groupScheduleRepository.findAllByGroupId(group.getId())));
        }
        return result;
    }

    private User requireUser(String authenticatedUserId) {
        UUID userId = parseUserId(authenticatedUserId);
        return userRepository.findById(userId).orElseThrow(GroupUserNotFoundException::new);
    }

    private Group requireGroup(UUID groupId) {
        return groupRepository.findById(groupId).orElseThrow(GroupNotFoundException::new);
    }

    private GroupMember requireAdmin(UUID groupId, UUID userId) {
        GroupMember membership = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupAccessDeniedException::new);
        if (membership.getRole() != GroupRole.ADMIN) {
            throw new GroupAccessDeniedException();
        }
        return membership;
    }

    private UUID parseUserId(String authenticatedUserId) {
        try {
            return UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupUserNotFoundException();
        }
    }

    private List<GroupSchedule> uniqueSchedules(UUID groupId, List<ScheduleRequest> requestedSchedules) {
        if (requestedSchedules == null || requestedSchedules.isEmpty()) {
            return List.of();
        }

        Map<String, GroupSchedule> unique = new LinkedHashMap<>();
        for (ScheduleRequest schedule : requestedSchedules) {
            String key = schedule.dayOfWeek().name() + "@" + schedule.startTime();
            unique.putIfAbsent(
                    key,
                    new GroupSchedule(groupId, schedule.dayOfWeek(), schedule.startTime()));
        }
        return new ArrayList<>(unique.values());
    }

    private GroupResponse toResponse(Group group, GroupRole role, List<GroupSchedule> schedules) {
        List<ScheduleResponse> scheduleResponses = schedules.stream()
                .sorted(Comparator
                        .comparing((GroupSchedule schedule) -> dayOrder(schedule.getDayOfWeek()))
                        .thenComparing(GroupSchedule::getStartTime))
                .map(schedule -> new ScheduleResponse(schedule.getDayOfWeek(), schedule.getStartTime()))
                .toList();

        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getPhotoUrl(),
                group.getCity(),
                group.getMascot(),
                group.getVenue(),
                scheduleResponses,
                role,
                group.getCreatedAt());
    }

    private int dayOrder(DayOfWeek dayOfWeek) {
        return dayOfWeek.getValue();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static final class GroupNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class GroupAccessDeniedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class GroupUserNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
