package com.onze.api.match;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.match.MatchService.MatchNotFoundException;
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
    private final MatchTeamImageRepository imageRepository;
    private final MatchTeamImageStorage imageStorage;
    private final ApplicationEventPublisher eventPublisher;

    public MatchTeamImageService(
            FootballMatchRepository matchRepository,
            GroupMemberRepository memberRepository,
            MatchTeamImageRepository imageRepository,
            MatchTeamImageStorage imageStorage,
            ApplicationEventPublisher eventPublisher) {
        this.matchRepository = matchRepository;
        this.memberRepository = memberRepository;
        this.imageRepository = imageRepository;
        this.imageStorage = imageStorage;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<MatchTeamImageResponse> list(String authenticatedUserId, UUID matchId) {
        FootballMatch match = matchRepository.findById(matchId)
                .orElseThrow(MatchNotFoundException::new);
        requireMembership(authenticatedUserId, match.getGroupId(), false);
        return imageRepository.findAllByMatchIdOrderByTeamNumberAsc(matchId).stream()
                .map(this::response)
                .toList();
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
            String imageUrl = imageStorage.upload(matchId, teamNumber, image.getBytes());
            MatchTeamImage stored = imageRepository.findByMatchIdAndTeamNumber(matchId, teamNumber)
                    .map(existing -> {
                        existing.updateImageUrl(imageUrl);
                        return existing;
                    })
                    .orElseGet(() -> new MatchTeamImage(matchId, teamNumber, imageUrl));
            MatchTeamImage saved = imageRepository.save(stored);
            if (match.getStatus() == MatchStatus.IN_PROGRESS) {
                match.liveStateChanged();
                eventPublisher.publishEvent(new LiveMatchChangedEvent(
                        matchId,
                        match.getGroupId(),
                        match.getLiveVersion(),
                        LiveMatchChangeType.TEAM_IMAGE_UPDATED));
            }
            return response(saved);
        } catch (IOException exception) {
            throw new TeamImageUploadFailedException(exception);
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
        return new MatchTeamImageResponse(image.getTeamNumber(), image.getImageUrl());
    }

    private int sideCount(FootballMatch match) {
        return match.getMatchType() == MatchType.INTERNAL ? match.getTeamCount() : 2;
    }

    public static final class InvalidTeamImageException extends RuntimeException { }
    public static final class TeamImageLockedException extends RuntimeException { }
    public static final class TeamImageStorageNotConfiguredException extends RuntimeException { }
    public static final class TeamImageUploadFailedException extends RuntimeException {
        public TeamImageUploadFailedException() { }
        public TeamImageUploadFailedException(Throwable cause) { super(cause); }
    }
}
