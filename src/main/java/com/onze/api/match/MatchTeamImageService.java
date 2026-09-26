package com.onze.api.match;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.Group;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRepository;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupNotFoundException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.match.MatchService.MatchNotFoundException;
import com.onze.api.match.LiveMatchModels.TeamIdentityNameRequest;
import com.onze.api.match.TeamModels.MatchTeamImageResponse;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MatchTeamImageService {
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;

    private final FootballMatchRepository matchRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupRepository groupRepository;
    private final GroupTeamIdentityRepository groupIdentityRepository;
    private final MatchTeamImageRepository imageRepository;
    private final MatchTeamImageStorage imageStorage;
    private final ApplicationEventPublisher eventPublisher;

    public MatchTeamImageService(
            FootballMatchRepository matchRepository,
            GroupMemberRepository memberRepository,
            GroupRepository groupRepository,
            GroupTeamIdentityRepository groupIdentityRepository,
            MatchTeamImageRepository imageRepository,
            MatchTeamImageStorage imageStorage,
            ApplicationEventPublisher eventPublisher) {
        this.matchRepository = matchRepository;
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
        this.groupIdentityRepository = groupIdentityRepository;
        this.imageRepository = imageRepository;
        this.imageStorage = imageStorage;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<MatchTeamImageResponse> list(String authenticatedUserId, UUID matchId) {
        FootballMatch match = matchRepository.findById(matchId)
                .orElseThrow(MatchNotFoundException::new);
        requireMembership(authenticatedUserId, match.getGroupId(), false);
        return identities(match);
    }

    @Transactional
    public MatchTeamImageResponse upload(
            String authenticatedUserId,
            UUID matchId,
            int teamNumber,
            MultipartFile image) {
        FootballMatch match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(MatchNotFoundException::new);
        requireMembership(authenticatedUserId, match.getGroupId(), true);
        if (match.getStatus() == MatchStatus.FINISHED || match.getStatus() == MatchStatus.CANCELLED) {
            throw new TeamImageLockedException();
        }
        if (teamNumber < 1 || teamNumber > sideCount(match)) {
            throw new InvalidTeamImageException();
        }
        validate(image);

        try {
            Group group = groupRepository.findById(match.getGroupId())
                    .orElseThrow(GroupNotFoundException::new);
            String imageUrl = imageStorage.upload(
                    match.getGroupId(), match.getMatchType(), teamNumber, image.getBytes());
            GroupTeamIdentity groupIdentity = groupIdentityRepository
                    .findByGroupIdAndMatchTypeAndTeamNumber(
                            match.getGroupId(), match.getMatchType(), teamNumber)
                    .orElseGet(() -> new GroupTeamIdentity(
                            match.getGroupId(),
                            match.getMatchType(),
                            teamNumber,
                            defaultName(match, group, teamNumber),
                            null));
            groupIdentity.updateImageUrl(imageUrl);
            GroupTeamIdentity savedDefault = groupIdentityRepository.save(groupIdentity);
            if (match.getStatus() == MatchStatus.IN_PROGRESS) {
                MatchTeamImage stored = imageRepository.findByMatchIdAndTeamNumber(matchId, teamNumber)
                        .orElseGet(() -> new MatchTeamImage(
                                matchId,
                                teamNumber,
                                savedDefault.getTeamName(),
                                imageUrl));
                stored.updateIdentity(savedDefault.getTeamName(), imageUrl);
                imageRepository.save(stored);
                match.liveStateChanged();
                eventPublisher.publishEvent(new LiveMatchChangedEvent(
                        matchId,
                        match.getGroupId(),
                        match.getLiveVersion(),
                        LiveMatchChangeType.TEAM_IMAGE_UPDATED));
            }
            return new MatchTeamImageResponse(
                    teamNumber,
                    savedDefault.getTeamName(),
                    imageUrl);
        } catch (IOException exception) {
            throw new TeamImageUploadFailedException(exception);
        }
    }

    @Transactional(readOnly = true)
    public List<MatchTeamImageResponse> identities(FootballMatch match) {
        Group group = groupRepository.findById(match.getGroupId())
                .orElseThrow(GroupNotFoundException::new);
        Map<Integer, GroupTeamIdentity> defaults = groupIdentityRepository
                .findAllByGroupIdAndMatchTypeOrderByTeamNumberAsc(
                        match.getGroupId(), match.getMatchType())
                .stream()
                .collect(Collectors.toMap(GroupTeamIdentity::getTeamNumber, item -> item));
        Map<Integer, MatchTeamImage> snapshots = match.getStatus() == MatchStatus.SCHEDULED
                ? Map.of()
                : imageRepository.findAllByMatchIdOrderByTeamNumberAsc(match.getId()).stream()
                        .collect(Collectors.toMap(MatchTeamImage::getTeamNumber, item -> item));

        return java.util.stream.IntStream.rangeClosed(1, sideCount(match))
                .mapToObj(teamNumber -> {
                    MatchTeamImage snapshot = snapshots.get(teamNumber);
                    if (snapshot != null) return response(snapshot);
                    GroupTeamIdentity stored = defaults.get(teamNumber);
                    return new MatchTeamImageResponse(
                            teamNumber,
                            stored == null
                                    ? defaultName(match, group, teamNumber)
                                    : stored.getTeamName(),
                            stored != null && stored.getImageUrl() != null
                                    ? stored.getImageUrl()
                                    : group.getPhotoUrl());
                })
                .toList();
    }

    @Transactional
    public void snapshotForStart(
            FootballMatch match,
            List<TeamIdentityNameRequest> requestedNames) {
        Group group = groupRepository.findById(match.getGroupId())
                .orElseThrow(GroupNotFoundException::new);
        int sides = sideCount(match);
        Map<Integer, String> names = new HashMap<>();
        if (requestedNames != null) {
            for (TeamIdentityNameRequest requested : requestedNames) {
                if (requested == null
                        || requested.teamNumber() == null
                        || requested.teamNumber() < 1
                        || requested.teamNumber() > sides
                        || requested.name() == null
                        || requested.name().trim().isEmpty()
                        || requested.name().trim().length() > 80
                        || names.put(requested.teamNumber(), requested.name().trim()) != null) {
                    throw new InvalidTeamIdentityException();
                }
            }
        }

        Map<Integer, GroupTeamIdentity> defaults = groupIdentityRepository
                .findAllByGroupIdAndMatchTypeOrderByTeamNumberAsc(
                        match.getGroupId(), match.getMatchType())
                .stream()
                .collect(Collectors.toMap(GroupTeamIdentity::getTeamNumber, item -> item));

        for (int teamNumber = 1; teamNumber <= sides; teamNumber++) {
            GroupTeamIdentity storedDefault = defaults.get(teamNumber);
            if (storedDefault == null) {
                storedDefault = new GroupTeamIdentity(
                        match.getGroupId(),
                        match.getMatchType(),
                        teamNumber,
                        defaultName(match, group, teamNumber),
                        null);
            }
            String requestedName = names.get(teamNumber);
            if (requestedName != null) storedDefault.updateName(requestedName);
            GroupTeamIdentity savedDefault = groupIdentityRepository.save(storedDefault);
            String effectiveImage = savedDefault.getImageUrl() != null
                    ? savedDefault.getImageUrl()
                    : group.getPhotoUrl();

            MatchTeamImage snapshot = imageRepository
                    .findByMatchIdAndTeamNumber(match.getId(), teamNumber)
                    .orElseGet(() -> new MatchTeamImage(
                            match.getId(),
                            teamNumber,
                            savedDefault.getTeamName(),
                            effectiveImage));
            snapshot.updateIdentity(savedDefault.getTeamName(), effectiveImage);
            imageRepository.save(snapshot);
        }
    }

    private void validate(MultipartFile image) {
        String contentType = image.getContentType();
        if (image.isEmpty()
                || image.getSize() > MAX_IMAGE_BYTES
                || contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new InvalidTeamImageException();
        }
    }

    private GroupMember requireMembership(String authenticatedUserId, UUID groupId, boolean manage) {
        UUID userId;
        try {
            userId = UUID.fromString(authenticatedUserId);
        } catch (IllegalArgumentException exception) {
            throw new GroupUserNotFoundException();
        }
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(GroupAccessDeniedException::new);
        if (manage && !member.hasPermission(GroupAdminPermission.SCHEDULE_GAMES)) {
            throw new GroupAccessDeniedException();
        }
        return member;
    }

    private MatchTeamImageResponse response(MatchTeamImage image) {
        return new MatchTeamImageResponse(
                image.getTeamNumber(),
                image.getTeamName(),
                image.getImageUrl());
    }

    private String defaultName(FootballMatch match, Group group, int teamNumber) {
        if (match.getMatchType() == MatchType.VERSUS_EXTERNAL) {
            return teamNumber == 1 ? group.getName() : "Adversário";
        }
        return "Time " + teamNumber;
    }

    private int sideCount(FootballMatch match) {
        return match.getMatchType() == MatchType.INTERNAL ? match.getTeamCount() : 2;
    }

    public static final class InvalidTeamImageException extends RuntimeException { }
    public static final class InvalidTeamIdentityException extends RuntimeException { }
    public static final class TeamImageLockedException extends RuntimeException { }
    public static final class TeamImageStorageNotConfiguredException extends RuntimeException { }
    public static final class TeamImageUploadFailedException extends RuntimeException {
        public TeamImageUploadFailedException() { }
        public TeamImageUploadFailedException(Throwable cause) { super(cause); }
    }
}
