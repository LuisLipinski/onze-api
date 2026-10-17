package com.onze.api.match;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.onze.api.group.GroupAdminPermission;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.PlayerPosition;

import org.springframework.stereotype.Service;

/** Called inside a transaction holding the football_matches row lock. */
@Service
public class MatchWaitlistService {
    private final MatchAttendanceRepository attendanceRepository;
    private final MatchGuestRepository guestRepository;
    private final GroupMemberRepository memberRepository;
    private final MatchCapacityService capacity;
    private final MatchGoalkeeperService goalkeepers;
    private final PlayerCreditService credits;
    private final MatchTeamAssignmentRepository assignments;
    private final MatchNotificationQueue notifications;

    public MatchWaitlistService(
            MatchAttendanceRepository attendanceRepository,
            MatchGuestRepository guestRepository,
            GroupMemberRepository memberRepository,
            MatchCapacityService capacity,
            MatchGoalkeeperService goalkeepers,
            PlayerCreditService credits,
            MatchTeamAssignmentRepository assignments,
            MatchNotificationQueue notifications) {
        this.attendanceRepository = attendanceRepository;
        this.guestRepository = guestRepository;
        this.memberRepository = memberRepository;
        this.capacity = capacity;
        this.goalkeepers = goalkeepers;
        this.credits = credits;
        this.assignments = assignments;
        this.notifications = notifications;
    }

    public List<MatchAttendance> waiting(FootballMatch match) {
        return attendanceRepository.findAllByMatchIdAndStatusOrderByWaitlistOrderAsc(
                match.getId(), AttendanceStatus.WAITLISTED);
    }

    public void join(FootballMatch match, MatchAttendance attendance, Instant now) {
        if (attendance.getStatus() == AttendanceStatus.WAITLISTED) return;
        credits.releaseReservation(match.getGroupId(), attendance, now);
        attendance.joinWaitlist(now, attendanceRepository.nextWaitlistOrder());
        credits.reserveForNextMatch(match.getGroupId(), attendance.getUserId(), now);
        notifyDecisions(match, now);
    }

    public boolean canAdmit(FootballMatch match, GroupMember member) {
        return capacity.canAdmit(match, member.getPrimaryPosition() == PlayerPosition.GOALKEEPER);
    }

    public void requireQueuePriority(FootballMatch match, UUID candidateId) {
        for (MatchAttendance waiting : waiting(match)) {
            GroupMember member = memberRepository.findByGroupIdAndUserId(
                    match.getGroupId(), waiting.getUserId()).orElse(null);
            if (member != null && canAdmit(match, member)) {
                if (!waiting.getUserId().equals(candidateId)) {
                    throw new WaitlistConflictException("A vaga pertence ao primeiro jogador elegível da lista de espera.");
                }
                return;
            }
        }
    }

    public int reconcile(FootballMatch match, Instant now) {
        if (!match.isAttendanceOpen(now)) return 0;
        int promoted = 0;
        for (MatchAttendance waiting : waiting(match)) {
            GroupMember member = memberRepository.findByGroupIdAndUserId(
                    match.getGroupId(), waiting.getUserId()).orElse(null);
            if (member == null) {
                waiting.changeStatus(AttendanceStatus.NOT_GOING, null, now);
                continue;
            }
            // A keeper can fill a reserved place without consuming the outfield FIFO place.
            if (canAdmit(match, member)) {
                promote(match, waiting, member, false, now);
                promoted++;
            }
        }
        notifyDecisions(match, now);
        return promoted;
    }

    public boolean guestDecisionRequired(FootballMatch match) {
        List<MatchAttendance> waiting = waiting(match);
        return !waiting.isEmpty()
                && !replaceableGuests(match, waiting.get(0)).isEmpty()
                && !guestPreferenceKey(match, waiting.get(0)).equals(match.getWaitlistGuestPreferenceKey());
    }

    public List<UUID> replaceableGuestIds(FootballMatch match) {
        List<MatchAttendance> waiting = waiting(match);
        return waiting.isEmpty() ? List.of()
                : replaceableGuests(match, waiting.get(0)).stream().map(MatchGuest::getId).toList();
    }

    public void decideGuestPreference(
            FootballMatch match, UUID expectedFirstUserId, UUID guestId, boolean keepGuests, Instant now) {
        MatchAttendance first = requireFirst(match, expectedFirstUserId);
        if (!guestDecisionRequired(match)) {
            throw new WaitlistConflictException("A preferência já foi decidida ou a lista mudou. Atualize a partida.");
        }
        if (keepGuests) {
            match.keepGuestPreference(guestPreferenceKey(match, first));
            return;
        }
        MatchGuest guest = replaceableGuests(match, first).stream()
                .filter(item -> item.getId().equals(guestId)).findFirst()
                .orElseThrow(() -> new WaitlistConflictException(
                        "Escolha um convidado cuja vaga possa ser ocupada pelo primeiro da fila."));
        guestRepository.delete(guest);
        guestRepository.flush();
        GroupMember member = memberRepository.findByGroupIdAndUserId(
                match.getGroupId(), first.getUserId()).orElseThrow();
        promote(match, first, member, false, now);
        reconcile(match, now);
    }

    public void useFirstAsGoalkeeper(FootballMatch match, UUID expectedFirstUserId, Instant now) {
        MatchAttendance first = requireFirst(match, expectedFirstUserId);
        if (match.getSignupDeadline() == null || !now.isAfter(match.getSignupDeadline())
                || capacity.reservedGoalkeeperSlots(match) == 0
                || !capacity.canAdmit(match, true)) {
            throw new WaitlistConflictException(
                    "Esta decisão exige prazo encerrado, goleiro faltante e uma vaga reservada disponível.");
        }
        GroupMember member = memberRepository.findByGroupIdAndUserId(
                match.getGroupId(), first.getUserId()).orElseThrow();
        // An explicit administrator decision assigns this role only to this occurrence.
        promote(match, first, member, true, now);
        reconcile(match, now);
    }

    private MatchAttendance requireFirst(FootballMatch match, UUID expectedFirstUserId) {
        List<MatchAttendance> waiting = waiting(match);
        if (waiting.isEmpty() || !waiting.get(0).getUserId().equals(expectedFirstUserId)) {
            throw new WaitlistConflictException("A lista de espera mudou. Atualize a partida antes de escolher.");
        }
        return waiting.get(0);
    }

    private List<MatchGuest> replaceableGuests(FootballMatch match, MatchAttendance first) {
        GroupMember member = memberRepository.findByGroupIdAndUserId(
                match.getGroupId(), first.getUserId()).orElse(null);
        if (member == null) return List.of();
        boolean keeper = member.getPrimaryPosition() == PlayerPosition.GOALKEEPER;
        return guestRepository.findAllByMatchIdOrderByCreatedAtAsc(match.getId()).stream()
                .filter(guest -> keeper || guest.getPrimaryPosition() != PlayerPosition.GOALKEEPER)
                .filter(guest -> keeper || match.getMaxPlayers() - capacity.occupiedSpots(match.getId()) + 1
                        > capacity.reservedGoalkeeperSlots(match))
                .toList();
    }

    private String guestPreferenceKey(FootballMatch match, MatchAttendance first) {
        String guestIds = guestRepository.findAllByMatchIdOrderByCreatedAtAsc(match.getId()).stream()
                .map(guest -> guest.getId().toString()).sorted().collect(java.util.stream.Collectors.joining(","));
        return UUID.nameUUIDFromBytes((first.getWaitlistOrder() + ":" + guestIds)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void promote(
            FootballMatch match, MatchAttendance attendance, GroupMember member, boolean asGoalkeeper, Instant now) {
        long order = attendance.getWaitlistOrder();
        if (asGoalkeeper) attendance.setGoalkeeper(true);
        attendance.changeStatus(AttendanceStatus.GOING,
                MatchPaymentPolicy.effectiveAmount(match, attendance), now);
        goalkeepers.assignPrimaryGoalkeeper(match, attendance, member, now);
        if (MatchPaymentPolicy.isExempt(match, attendance)) attendance.exemptFromPayment(now);
        // The existing replacement window also covers promotions after the payment deadline.
        attendance.markAddedAsReplacement(null, now);
        credits.reserveForNextMatch(match.getGroupId(), attendance.getUserId(), now);
        credits.consumeReservation(match.getGroupId(), attendance, now);
        MatchAttendance departed = attendanceRepository.findAllByMatchIdOrderByCreatedAtAsc(match.getId())
                .stream().filter(MatchAttendance::isAwaitingReplacement)
                .filter(item -> !item.getUserId().equals(attendance.getUserId()))
                .min(Comparator.comparing(MatchAttendance::getReplacementRequiredAt)).orElse(null);
        if (departed != null) {
            departed.fillReplacement(attendance.getUserId(), now);
            notifications.enqueue(match.getId(), departed.getUserId(), MatchNotificationType.REPLACEMENT_FILLED,
                    "match:" + match.getId() + ":waitlist-replacement:" + order, now);
        }
        assignments.deleteAllByMatchId(match.getId());
        notifications.enqueue(match.getId(), attendance.getUserId(), MatchNotificationType.WAITLIST_PROMOTED,
                "match:" + match.getId() + ":waitlist-promoted:" + order, now);
        if (capacity.isFull(match)) {
            notifications.enqueue(match.getId(), null, MatchNotificationType.TEAM_FULL,
                    "match:" + match.getId() + ":waitlist-full:" + order, now);
        }
    }

    private void notifyDecisions(FootballMatch match, Instant now) {
        if (!match.isAttendanceOpen(now)) return;
        boolean missingKeeper = match.getSignupDeadline() != null && now.isAfter(match.getSignupDeadline())
                && capacity.reservedGoalkeeperSlots(match) > 0;
        boolean guests = guestDecisionRequired(match);
        if (!missingKeeper && !guests) return;
        String guestKey = guests ? guestPreferenceKey(match, waiting(match).get(0)) : null;
        for (GroupMember manager : memberRepository.findAllByGroupIdOrderByCreatedAtAsc(match.getGroupId())) {
            if (!manager.hasPermission(GroupAdminPermission.MANAGE_MATCH_PLAYERS)) continue;
            if (missingKeeper) {
                notifications.enqueue(match.getId(), manager.getUserId(), MatchNotificationType.WAITLIST_GOALKEEPER_DECISION,
                        "match:" + match.getId() + ":waitlist-keeper:" + match.getSignupDeadline().toEpochMilli()
                                + ":" + capacity.reservedGoalkeeperSlots(match) + ":" + manager.getUserId(), now);
            }
            if (guests) {
                notifications.enqueue(match.getId(), manager.getUserId(), MatchNotificationType.WAITLIST_GUEST_DECISION,
                        "match:" + match.getId() + ":waitlist-guests:" + guestKey + ":" + manager.getUserId(), now);
            }
        }
    }

    public static final class WaitlistConflictException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public WaitlistConflictException(String message) { super(message); }
    }
}
