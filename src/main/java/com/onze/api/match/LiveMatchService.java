package com.onze.api.match;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.match.MatchService.MatchNotFoundException;
import com.onze.api.match.LiveMatchModels.LiveMatchStateResponse;
import com.onze.api.match.LiveMatchModels.LiveMatchSnapshotResponse;
import com.onze.api.match.LiveMatchModels.LiveScoreSideResponse;
import com.onze.api.match.LiveMatchModels.TeamIdentityNameRequest;
import com.onze.api.match.LiveMatchModels.CreateGoalEventResponse;
import com.onze.api.match.LiveMatchModels.GoalEventResponse;
import com.onze.api.match.LiveMatchModels.CardEventResponse;
import com.onze.api.match.LiveMatchModels.CreateCardEventResponse;
import com.onze.api.match.LiveMatchModels.MatchPeriodResponse;
import com.onze.api.match.LiveMatchModels.PenaltyAttemptResponse;
import com.onze.api.match.LiveMatchModels.PenaltyShootoutResponse;
import com.onze.api.match.LiveMatchModels.PenaltyTakerRequest;
import com.onze.api.match.LiveMatchModels.PenaltyTakerResponse;
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
    private final MatchTeamImageService teamIdentityService;
    private final MatchTeamAssignmentRepository assignmentRepository;
    private final MatchGoalEventRepository goalEventRepository;
    private final MatchCardEventRepository cardEventRepository;
    private final MatchPeriodRepository periodRepository;
    private final MatchPenaltyShootoutRepository penaltyShootoutRepository;
    private final MatchPenaltyTakerRepository penaltyTakerRepository;
    private final MatchPenaltyAttemptRepository penaltyAttemptRepository;
    private final UserRepository userRepository;
    private final MatchGuestRepository guestRepository;
    private final MatchRentalGoalkeeperRepository rentalGoalkeeperRepository;
    private final MatchNotificationQueue notificationQueue;
    private final WeeklyMatchWindowService weeklyMatchWindowService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public LiveMatchService(FootballMatchRepository matchRepository, GroupMemberRepository memberRepository,
            LiveMatchScoreRepository scoreRepository, MatchTeamImageRepository teamImageRepository,
            MatchTeamImageService teamIdentityService,
            MatchTeamAssignmentRepository assignmentRepository,
            MatchGoalEventRepository goalEventRepository, MatchCardEventRepository cardEventRepository,
            MatchPeriodRepository periodRepository,
            MatchPenaltyShootoutRepository penaltyShootoutRepository,
            MatchPenaltyTakerRepository penaltyTakerRepository,
            MatchPenaltyAttemptRepository penaltyAttemptRepository,
            UserRepository userRepository,
            MatchGuestRepository guestRepository,
            MatchRentalGoalkeeperRepository rentalGoalkeeperRepository,
            MatchNotificationQueue notificationQueue,
            WeeklyMatchWindowService weeklyMatchWindowService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.matchRepository = matchRepository;
        this.memberRepository = memberRepository;
        this.scoreRepository = scoreRepository;
        this.teamImageRepository = teamImageRepository;
        this.teamIdentityService = teamIdentityService;
        this.assignmentRepository = assignmentRepository;
        this.goalEventRepository = goalEventRepository;
        this.cardEventRepository = cardEventRepository;
        this.periodRepository = periodRepository;
        this.penaltyShootoutRepository = penaltyShootoutRepository;
        this.penaltyTakerRepository = penaltyTakerRepository;
        this.penaltyAttemptRepository = penaltyAttemptRepository;
        this.userRepository = userRepository;
        this.guestRepository = guestRepository;
        this.rentalGoalkeeperRepository = rentalGoalkeeperRepository;
        this.notificationQueue = notificationQueue;
        this.weeklyMatchWindowService = weeklyMatchWindowService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void start(String authenticatedUserId, UUID matchId) {
        start(authenticatedUserId, matchId, List.of());
    }

    @Transactional
    public void start(
            String authenticatedUserId,
            UUID matchId,
            List<TeamIdentityNameRequest> teamNames) {
        FootballMatch match = managedMatch(authenticatedUserId, matchId);
        if (match.getStatus() != MatchStatus.SCHEDULED) throw new InvalidLiveMatchTransitionException();
        teamIdentityService.snapshotForStart(match, teamNames);
        Instant now = Instant.now(clock);
        match.start(now);
        initializeScoreboard(match, matchId);
        if (match.isPeriodsEnabled()) {
            periodRepository.save(new MatchPeriod(
                    matchId,
                    MatchPeriodType.REGULATION,
                    1,
                    match.getRegulationPeriodMinutes(),
                    now));
        }
        enqueueLiveNotification(matchId, match, MatchNotificationType.LIVE_MATCH_STARTED);
        publish(matchId, match, LiveMatchChangeType.MATCH_STARTED);
    }

    @Transactional
    public void finish(String authenticatedUserId, UUID matchId) {
        FootballMatch match = managedMatch(authenticatedUserId, matchId);
        if (match.getStatus() != MatchStatus.IN_PROGRESS) throw new InvalidLiveMatchTransitionException();
        if (match.isPeriodsEnabled()) throw new InvalidLiveMatchTransitionException();
        Instant now = Instant.now(clock);
        match.finish(now);
        weeklyMatchWindowService.ensureForMatch(match, now);
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
        penaltyAttemptRepository.deleteAllByMatchId(matchId);
        penaltyTakerRepository.deleteAllByMatchId(matchId);
        penaltyShootoutRepository.deleteByMatchId(matchId);
        periodRepository.deleteAllByMatchId(matchId);
        match.resetLiveMatch();
        publish(matchId, match, LiveMatchChangeType.MATCH_RESET);
    }

    @Transactional
    public LiveMatchStateResponse updatePeriodAddedTime(
            String authenticatedUserId, UUID matchId, int minutes) {
        Access access = accessibleMatch(authenticatedUserId, matchId, true);
        FootballMatch match = access.match();
        if (!match.isPeriodsEnabled() || match.getStatus() != MatchStatus.IN_PROGRESS
                || minutes < 0 || minutes > MatchTimingPolicy.MAX_MINUTES) {
            throw new InvalidPeriodConfigurationException();
        }
        MatchPeriod period = activePeriod(matchId)
                .orElseThrow(InvalidLiveMatchTransitionException::new);
        period.configureAddedTime(minutes);
        match.liveStateChanged();
        publish(matchId, match, LiveMatchChangeType.PERIOD_ADDED_TIME_UPDATED);
        return response(matchId, match, access.member());
    }

    @Transactional
    public LiveMatchStateResponse finishCurrentPeriod(String authenticatedUserId, UUID matchId) {
        Access access = accessibleMatch(authenticatedUserId, matchId, true);
        FootballMatch match = access.match();
        if (!match.isPeriodsEnabled() || match.getStatus() != MatchStatus.IN_PROGRESS) {
            throw new InvalidLiveMatchTransitionException();
        }
        MatchPeriod period = activePeriod(matchId)
                .orElseThrow(InvalidLiveMatchTransitionException::new);
        Instant now = Instant.now(clock);
        long elapsed = Math.max(0, Duration.between(period.getStartedAt(), now).getSeconds());
        long required = Duration.ofMinutes(period.getDurationMinutes()
                + (period.getAddedTimeMinutes() == null ? 0 : period.getAddedTimeMinutes())).toSeconds();
        if (elapsed < required) throw new MatchPeriodNotReadyException();

        period.finish(now);
        match.liveStateChanged();
        LiveMatchChangeType changeType = transitionAfterPeriod(match, matchId, period, now);
        publish(matchId, match, changeType);
        return response(matchId, match, access.member());
    }

    @Transactional
    public LiveMatchStateResponse startNextPeriod(String authenticatedUserId, UUID matchId) {
        Access access = accessibleMatch(authenticatedUserId, matchId, true);
        FootballMatch match = access.match();
        if (!match.isPeriodsEnabled() || match.getStatus() != MatchStatus.IN_PROGRESS
                || activePeriod(matchId).isPresent()
                || penaltyShootoutRepository.findByMatchId(matchId).isPresent()) {
            throw new InvalidLiveMatchTransitionException();
        }
        NextPeriod next = nextPeriod(match, matchId)
                .orElseThrow(InvalidLiveMatchTransitionException::new);
        Instant now = Instant.now(clock);
        periodRepository.save(new MatchPeriod(
                matchId, next.type(), next.number(), next.durationMinutes(), now));
        match.liveStateChanged();
        publish(matchId, match, LiveMatchChangeType.PERIOD_STARTED);
        return response(matchId, match, access.member());
    }

    @Transactional
    public LiveMatchStateResponse setPenaltyLineup(
            String authenticatedUserId,
            UUID matchId,
            List<PenaltyTakerRequest> requestedTakers) {
        Access access = accessibleMatch(authenticatedUserId, matchId, true);
        FootballMatch match = access.match();
        MatchPenaltyShootout shootout = penaltyShootoutRepository.findByMatchId(matchId)
                .orElseThrow(InvalidLiveMatchTransitionException::new);
        if (match.getStatus() != MatchStatus.IN_PROGRESS
                || shootout.getStatus() != PenaltyShootoutStatus.SETUP
                || requestedTakers == null || requestedTakers.size() != 10) {
            throw new InvalidPenaltyShootoutException();
        }

        Set<String> positions = new HashSet<>();
        List<ResolvedPenaltyTaker> resolved = requestedTakers.stream()
                .map(request -> {
                    if (request.teamNumber() == null || request.teamNumber() < 1 || request.teamNumber() > 2
                            || request.kickOrder() == null || request.kickOrder() < 1 || request.kickOrder() > 5
                            || !positions.add(request.teamNumber() + ":" + request.kickOrder())) {
                        throw new InvalidPenaltyShootoutException();
                    }
                    return resolvePenaltyTaker(
                            matchId, request.teamNumber(), request.assignmentId(), request.displayName());
                })
                .toList();
        if (positions.size() != 10) throw new InvalidPenaltyShootoutException();

        penaltyTakerRepository.deleteAllByMatchId(matchId);
        for (int index = 0; index < requestedTakers.size(); index++) {
            PenaltyTakerRequest request = requestedTakers.get(index);
            ResolvedPenaltyTaker taker = resolved.get(index);
            penaltyTakerRepository.save(new MatchPenaltyTaker(
                    matchId,
                    request.teamNumber(),
                    request.kickOrder(),
                    taker.assignment(),
                    taker.displayName()));
        }
        shootout.start(Instant.now(clock));
        match.liveStateChanged();
        publish(matchId, match, LiveMatchChangeType.PENALTY_SHOOTOUT_STARTED);
        return response(matchId, match, access.member());
    }

    @Transactional
    public LiveMatchStateResponse recordPenaltyAttempt(
            String authenticatedUserId,
            UUID matchId,
            boolean scored,
            UUID takerAssignmentId,
            String takerDisplayName) {
        Access access = accessibleMatch(authenticatedUserId, matchId, true);
        FootballMatch match = access.match();
        MatchPenaltyShootout shootout = penaltyShootoutRepository.findByMatchId(matchId)
                .orElseThrow(InvalidLiveMatchTransitionException::new);
        if (match.getStatus() != MatchStatus.IN_PROGRESS
                || shootout.getStatus() != PenaltyShootoutStatus.IN_PROGRESS) {
            throw new InvalidLiveMatchTransitionException();
        }

        List<MatchPenaltyAttempt> attempts = penaltyAttemptRepository
                .findAllByMatchIdOrderBySequenceNumberAsc(matchId);
        int sequence = attempts.size() + 1;
        int teamNumber = sequence % 2 == 1 ? 1 : 2;
        int roundNumber = (sequence + 1) / 2;
        ResolvedPenaltyTaker taker;
        if (roundNumber <= 5) {
            MatchPenaltyTaker planned = penaltyTakerRepository
                    .findByMatchIdAndTeamNumberAndKickOrder(matchId, teamNumber, roundNumber)
                    .orElseThrow(InvalidPenaltyShootoutException::new);
            MatchTeamAssignment assignment = planned.getAssignmentId() == null
                    ? null
                    : assignmentRepository.findByIdAndMatchId(planned.getAssignmentId(), matchId)
                            .orElseThrow(InvalidPenaltyShootoutException::new);
            taker = new ResolvedPenaltyTaker(assignment, planned.getDisplayName());
        } else {
            taker = resolvePenaltyTaker(matchId, teamNumber, takerAssignmentId, takerDisplayName);
        }

        MatchPenaltyAttempt attempt = penaltyAttemptRepository.save(new MatchPenaltyAttempt(
                matchId,
                sequence,
                roundNumber,
                teamNumber,
                taker.assignment(),
                taker.displayName(),
                scored,
                access.member().getUserId(),
                Instant.now(clock)));
        attempts = new java.util.ArrayList<>(attempts);
        attempts.add(attempt);
        Integer winner = penaltyWinner(attempts);
        LiveMatchChangeType changeType = LiveMatchChangeType.PENALTY_ATTEMPT_RECORDED;
        if (winner != null) {
            shootout.decide(winner, Instant.now(clock));
            changeType = LiveMatchChangeType.PENALTY_SHOOTOUT_DECIDED;
        }
        match.liveStateChanged();
        publish(matchId, match, changeType);
        return response(matchId, match, access.member());
    }

    @Transactional
    public LiveMatchStateResponse confirmPenaltyWinner(String authenticatedUserId, UUID matchId) {
        Access access = accessibleMatch(authenticatedUserId, matchId, true);
        FootballMatch match = access.match();
        MatchPenaltyShootout shootout = penaltyShootoutRepository.findByMatchId(matchId)
                .orElseThrow(InvalidLiveMatchTransitionException::new);
        if (match.getStatus() != MatchStatus.IN_PROGRESS
                || shootout.getStatus() != PenaltyShootoutStatus.AWAITING_CONFIRMATION) {
            throw new InvalidLiveMatchTransitionException();
        }
        Instant now = Instant.now(clock);
        shootout.complete(now);
        finishMatch(matchId, match, now);
        publish(matchId, match, LiveMatchChangeType.MATCH_FINISHED);
        return response(matchId, match, access.member());
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
        if (match.isPeriodsEnabled() && activePeriod(matchId).isEmpty()) {
            throw new InvalidLiveMatchTransitionException();
        }
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
        EventTime eventTime = eventTime(match, now);
        MatchGoalEvent event = goalEventRepository.save(new MatchGoalEvent(
                matchId, scorer, participantName(matchId, scorer), assist,
                assist == null ? null : participantName(matchId, assist),
                penalty, eventTime.elapsedSeconds(), eventTime.periodType(),
                eventTime.periodNumber(), eventTime.periodElapsedSeconds(),
                access.member().getUserId(), now));
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
        EventTime eventTime = eventTime(match, now);
        MatchCardEvent event = cardEventRepository.save(new MatchCardEvent(matchId, player,
                participantName(matchId, player), cardType, eventTime.elapsedSeconds(),
                eventTime.periodType(), eventTime.periodNumber(), eventTime.periodElapsedSeconds(),
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
        if (match.isPeriodsEnabled() && activePeriod(matchId).isEmpty()) {
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
        if (match.isPeriodsEnabled() && activePeriod(matchId).isEmpty()) {
            throw new InvalidLiveMatchTransitionException();
        }
        MatchCardEvent event = cardEventRepository.findByIdAndMatchId(eventId, matchId)
                .orElseThrow(InvalidCardEventException::new);
        cardEventRepository.delete(event);
        match.liveStateChanged();
        publish(matchId, match, LiveMatchChangeType.CARD_REMOVED);
        return response(matchId, match, access.member());
    }

    private Optional<MatchPeriod> activePeriod(UUID matchId) {
        return periodRepository.findFirstByMatchIdAndEndedAtIsNullOrderByStartedAtDesc(matchId);
    }

    private LiveMatchChangeType transitionAfterPeriod(
            FootballMatch match,
            UUID matchId,
            MatchPeriod finishedPeriod,
            Instant now) {
        long completedSameType = periodRepository.countByMatchIdAndPeriodType(
                matchId, finishedPeriod.getPeriodType());
        int configuredSameType = finishedPeriod.getPeriodType() == MatchPeriodType.REGULATION
                ? match.getRegulationPeriodCount()
                : match.getOvertimePeriodCount();
        if (completedSameType < configuredSameType) {
            return LiveMatchChangeType.PERIOD_FINISHED;
        }

        boolean tied = isScoreTied(matchId);
        if (finishedPeriod.getPeriodType() == MatchPeriodType.REGULATION
                && tied && match.isOvertimeEnabled()) {
            return LiveMatchChangeType.PERIOD_FINISHED;
        }
        if (tied && match.isPenaltyShootoutEnabled()) {
            penaltyShootoutRepository.findByMatchId(matchId)
                    .orElseGet(() -> penaltyShootoutRepository.save(new MatchPenaltyShootout(matchId)));
            return LiveMatchChangeType.PENALTY_SHOOTOUT_READY;
        }
        finishMatch(matchId, match, now);
        return LiveMatchChangeType.MATCH_FINISHED;
    }

    private Optional<NextPeriod> nextPeriod(FootballMatch match, UUID matchId) {
        int regulationCompleted = Math.toIntExact(periodRepository.countByMatchIdAndPeriodType(
                matchId, MatchPeriodType.REGULATION));
        if (regulationCompleted < match.getRegulationPeriodCount()) {
            return Optional.of(new NextPeriod(
                    MatchPeriodType.REGULATION,
                    regulationCompleted + 1,
                    match.getRegulationPeriodMinutes()));
        }
        int overtimeCompleted = Math.toIntExact(periodRepository.countByMatchIdAndPeriodType(
                matchId, MatchPeriodType.OVERTIME));
        if (isScoreTied(matchId) && match.isOvertimeEnabled()
                && overtimeCompleted < match.getOvertimePeriodCount()) {
            return Optional.of(new NextPeriod(
                    MatchPeriodType.OVERTIME,
                    overtimeCompleted + 1,
                    match.getOvertimePeriodMinutes()));
        }
        return Optional.empty();
    }

    private boolean isScoreTied(UUID matchId) {
        List<LiveMatchScore> scores = scoreRepository.findAllByMatchIdOrderBySideNumberAsc(matchId);
        return scores.size() >= 2 && scores.get(0).getScore() == scores.get(1).getScore();
    }

    private void finishMatch(UUID matchId, FootballMatch match, Instant now) {
        match.finish(now);
        weeklyMatchWindowService.ensureForMatch(match, now);
        enqueueLiveNotification(matchId, match, MatchNotificationType.LIVE_MATCH_FINISHED);
    }

    private ResolvedPenaltyTaker resolvePenaltyTaker(
            UUID matchId,
            int teamNumber,
            UUID assignmentId,
            String requestedDisplayName) {
        if (assignmentId != null) {
            MatchTeamAssignment assignment = assignmentRepository.findByIdAndMatchId(assignmentId, matchId)
                    .orElseThrow(InvalidPenaltyShootoutException::new);
            if (assignment.getTeamNumber() != teamNumber) throw new InvalidPenaltyShootoutException();
            return new ResolvedPenaltyTaker(assignment, participantName(matchId, assignment));
        }
        String displayName = requestedDisplayName == null ? null : requestedDisplayName.trim();
        if (displayName == null || displayName.isBlank() || displayName.length() > 120) {
            throw new InvalidPenaltyShootoutException();
        }
        return new ResolvedPenaltyTaker(null, displayName);
    }

    static Integer penaltyWinner(List<MatchPenaltyAttempt> attempts) {
        int takenOne = 0;
        int takenTwo = 0;
        int scoreOne = 0;
        int scoreTwo = 0;
        for (MatchPenaltyAttempt attempt : attempts) {
            if (attempt.getTeamNumber() == 1) {
                takenOne++;
                if (attempt.isScored()) scoreOne++;
            } else {
                takenTwo++;
                if (attempt.isScored()) scoreTwo++;
            }
        }

        if (takenOne <= 5 && takenTwo <= 5) {
            int remainingOne = Math.max(0, 5 - takenOne);
            int remainingTwo = Math.max(0, 5 - takenTwo);
            if (scoreOne > scoreTwo + remainingTwo) return 1;
            if (scoreTwo > scoreOne + remainingOne) return 2;
        }
        if (takenOne >= 5 && takenTwo >= 5 && takenOne == takenTwo && scoreOne != scoreTwo) {
            return scoreOne > scoreTwo ? 1 : 2;
        }
        return null;
    }

    private EventTime eventTime(FootballMatch match, Instant now) {
        if (!match.isPeriodsEnabled()) {
            return new EventTime(elapsedSeconds(match, now), null, null, null);
        }
        List<MatchPeriod> periods = periodRepository.findAllByMatchIdOrderByStartedAtAsc(match.getId());
        MatchPeriod active = periods.stream()
                .filter(MatchPeriod::isRunning)
                .findFirst()
                .orElseThrow(InvalidLiveMatchTransitionException::new);
        long totalElapsed = 0;
        long periodElapsed = 0;
        for (MatchPeriod period : periods) {
            Instant end = period.getEndedAt() == null ? now : period.getEndedAt();
            long elapsed = Math.max(0, Duration.between(period.getStartedAt(), end).getSeconds());
            totalElapsed += elapsed;
            if (period.getId().equals(active.getId())) periodElapsed = elapsed;
        }
        return new EventTime(
                totalElapsed,
                active.getPeriodType(),
                active.getPeriodNumber(),
                periodElapsed);
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
        if (match.isPeriodsEnabled()) return false;
        if (match.getStatus() == MatchStatus.IN_PROGRESS && match.getStartedAt() != null) {
            Instant deadline = match.getStartedAt().plus(MAX_MATCH_DURATION);
            if (!now.isBefore(deadline)) {
                match.finish(deadline);
                weeklyMatchWindowService.ensureForMatch(match, now);
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
                event.isPenalty(), event.getElapsedSeconds(), event.getPeriodType(),
                event.getPeriodNumber(), event.getPeriodElapsedSeconds(), event.getCreatedAt());
    }

    private CardEventResponse cardResponse(MatchCardEvent event) {
        return new CardEventResponse(event.getId(), event.getMatchId(), event.getSideNumber(),
                event.getPlayerAssignmentId(), event.getPlayerParticipantType(), event.getPlayerParticipantId(),
                event.getPlayerDisplayName(), event.getCardType(), event.getElapsedSeconds(),
                event.getPeriodType(), event.getPeriodNumber(), event.getPeriodElapsedSeconds(),
                event.getCreatedAt());
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
                snapshot.phase(), snapshot.periods(), snapshot.penaltyShootout(),
                member.hasPermission(GroupAdminPermission.SCHEDULE_GAMES));
    }

    @Transactional(readOnly = true)
    public Optional<LiveMatchSnapshotResponse> getSnapshot(UUID matchId) {
        return matchRepository.findById(matchId).map(match -> snapshot(matchId, match));
    }

    private LiveMatchSnapshotResponse snapshot(UUID matchId, FootballMatch match) {
        Map<Integer, MatchTeamImage> identitiesByTeam = teamImageRepository
                .findAllByMatchIdOrderByTeamNumberAsc(matchId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        MatchTeamImage::getTeamNumber,
                        item -> item));
        List<LiveScoreSideResponse> scores = scoreRepository.findAllByMatchIdOrderBySideNumberAsc(matchId)
                .stream().map(item -> {
                    MatchTeamImage identity = identitiesByTeam.get(item.getSideNumber());
                    return new LiveScoreSideResponse(
                            item.getSideNumber(),
                            item.getScore(),
                            identity == null ? "Time " + item.getSideNumber() : identity.getTeamName(),
                            identity == null ? null : identity.getImageUrl());
                })
                .toList();
        if (scores.isEmpty() && match.getStatus() != MatchStatus.SCHEDULED) {
            scores = java.util.stream.IntStream.rangeClosed(1, sideCount(match))
                    .mapToObj(side -> {
                        MatchTeamImage identity = identitiesByTeam.get(side);
                        return new LiveScoreSideResponse(
                                side,
                                0,
                                identity == null ? "Time " + side : identity.getTeamName(),
                                identity == null ? null : identity.getImageUrl());
                    })
                    .toList();
        }
        List<GoalEventResponse> goalEvents = goalEventRepository
                .findAllByMatchIdOrderByElapsedSecondsDescCreatedAtDesc(matchId)
                .stream().map(this::goalResponse).toList();
        List<CardEventResponse> cardEvents = cardEventRepository
                .findAllByMatchIdOrderByElapsedSecondsDescCreatedAtDesc(matchId)
                .stream().map(this::cardResponse).toList();
        List<MatchPeriodResponse> periods = periodRepository.findAllByMatchIdOrderByStartedAtAsc(matchId)
                .stream().map(this::periodResponse).toList();
        PenaltyShootoutResponse penaltyShootout = penaltyResponse(matchId).orElse(null);
        return new LiveMatchSnapshotResponse(matchId, match.getStatus(), match.getStartedAt(),
                match.getFinishedAt(), match.getLiveVersion(), scores, goalEvents, cardEvents,
                livePhase(match, periods, penaltyShootout), periods, penaltyShootout);
    }

    private MatchPeriodResponse periodResponse(MatchPeriod period) {
        return new MatchPeriodResponse(
                period.getId(),
                period.getPeriodType(),
                period.getPeriodNumber(),
                period.getDurationMinutes(),
                period.getAddedTimeMinutes(),
                period.getStartedAt(),
                period.getEndedAt());
    }

    private Optional<PenaltyShootoutResponse> penaltyResponse(UUID matchId) {
        return penaltyShootoutRepository.findByMatchId(matchId).map(shootout -> {
            List<PenaltyTakerResponse> takers = penaltyTakerRepository
                    .findAllByMatchIdOrderByTeamNumberAscKickOrderAsc(matchId)
                    .stream().map(this::penaltyTakerResponse).toList();
            List<MatchPenaltyAttempt> storedAttempts = penaltyAttemptRepository
                    .findAllByMatchIdOrderBySequenceNumberAsc(matchId);
            List<PenaltyAttemptResponse> attempts = storedAttempts.stream()
                    .map(this::penaltyAttemptResponse).toList();
            int teamOneAttempts = (int) storedAttempts.stream()
                    .filter(item -> item.getTeamNumber() == 1).count();
            int teamTwoAttempts = (int) storedAttempts.stream()
                    .filter(item -> item.getTeamNumber() == 2).count();
            int teamOneScore = (int) storedAttempts.stream()
                    .filter(item -> item.getTeamNumber() == 1 && item.isScored()).count();
            int teamTwoScore = (int) storedAttempts.stream()
                    .filter(item -> item.getTeamNumber() == 2 && item.isScored()).count();

            Integer nextTeam = null;
            Integer nextRound = null;
            boolean nextTakerSelectionRequired = false;
            PenaltyTakerResponse nextTaker = null;
            if (shootout.getStatus() == PenaltyShootoutStatus.IN_PROGRESS) {
                int nextSequence = storedAttempts.size() + 1;
                nextTeam = nextSequence % 2 == 1 ? 1 : 2;
                nextRound = (nextSequence + 1) / 2;
                if (nextRound <= 5) {
                    final int plannedTeam = nextTeam;
                    final int plannedOrder = nextRound;
                    nextTaker = takers.stream()
                            .filter(item -> item.teamNumber() == plannedTeam
                                    && item.kickOrder() == plannedOrder)
                            .findFirst().orElse(null);
                } else {
                    nextTakerSelectionRequired = true;
                }
            }
            return new PenaltyShootoutResponse(
                    shootout.getStatus(),
                    teamOneScore,
                    teamTwoScore,
                    teamOneAttempts,
                    teamTwoAttempts,
                    nextTeam,
                    nextRound,
                    nextTakerSelectionRequired,
                    nextTaker,
                    shootout.getWinnerTeamNumber(),
                    takers,
                    attempts);
        });
    }

    private PenaltyTakerResponse penaltyTakerResponse(MatchPenaltyTaker taker) {
        return new PenaltyTakerResponse(
                taker.getTeamNumber(),
                taker.getKickOrder(),
                taker.getAssignmentId(),
                taker.getParticipantType(),
                taker.getParticipantId(),
                taker.getDisplayName());
    }

    private PenaltyAttemptResponse penaltyAttemptResponse(MatchPenaltyAttempt attempt) {
        return new PenaltyAttemptResponse(
                attempt.getId(),
                attempt.getSequenceNumber(),
                attempt.getRoundNumber(),
                attempt.getTeamNumber(),
                attempt.getAssignmentId(),
                attempt.getParticipantType(),
                attempt.getParticipantId(),
                attempt.getDisplayName(),
                attempt.isScored(),
                attempt.getCreatedAt());
    }

    private LiveMatchPhase livePhase(
            FootballMatch match,
            List<MatchPeriodResponse> periods,
            PenaltyShootoutResponse penaltyShootout) {
        if (match.getStatus() == MatchStatus.FINISHED) return LiveMatchPhase.FINISHED;
        if (!match.isPeriodsEnabled()) return LiveMatchPhase.LEGACY;
        if (penaltyShootout != null && penaltyShootout.status() != PenaltyShootoutStatus.COMPLETED) {
            return LiveMatchPhase.PENALTY_SHOOTOUT;
        }
        MatchPeriodResponse active = periods.stream()
                .filter(period -> period.endedAt() == null)
                .findFirst().orElse(null);
        if (active != null) {
            return active.periodType() == MatchPeriodType.OVERTIME
                    ? LiveMatchPhase.OVERTIME
                    : LiveMatchPhase.REGULATION;
        }
        long completedRegulation = periods.stream()
                .filter(period -> period.periodType() == MatchPeriodType.REGULATION).count();
        if (completedRegulation < match.getRegulationPeriodCount()) return LiveMatchPhase.REGULATION;
        return LiveMatchPhase.OVERTIME;
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

    private record NextPeriod(MatchPeriodType type, int number, int durationMinutes) { }

    private record EventTime(
            long elapsedSeconds,
            MatchPeriodType periodType,
            Integer periodNumber,
            Long periodElapsedSeconds) { }

    private record ResolvedPenaltyTaker(MatchTeamAssignment assignment, String displayName) { }

    public static final class InvalidLiveMatchTransitionException extends RuntimeException { }
    public static final class InvalidLiveMatchScoreException extends RuntimeException { }
    public static final class InvalidGoalEventException extends RuntimeException { }
    public static final class InvalidCardEventException extends RuntimeException { }
    public static final class InvalidPeriodConfigurationException extends RuntimeException { }
    public static final class MatchPeriodNotReadyException extends RuntimeException { }
    public static final class InvalidPenaltyShootoutException extends RuntimeException { }
}
