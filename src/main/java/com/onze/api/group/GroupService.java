package com.onze.api.group;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.web.multipart.MultipartFile;

@Service
public class GroupService {

    private static final long MAX_PHOTO_BYTES = 5L * 1024L * 1024L;

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupScheduleRepository groupScheduleRepository;
    private final UserRepository userRepository;
    private final GroupPhotoStorage groupPhotoStorage;

    public GroupService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupScheduleRepository groupScheduleRepository,
            UserRepository userRepository,
            GroupPhotoStorage groupPhotoStorage) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupScheduleRepository = groupScheduleRepository;
        this.userRepository = userRepository;
        this.groupPhotoStorage = groupPhotoStorage;
    }

    @Transactional
    public GroupResponse create(String authenticatedUserId, CreateGroupRequest request) {
        User creator = requireUser(authenticatedUserId);
        Group group = groupRepository.save(new Group(
                request.name().trim(),
                normalizeOptional(request.description()),
                creator.getId()));

        groupMemberRepository.save(new GroupMember(group.getId(), creator.getId(), GroupRole.PRIMARY_ADMIN));
        return toResponse(
                group,
                GroupRole.PRIMARY_ADMIN,
                Set.of(GroupAdminPermission.values()),
                List.of());
    }

    @Transactional
    public GroupResponse updateDetails(
            String authenticatedUserId,
            UUID groupId,
            UpdateGroupDetailsRequest request) {
        UUID userId = parseUserId(authenticatedUserId);
        Group group = requireGroup(groupId);
        GroupMember membership = requirePermission(groupId, userId, GroupAdminPermission.EDIT_GROUP);

        BigDecimal paymentAmount = request.defaultPaymentAmount();
        String pixKey = normalizeOptional(request.defaultPixKey());
        boolean paymentFieldsProvided = paymentAmount != null || pixKey != null;
        if (request.defaultPaymentEnabled() != null || paymentFieldsProvided) {
            if (Boolean.FALSE.equals(request.defaultPaymentEnabled())) {
                paymentAmount = null;
                pixKey = null;
            } else if (paymentAmount == null || pixKey == null) {
                throw new InvalidPaymentConfigurationException();
            }
            group.updatePaymentDetails(paymentAmount, pixKey);
        }

        group.updateOptionalDetails(
                normalizeOptional(request.city()),
                normalizeOptional(request.mascot()),
                normalizeOptional(request.venue()));

        groupScheduleRepository.deleteAllByGroupId(groupId);
        groupScheduleRepository.flush();
        List<GroupSchedule> schedules = uniqueSchedules(groupId, request.schedules());
        if (!schedules.isEmpty()) {
            groupScheduleRepository.saveAll(schedules);
        }

        return toResponse(group, membership.getRole(), membership.getPermissions(), schedules);
    }

    @Transactional
    public GroupResponse updatePhoto(String authenticatedUserId, UUID groupId, MultipartFile photo) {
        UUID userId = parseUserId(authenticatedUserId);
        Group group = requireGroup(groupId);
        GroupMember membership = requirePermission(groupId, userId, GroupAdminPermission.EDIT_GROUP);
        validatePhoto(photo);

        try {
            String photoUrl = groupPhotoStorage.upload(groupId, photo.getBytes());
            group.updatePhotoUrl(photoUrl);
            return toResponse(
                    group,
                    membership.getRole(),
                    membership.getPermissions(),
                    groupScheduleRepository.findAllByGroupId(groupId));
        } catch (IOException exception) {
            throw new PhotoUploadFailedException(exception);
        }
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
                    membership.getPermissions(),
                    groupScheduleRepository.findAllByGroupId(group.getId())));
        }
        return result;
    }

    private void validatePhoto(MultipartFile photo) {
        String contentType = photo.getContentType();
        if (photo.isEmpty()
                || photo.getSize() > MAX_PHOTO_BYTES
                || contentType == null
                || !contentType.toLowerCase().startsWith("image/")) {
            throw new InvalidGroupPhotoException();
        }
    }

    private User requireUser(String authenticatedUserId) {
        UUID userId = parseUserId(authenticatedUserId);
        return userRepository.findById(userId).orElseThrow(GroupUserNotFoundException::new);
    }

    private Group requireGroup(UUID groupId) {
        return groupRepository.findById(groupId).orElseThrow(GroupNotFoundException::new);
    }

    private GroupMember requirePermission(
            UUID groupId,
            UUID userId,
            GroupAdminPermission permission) {
        GroupMember membership = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupAccessDeniedException::new);
        if (!membership.hasPermission(permission)) {
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

    private GroupResponse toResponse(
            Group group,
            GroupRole role,
            Set<GroupAdminPermission> permissions,
            List<GroupSchedule> schedules) {
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
                group.getDefaultPaymentAmount(),
                group.getDefaultPixKey(),
                scheduleResponses,
                role,
                permissions,
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

    public static final class InvalidGroupPhotoException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class PhotoStorageNotConfiguredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class PhotoUploadFailedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public PhotoUploadFailedException() {
        }

        public PhotoUploadFailedException(Throwable cause) {
            super(cause);
        }
    }

    public static final class InvalidPaymentConfigurationException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
