package com.onze.api.match;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.match.MatchService.MatchNotFoundException;
import com.onze.api.match.LiveMatchModels.LiveMatchStateResponse;
import com.onze.api.match.LiveMatchModels.LiveMatchSnapshotResponse;
import com.onze.api.match.LiveMatchModels.LiveScoreSideResponse;
import com.onze.api.match.LiveMatchModels.CreateGoalEventResponse;
import com.onze.api.match.LiveMatchModels.GoalEventResponse;
import com.onze.api.match.LiveMatchModels.CardEventResponse;
import com.onze.api.match.LiveMatchModels.CreateCardEventResponse;
import com.onze.api.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class LiveMatchService {
    public static final Duration MAX_MATCH_DURATION = Duration.ofHours(3);
    private final FootballMatchRepository matchRepository;
    private final GroupMemberRepository memberRepository;
    private final LiveMatchScoreRepository scoreRepository;
    private final MatchTeamImageRepository teamImageRepository;
    private final MatchTeamAssignmentRepository assignmentRepository;
    private final MatchGoalEventRepository goalEventRepository;
    private final MatchCardEventRepository cardEventRepository;
    private final UserRepository userRepository;
    private final MatchGuestRepository guestRepository;
    private final MatchRentalGoalkeeperRepository rentalGoalkeeperRepository;
    private final MatchNotificationQueue notificationQueue;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public LiveMatchService(FootballMatchRepository matchRepository, GroupMemberRepository memberRepository,
            LiveMatchScoreRepository scoreRepository, MatchTeamImageRepository teamImageRepository,
            MatchTeamAssignmentRepository assignmentRepository,
            MatchGoalEventRepository goalEventRepository, MatchCardEventRepository cardEventRepository,
            UserRepository userRepository,
            MatchGuestRepository guestRepository,
            MatchRentalGoalkeeperRepository rentalGoalkeeperRepository,
            MatchNotificationQueue notificationQueue,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.matchRepository = matchRepository;
        this.memberRepository = memberRepository;
        this.scoreRepository = scoreRepository;
        this.teamImageRepository = teamImageRepository;
        this.assignmentRepository = assignmentRepository;
        this.goalEventRepository = goalEventRepository;
        this.cardEventRepository = cardEventRepository;
        this.userRepository = userRepository;
        this.guestRepository = guestRepository;
        this.rentalGoalkeeperRepository = rentalGoalkeeperRepository;
        this.notificationQueue = notificationQueue;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void start(String authenticatedUserId, UUID matchId) {
        FootballMatch match = managedMatch(authenticatedUserId, matchId);
        if (match.getStatus() != MatchStatus.SCHEDULED) throw new InvalidLiveMatchTransitionException();
        match.start(Instant.now(clock));
        initializeScoreboard(match, matchId);
        enqueueLiveNotification(matchId, match, MatchNotificationType.LIVE_MATCH_STARTED);
        publish(matchId, match, LiveMatchChangeType.MATCH_STARTED);
    }

    @Transactional
    public void finish(String authenticatedUserId, UUID matchId) {
        FootballMatch match = managedMatch(authenticatedUserId, matchId);
        if (match.getStatus() != MatchStatus.IN_PROGRESS) throw new InvalidLiveMatchTransitionException();
        match.finish(Instant.now(clock));
        enqueueLiveNotification(matchId, match, MatchNotificationType.LIVE_MATCH_FINISHED);
        publish(matchId, match, LiveMatchChangeType.MATCH_FINISHED);
    }

    @Transactional
    public void reset(String authenticatedUserId, UUID matchId) {
        FootballMatch match = managedMatch(authenticatedUserId, matchId);
        if (match.getStatus() != MatchStatus.IN_PROGRESS) throw new InvalidLiveMatchTransitionException();
        scoreRepository.deleteAllByMatchId(matchId);
        goalEventRepository.deleteAllByMatchId(matchId);
        cardEventRepository.deleteAllByMatchId(matchId);
        match.resetLiveMatch();
        publish(matchId, match, LiveMatchChangeType.MATCH_RESET);
    }

    @Transactional
    public LiveMatchStateResponse get(String authenticatedUserId, UUID matchId) {
        return getIfChanged(authenticatedUserId, matchId, null).orElseThrow();
    }

    @Transactional
    public Optional<LiveMatchStateResponse> getIfChanged(
            String authenticatedUserId, UUID matchId, Long knownVersion) {
        Access access = accessibleMatch(authenticatedUserId, matchId, false);
        if (finishIfExpired(access.match(), Instant.now(clock))) {
            enqueueLiveNotification(matchId, access.match(), MatchNotificationType.LIVE_MATCH_FINISHED);
            publish(matchId, access.match(), LiveMatchChangeType.MATCH_FINISHED);
        }
        if (knownVersion != null && knownVersion == access.match().getLiveVersion()) {
            return Optional.empty();
        }
        return Optional.of(response(matchId, access.match(), access.member()));
    }

    @Transactional
    public LiveMatchStateResponse updateScore(
            String authenticatedUserId, UUID matchId, int sideNumber, int score) {
        Access access = accessibleMatch(authenticatedUserId, matchId, true);
        FootballMatch match = access.match();
        finishIfExpired(match, Instant.now(clock));
        if (match.getStatus() != MatchStatus.IN_PROGRESS) throw new InvalidLiveMatchTransitionException();
        if (sideNumber < 1 || sideNumber > sideCount(match) || score < 0) {
            throw new InvalidLiveMatchScoreException();
        }
        initializeScoreboard(match, matchId);
        LiveMatchScore storedScore = scoreRepository.findByMatchIdAndSideNumber(matchId, sideNumber)
                .orElseThrow(InvalidLiveMatchScoreException::new);
        if (storedScore.getScore() != score) {
            storedScore.update(score);
            match.liveStateChanged();
            publish(matchId, match, LiveMatchChangeType.SCORE_UPDATED);
        }
        return response(matchId, match, access.member());
    }

    @Transactional
    public CreateGoalEventResponse createGoal(String authenticatedUserId, UUID matchId,
            UUID scorerAssignmentId, UUID assistAssignmentId, boolean penalty) {
        Access access = accessibleMatch(authenticatedUserId, matchId, true);
        FootballMatch match = access.match();
        finishIfExpired(match, Instant.now(clock));
        if (match.getStatus() != MatchStatus.IN_PROGRESS || match.getStartedAt() == null) {
            throw new InvalidLiveMatchTransitionException();
        }
        if (penalty && assistAssignmentId != null) throw new InvalidGoalEventException();

        MatchTeamAssignment scorer = assignmentRepository.findByIdAndMatchId(scorerAssignmentId, matchId)
                .orElseThrow(InvalidGoalEventException::new);
        if (isSentOff(matchId, scorer.getId())) throw new InvalidGoalEventException();
        MatchTeamAssignment assist = null;
        if (assistAssignmentId != null) {
            assist = assignmentRepository.findByIdAndMatchId(assistAssignmentId, matchId)
                    .orElseThrow(InvalidGoalEventException::new);
            if (assist.getId().equals(scorer.getId()) || assist.getTeamNumber() != scorer.getTeamNumber()) {
                throw new InvalidGoalEventException();
            }
            if (isSentOff(matchId, assist.getId())) throw new InvalidGoalEventException();
        }
        if (scorer.getTeamNumber() < 1 || scorer.getTeamNumber() > sideCount(match)) {
            throw new InvalidGoalEventException();
        }

        initializeScoreboard(match, matchId);
        LiveMatchScore score = scoreRepository.findByMatchIdAndSideNumber(matchId, scorer.getTeamNumber())
                .orElseThrow(InvalidLiveMatchScoreException::new);
        Instant now = Instant.now(clock);
        long elapsedSeconds = elapsedSeconds(match, now);
        MatchGoalEvent event = goalEventRepository.save(new MatchGoalEvent(
                matchId, scorer, participantName(matchId, scorer), assist,
                assist == null ? null : participantName(matchId, assist),
                penalty, elapsedSeconds, access.member().getUserId(), now));
        score.update(score.getScore() + 1);
        match.liveStateChanged();
        enqueueLiveNotification(matchId, match, MatchNotificationType.LIVE_MATCH_GOAL);
        publish(matchId, match, LiveMatchChangeType.GOAL_ADDED);
        return new CreateGoalEventResponse(goalResponse(event), response(matchId, match, access.member()));
    }

    @Transactional
    public CreateCardEventResponse createCard(String authenticatedUserId, UUID matchId,
            UUID playerAssignmentId, MatchCardType cardType) {
        Access access = accessibleMatch(authenticatedUserId, matchId, true);
        FootballMatch match = access.match();
        Instant now = Instant.now(clock);
        finishIfExpired(match, now);
        if (match.getStatus() != MatchStatus.IN_PROGRESS || match.getStartedAt() == null) {
            throw new InvalidLiveMatchTransitionException();
        }
        MatchTeamAssignment player = assignmentRepository.findByIdAndMatchId(playerAssignmentId, matchId)
                .orElseThrow(InvalidCardEventException::new);
        if (player.getTeamNumber() < 1 || player.getTeamNumber() > sideCount(match)) {
            throw new InvalidCardEventException();
        }
        boolean directRed = cardEventRepository.existsByMatchIdAndPlayerAssignmentIdAndCardType(
                matchId, player.getId(), MatchCardType.RED);
        long yellowCards = cardEventRepository.countByMatchIdAndPlayerAssignmentIdAndCardType(
                matchId, player.getId(), MatchCardType.YELLOW);
        if (directRed || yellowCards >= 2) throw new InvalidCardEventException();
        MatchCardEvent event = cardEventRepository.save(new MatchCardEvent(matchId, player,
                participantName(matchId, player), cardType, elapsedSeconds(match, now),
                access.member().getUserId(), now));
        match.liveStateChanged();
        MatchNotificationType notificationType = cardType == MatchCardType.RED
                ? MatchNotificationType.LIVE_MATCH_RED_CARD
                : yellowCards == 1
                        ? MatchNotificationType.LIVE_MATCH_SECOND_YELLOW_CARD
                        : MatchNotificationType.LIVE_MATCH_YELLOW_CARD;
        enqueueLiveNotification(matchId, match, notificationType);
        publish(matchId, match, LiveMatchChangeType.CARD_ADDED);
        return new CreateCardEventResponse(cardResponse(event), response(matchId, match, access.member()));
    }

    @Transactional
    public LiveMatchStateResponse deleteGoal(String authenticatedUserId, UUID matchId, UUID eventId) {
        Access access = accessibleMatch(authenticatedUserId, matchId, true);
        FootballMatch match = access.match();
        finishIfExpired(match, Instant.now(clock));
        if (match.getStatus() != MatchStatus.IN_PROGRESS || match.getStartedAt() == null) {
            throw new InvalidLiveMatchTransitionException();
        }
        MatchGoalEvent event = goalEventRepository.findByIdAndMatchId(eventId, matchId)
                .orElseThrow(InvalidGoalEventException::new);
        LiveMatchScore score = scoreRepository.findByMatchIdAndSideNumber(matchId, event.getSideNumber())
                .orElseThrow(InvalidLiveMatchScoreException::new);
        score.update(Math.max(0, score.getScore() - 1));
        goalEventRepository.delete(event);
        match.liveStateChanged();
        publish(matchId, match, LiveMatchChangeType.GOAL_REMOVED);
        return response(matchId, match, access.member());
    }

    @Transactional
    public LiveMatchStateResponse deleteCard(String authenticatedUserId, UUID matchId, UUID eventId) {
        Access access = accessibleMatch(authenticatedUserId, matchId, true);
        FootballMatch match = access.match();
        finishIfExpired(match, Instant.now(clock));
        if (match.getStatus() != MatchStatus.IN_PROGRESS || match.getStartedAt() == null) {
            throw new InvalidLiveMatchTransitionException();
        }
        MatchCardEvent event = cardEventRepository.findByIdAndMatchId(eventId, matchId)
                .orElseThrow(InvalidCardEventException::new);
        cardEventRepository.delete(event);
        match.liveStateChanged();
        publish(matchId, match, LiveMatchChangeType.CARD_REMOVED);
        return response(matchId, match, access.member());
    }

    @Transactional
    public void finishExpiredMatches() {
        Instant now = Instant.now(clock);
        Instant cutoff = now.minus(MAX_MATCH_DURATION);
        for (FootballMatch candidate : matchRepository
                .findAllByStatusAndStartedAtLessThanEqualOrderByStartedAtAsc(MatchStatus.IN_PROGRESS, cutoff)) {
            matchRepository.findByIdForUpdate(candidate.getId()).ifPresent(match -> {
                if (finishIfExpired(match, now)) {
                    enqueueLiveNotification(candidate.getId(), match,
                            MatchNotificationType.LIVE_MATCH_FINISHED);
                    publish(candidate.getId(), match, LiveMatchChangeType.MATCH_FINISHED);
                }
            });
        }
    }

    private boolean finishIfExpired(FootballMatch match, Instant now) {
        if (match.getStatus() == MatchStatus.IN_PROGRESS && match.getStartedAt() != null) {
            Instant deadline = match.getStartedAt().plus(MAX_MATCH_DURATION);
            if (!now.isBefore(deadline)) {
                match.finish(deadline);
                return true;
            }
        }
        return false;
    }

    private long elapsedSeconds(FootballMatch match, Instant now) {
        return Math.min(MAX_MATCH_DURATION.toSeconds(),
                Math.max(0, Duration.between(match.getStartedAt(), now).getSeconds()));
    }

    private boolean isSentOff(UUID matchId, UUID playerAssignmentId) {
        return cardEventRepository.existsByMatchIdAndPlayerAssignmentIdAndCardType(
                matchId, playerAssignmentId, MatchCardType.RED)
                || cardEventRepository.countByMatchIdAndPlayerAssignmentIdAndCardType(
                        matchId, playerAssignmentId, MatchCardType.YELLOW) >= 2;
    }

    private GoalEventResponse goalResponse(MatchGoalEvent event) {
        return new GoalEventResponse(event.getId(), event.getMatchId(), event.getSideNumber(),
                event.getScorerAssignmentId(), event.getScorerParticipantType(), event.getScorerParticipantId(),
                event.getScorerDisplayName(), event.getAssistAssignmentId(), event.getAssistParticipantType(),
                event.getAssistParticipantId(), event.getAssistDisplayName(),
                event.isPenalty(), event.getElapsedSeconds(), event.getCreatedAt());
    }

    private CardEventResponse cardResponse(MatchCardEvent event) {
        return new CardEventResponse(event.getId(), event.getMatchId(), event.getSideNumber(),
                event.getPlayerAssignmentId(), event.getPlayerParticipantType(), event.getPlayerParticipantId(),
                event.getPlayerDisplayName(), event.getCardType(), event.getElapsedSeconds(), event.getCreatedAt());
    }

    private String participantName(UUID matchId, MatchTeamAssignment assignment) {
        return switch (assignment.getParticipantType()) {
            case MEMBER -> userRepository.findById(assignment.getParticipantId())
                    .map(user -> user.getDisplayName()).orElseThrow(InvalidGoalEventException::new);
            case GUEST -> guestRepository.findByIdAndMatchId(assignment.getParticipantId(), matchId)
                    .map(MatchGuest::getDisplayName).orElseThrow(InvalidGoalEventException::new);
            case RENTAL_GOALKEEPER -> rentalGoalkeeperRepository
                    .findByIdAndMatchId(assignment.getParticipantId(), matchId)
                    .map(MatchRentalGoalkeeper::getDisplayName)
                    .orElseThrow(InvalidGoalEventException::new);
        };
    }

    private FootballMatch managedMatch(String authenticatedUserId, UUID matchId) {
        return accessibleMatch(authenticatedUserId, matchId, true).match();
    }

    private Access accessibleMatch(String authenticatedUserId, UUID matchId, boolean lock) {
        UUID userId;
        try { userId = UUID.fromString(authenticatedUserId); }
        catch (IllegalArgumentException exception) { throw new GroupUserNotFoundException(); }
        FootballMatch match = (lock ? matchRepository.findByIdForUpdate(matchId) : matchRepository.findById(matchId))
                .orElseThrow(MatchNotFoundException::new);
        GroupMember member = memberRepository.findByGroupIdAndUserId(match.getGroupId(), userId)
                .orElseThrow(GroupAccessDeniedException::new);
        if (lock && !member.hasPermission(GroupAdminPermission.SCHEDULE_GAMES)) throw new GroupAccessDeniedException();
        return new Access(match, member);
    }

    private void initializeScoreboard(FootballMatch match, UUID matchId) {
        if (!scoreRepository.findAllByMatchIdOrderBySideNumberAsc(matchId).isEmpty()) return;
        for (int sideNumber = 1; sideNumber <= sideCount(match); sideNumber++) {
            scoreRepository.save(new LiveMatchScore(matchId, sideNumber));
        }
    }

    private LiveMatchStateResponse response(UUID matchId, FootballMatch match, GroupMember member) {
        LiveMatchSnapshotResponse snapshot = snapshot(matchId, match);
        return new LiveMatchStateResponse(
                snapshot.matchId(), snapshot.status(), snapshot.startedAt(), snapshot.finishedAt(),
                snapshot.version(), snapshot.scores(), snapshot.goalEvents(), snapshot.cardEvents(),
                member.hasPermission(GroupAdminPermission.SCHEDULE_GAMES));
    }

    @Transactional(readOnly = true)
    public Optional<LiveMatchSnapshotResponse> getSnapshot(UUID matchId) {
        return matchRepository.findById(matchId).map(match -> snapshot(matchId, match));
    }

    private LiveMatchSnapshotResponse snapshot(UUID matchId, FootballMatch match) {
        Map<Integer, String> imagesByTeam = teamImageRepository
                .findAllByMatchIdOrderByTeamNumberAsc(matchId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        MatchTeamImage::getTeamNumber,
                        MatchTeamImage::getImageUrl));
        List<LiveScoreSideResponse> scores = scoreRepository.findAllByMatchIdOrderBySideNumberAsc(matchId)
                .stream().map(item -> new LiveScoreSideResponse(
                        item.getSideNumber(),
                        item.getScore(),
                        imagesByTeam.get(item.getSideNumber())))
                .toList();
        if (scores.isEmpty() && match.getStatus() != MatchStatus.SCHEDULED) {
            scores = java.util.stream.IntStream.rangeClosed(1, sideCount(match))
                    .mapToObj(side -> new LiveScoreSideResponse(side, 0, imagesByTeam.get(side)))
                    .toList();
        }
        List<GoalEventResponse> goalEvents = goalEventRepository
                .findAllByMatchIdOrderByElapsedSecondsDescCreatedAtDesc(matchId)
                .stream().map(this::goalResponse).toList();
        List<CardEventResponse> cardEvents = cardEventRepository
                .findAllByMatchIdOrderByElapsedSecondsDescCreatedAtDesc(matchId)
                .stream().map(this::cardResponse).toList();
        return new LiveMatchSnapshotResponse(matchId, match.getStatus(), match.getStartedAt(),
                match.getFinishedAt(), match.getLiveVersion(), scores, goalEvents, cardEvents);
    }

    private void enqueueLiveNotification(
            UUID matchId,
            FootballMatch match,
            MatchNotificationType notificationType) {
        notificationQueue.enqueue(
                matchId,
                null,
                notificationType,
                matchId + ":" + notificationType + ":" + match.getLiveVersion(),
                Instant.now(clock));
    }

    private void publish(UUID matchId, FootballMatch match, LiveMatchChangeType type) {
        eventPublisher.publishEvent(new LiveMatchChangedEvent(
                matchId, match.getGroupId(), match.getLiveVersion(), type));
    }

    private int sideCount(FootballMatch match) {
        return match.getMatchType() == MatchType.INTERNAL ? match.getTeamCount() : 2;
    }

    private record Access(FootballMatch match, GroupMember member) { }

    public static final class InvalidLiveMatchTransitionException extends RuntimeException { }
    public static final class InvalidLiveMatchScoreException extends RuntimeException { }
    public static final class InvalidGoalEventException extends RuntimeException { }
    public static final class InvalidCardEventException extends RuntimeException { }
}
