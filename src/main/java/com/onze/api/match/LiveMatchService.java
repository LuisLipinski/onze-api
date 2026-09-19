package com.onze.api.match;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.match.MatchService.MatchNotFoundException;
import com.onze.api.match.LiveMatchModels.LiveMatchStateResponse;
import com.onze.api.match.LiveMatchModels.LiveScoreSideResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LiveMatchService {
    private final FootballMatchRepository matchRepository;
    private final GroupMemberRepository memberRepository;
    private final LiveMatchScoreRepository scoreRepository;
    private final Clock clock;

    public LiveMatchService(FootballMatchRepository matchRepository, GroupMemberRepository memberRepository,
            LiveMatchScoreRepository scoreRepository, Clock clock) {
        this.matchRepository = matchRepository;
        this.memberRepository = memberRepository;
        this.scoreRepository = scoreRepository;
        this.clock = clock;
    }

    @Transactional
    public void start(String authenticatedUserId, UUID matchId) {
        FootballMatch match = managedMatch(authenticatedUserId, matchId);
        if (match.getStatus() != MatchStatus.SCHEDULED) throw new InvalidLiveMatchTransitionException();
        match.start(Instant.now(clock));
        initializeScoreboard(match, matchId);
    }

    @Transactional
    public void finish(String authenticatedUserId, UUID matchId) {
        FootballMatch match = managedMatch(authenticatedUserId, matchId);
        if (match.getStatus() != MatchStatus.IN_PROGRESS) throw new InvalidLiveMatchTransitionException();
        match.finish(Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public LiveMatchStateResponse get(String authenticatedUserId, UUID matchId) {
        Access access = accessibleMatch(authenticatedUserId, matchId, false);
        return response(matchId, access.match(), access.member());
    }

    @Transactional
    public LiveMatchStateResponse updateScore(
            String authenticatedUserId, UUID matchId, int sideNumber, int score) {
        Access access = accessibleMatch(authenticatedUserId, matchId, true);
        FootballMatch match = access.match();
        if (match.getStatus() != MatchStatus.IN_PROGRESS) throw new InvalidLiveMatchTransitionException();
        if (sideNumber < 1 || sideNumber > sideCount(match) || score < 0) {
            throw new InvalidLiveMatchScoreException();
        }
        initializeScoreboard(match, matchId);
        LiveMatchScore storedScore = scoreRepository.findByMatchIdAndSideNumber(matchId, sideNumber)
                .orElseThrow(InvalidLiveMatchScoreException::new);
        storedScore.update(score);
        return response(matchId, match, access.member());
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
        List<LiveScoreSideResponse> scores = scoreRepository.findAllByMatchIdOrderBySideNumberAsc(matchId)
                .stream().map(item -> new LiveScoreSideResponse(item.getSideNumber(), item.getScore())).toList();
        if (scores.isEmpty() && match.getStatus() != MatchStatus.SCHEDULED) {
            scores = java.util.stream.IntStream.rangeClosed(1, sideCount(match))
                    .mapToObj(side -> new LiveScoreSideResponse(side, 0)).toList();
        }
        return new LiveMatchStateResponse(matchId, match.getStatus(), match.getStartedAt(),
                match.getFinishedAt(), scores, member.hasPermission(GroupAdminPermission.SCHEDULE_GAMES));
    }

    private int sideCount(FootballMatch match) {
        return match.getMatchType() == MatchType.INTERNAL ? match.getTeamCount() : 2;
    }

    private record Access(FootballMatch match, GroupMember member) { }

    public static final class InvalidLiveMatchTransitionException extends RuntimeException { }
    public static final class InvalidLiveMatchScoreException extends RuntimeException { }
}
