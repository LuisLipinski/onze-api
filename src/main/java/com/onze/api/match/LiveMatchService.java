package com.onze.api.match;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupService.GroupAccessDeniedException;
import com.onze.api.group.GroupService.GroupUserNotFoundException;
import com.onze.api.match.MatchService.MatchNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LiveMatchService {
    private final FootballMatchRepository matchRepository;
    private final GroupMemberRepository memberRepository;
    private final Clock clock;

    public LiveMatchService(FootballMatchRepository matchRepository, GroupMemberRepository memberRepository, Clock clock) {
        this.matchRepository = matchRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    @Transactional
    public void start(String authenticatedUserId, UUID matchId) {
        FootballMatch match = managedMatch(authenticatedUserId, matchId);
        if (match.getStatus() != MatchStatus.SCHEDULED) throw new InvalidLiveMatchTransitionException();
        match.start(Instant.now(clock));
    }

    @Transactional
    public void finish(String authenticatedUserId, UUID matchId) {
        FootballMatch match = managedMatch(authenticatedUserId, matchId);
        if (match.getStatus() != MatchStatus.IN_PROGRESS) throw new InvalidLiveMatchTransitionException();
        match.finish(Instant.now(clock));
    }

    private FootballMatch managedMatch(String authenticatedUserId, UUID matchId) {
        UUID userId;
        try { userId = UUID.fromString(authenticatedUserId); }
        catch (IllegalArgumentException exception) { throw new GroupUserNotFoundException(); }
        FootballMatch match = matchRepository.findByIdForUpdate(matchId).orElseThrow(MatchNotFoundException::new);
        GroupMember member = memberRepository.findByGroupIdAndUserId(match.getGroupId(), userId)
                .orElseThrow(GroupAccessDeniedException::new);
        if (!member.hasPermission(GroupAdminPermission.SCHEDULE_GAMES)) throw new GroupAccessDeniedException();
        return match;
    }

    public static final class InvalidLiveMatchTransitionException extends RuntimeException { }
}
