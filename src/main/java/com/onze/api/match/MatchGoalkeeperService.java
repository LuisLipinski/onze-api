package com.onze.api.match;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.PlayerPosition;

import org.springframework.stereotype.Service;

@Service
public class MatchGoalkeeperService {

    private final MatchAttendanceRepository attendanceRepository;
    private final MatchRentalGoalkeeperRepository rentalGoalkeeperRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final PlayerCreditService playerCreditService;

    public MatchGoalkeeperService(
            MatchAttendanceRepository attendanceRepository,
            MatchRentalGoalkeeperRepository rentalGoalkeeperRepository,
            GroupMemberRepository groupMemberRepository,
            PlayerCreditService playerCreditService) {
        this.attendanceRepository = attendanceRepository;
        this.rentalGoalkeeperRepository = rentalGoalkeeperRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.playerCreditService = playerCreditService;
    }

    public void assignPrimaryGoalkeeper(
            FootballMatch match,
            MatchAttendance attendance,
            GroupMember member,
            Instant now) {
        if (member.getPrimaryPosition() == PlayerPosition.GOALKEEPER
                && !attendance.isGoalkeeper()) {
            applyGoalkeeperRole(match, attendance, true, now);
        }
    }

    public void updateByAdministrator(
            FootballMatch match,
            MatchAttendance attendance,
            GroupMember member,
            boolean goalkeeper,
            Instant now) {
        if (goalkeeper && !isManualCandidate(member)) {
            throw new GoalkeeperCandidateRequiredException();
        }
        if (!goalkeeper && member.getPrimaryPosition() == PlayerPosition.GOALKEEPER) {
            throw new PrimaryGoalkeeperCannotBeUnassignedException();
        }
        applyGoalkeeperRole(match, attendance, goalkeeper, now);
    }

    public int processSignupDeadline(FootballMatch match, Instant now) {
        if (match.getSignupDeadline() == null
                || !now.isAfter(match.getSignupDeadline())
                || match.getStatus() != MatchStatus.SCHEDULED) {
            return 0;
        }

        List<MatchAttendance> attendances = attendanceRepository
                .findAllByMatchIdOrderByCreatedAtAsc(match.getId());
        List<GroupMember> members = groupMemberRepository
                .findAllByGroupIdOrderByCreatedAtAsc(match.getGroupId());
        int missingGoalkeepers = summary(
                match,
                attendances,
                members,
                rentalGoalkeeperRepository.countByMatchId(match.getId()),
                now).missingGoalkeepers();
        if (missingGoalkeepers == 0) {
            return 0;
        }

        Map<UUID, GroupMember> membersByUser = members.stream()
                .collect(Collectors.toMap(GroupMember::getUserId, Function.identity()));
        List<MatchAttendance> secondaryCandidates = attendances.stream()
                .filter(attendance -> attendance.getStatus() == AttendanceStatus.GOING)
                .filter(attendance -> !attendance.isGoalkeeper())
                .filter(attendance -> {
                    GroupMember member = membersByUser.get(attendance.getUserId());
                    return member != null
                            && member.getSecondaryPosition() == PlayerPosition.GOALKEEPER;
                })
                .toList();

        if (secondaryCandidates.isEmpty() || secondaryCandidates.size() > missingGoalkeepers) {
            return 0;
        }

        int assigned = 0;
        for (MatchAttendance candidate : secondaryCandidates) {
            if (!match.isGoalkeeperPays() && candidate.hasRecordedCashPayment()) {
                continue;
            }
            applyGoalkeeperRole(match, candidate, true, now);
            assigned++;
        }
        return assigned;
    }

    public GoalkeeperSummary summary(
            FootballMatch match,
            List<MatchAttendance> attendances,
            List<GroupMember> members,
            long rentalGoalkeepers,
            Instant now) {
        int memberGoalkeepers = (int) attendances.stream()
                .filter(attendance -> attendance.getStatus() == AttendanceStatus.GOING)
                .filter(MatchAttendance::isGoalkeeper)
                .count();
        int currentGoalkeepers = Math.toIntExact(memberGoalkeepers + rentalGoalkeepers);
        int missingGoalkeepers = Math.max(
                match.getRequiredGoalkeepers() - currentGoalkeepers,
                0);
        boolean afterSignupDeadline = match.getSignupDeadline() != null
                && now.isAfter(match.getSignupDeadline());

        Map<UUID, GroupMember> membersByUser = members.stream()
                .collect(Collectors.toMap(GroupMember::getUserId, Function.identity()));
        long secondaryCandidates = attendances.stream()
                .filter(attendance -> attendance.getStatus() == AttendanceStatus.GOING)
                .filter(attendance -> !attendance.isGoalkeeper())
                .map(attendance -> membersByUser.get(attendance.getUserId()))
                .filter(java.util.Objects::nonNull)
                .filter(member -> member.getSecondaryPosition() == PlayerPosition.GOALKEEPER)
                .count();

        return new GoalkeeperSummary(
                currentGoalkeepers,
                missingGoalkeepers,
                afterSignupDeadline && missingGoalkeepers > 0,
                afterSignupDeadline
                        && missingGoalkeepers > 0
                        && secondaryCandidates >= 2
                        && secondaryCandidates > missingGoalkeepers);
    }

    private boolean isManualCandidate(GroupMember member) {
        return member.getPrimaryPosition() == PlayerPosition.GOALKEEPER
                || member.getSecondaryPosition() == PlayerPosition.GOALKEEPER
                || member.canPlayGoalkeeper();
    }

    private void applyGoalkeeperRole(
            FootballMatch match,
            MatchAttendance attendance,
            boolean goalkeeper,
            Instant now) {
        if (attendance.isGoalkeeper() == goalkeeper) {
            return;
        }

        if (goalkeeper) {
            if (!match.isGoalkeeperPays() && attendance.hasRecordedCashPayment()) {
                throw new GoalkeeperPaymentAlreadyRecordedException();
            }
            attendance.setGoalkeeper(true);
            if (MatchPaymentPolicy.isExempt(match, attendance)) {
                boolean creditReleased = playerCreditService.releaseReservation(
                        match.getGroupId(),
                        attendance,
                        now);
                attendance.exemptFromPayment(now);
                if (creditReleased) {
                    playerCreditService.reserveForNextMatch(
                            match.getGroupId(),
                            attendance.getUserId(),
                            now);
                }
            }
            return;
        }

        boolean wasExempt = MatchPaymentPolicy.isExempt(match, attendance);
        attendance.setGoalkeeper(false);
        if (wasExempt) {
            attendance.restorePaymentObligation(match.getPaymentAmount());
            playerCreditService.reserveForNextMatch(
                    match.getGroupId(),
                    attendance.getUserId(),
                    now);
        }
    }

    public record GoalkeeperSummary(
            int currentGoalkeepers,
            int missingGoalkeepers,
            boolean goalkeeperDecisionRequired,
            boolean secondaryGoalkeeperDecisionRequired) {
    }

    public static final class GoalkeeperPaymentAlreadyRecordedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class GoalkeeperCandidateRequiredException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    public static final class PrimaryGoalkeeperCannotBeUnassignedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
