package com.onze.api.match;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.onze.api.IntegrationTestSupport;
import com.onze.api.auth.AuthModels.AuthResponse;
import com.onze.api.group.GroupInviteModels.InviteResponse;
import com.onze.api.group.GroupModels.GroupResponse;
import com.onze.api.match.MatchModels.MatchResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "security.jwt.secret=onze-waitlist-integration-secret-with-at-least-32-bytes",
        "notifications.expo.enabled=false", "matches.processing.initial-delay-ms=3600000"
})
@AutoConfigureMockMvc
class MatchWaitlistIntegrationTest extends IntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MatchLifecycleService lifecycle;
    private AuthResponse admin;
    private UUID groupId;
    private String invite;
    private int serial;

    @BeforeEach
    void setup() throws Exception {
        jdbc.update("DELETE FROM groups");
        jdbc.update("DELETE FROM password_reset_codes");
        jdbc.update("DELETE FROM users");
        admin = register("Principal");
        groupId = read(mvc.perform(post("/api/groups").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Fila de espera\"}"))
                .andExpect(status().isCreated()), GroupResponse.class).id();
        invite = read(mvc.perform(post("/api/groups/{id}/invite", groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isOk()), InviteResponse.class).code();
        position(admin, "DEFENDER");
    }

    @Test
    void shouldReserveTwoOfFourteenPlacesAndKeepQueueOrderWithoutChargingWaiters() throws Exception {
        UUID match = create(14, false, "NONE");
        List<AuthResponse> players = new ArrayList<>();
        for (int n = 0; n < 14; n++) {
            AuthResponse player = member("Linha " + n, "DEFENDER");
            players.add(player);
            attend(match, player, "GOING").andExpect(status().isOk())
                    .andExpect(jsonPath("$.goingCount").value(Math.min(n + 1, 12)));
        }
        MatchResponse queued = getMatch(match, players.get(12));
        assertThat(queued.reservedGoalkeeperSlots()).isEqualTo(2);
        assertThat(queued.waitlistCount()).isEqualTo(2);
        assertThat(queued.myWaitlistPosition()).isEqualTo(1);
        assertThat(queued.myPaymentStatus()).isNull();
        attend(match, players.get(12), "GOING").andExpect(status().isOk())
                .andExpect(jsonPath("$.myWaitlistPosition").value(1));
        attend(match, member("Goleiro 1", "GOALKEEPER"), "GOING").andExpect(status().isOk());
        attend(match, member("Goleiro 2", "GOALKEEPER"), "GOING").andExpect(status().isOk())
                .andExpect(jsonPath("$.goingCount").value(14))
                .andExpect(jsonPath("$.currentGoalkeepers").value(2))
                .andExpect(jsonPath("$.waitlistCount").value(2));
    }

    @Test
    void shouldPromoteFirstOnWithdrawalAndPutRejoiningPlayerAtTheEnd() throws Exception {
        UUID match = create(4, true, "NONE");
        rentals(match);
        AuthResponse a = member("A", "DEFENDER"), b = member("B", "ATTACKER");
        AuthResponse first = member("Primeiro", "DEFENDER"), second = member("Segundo", "DEFENDER");
        for (AuthResponse player : List.of(a, b, first, second)) attend(match, player, "GOING").andExpect(status().isOk());
        assertThat(getMatch(match, first).myPaymentStatus()).isNull();
        attend(match, a, "NOT_GOING").andExpect(status().isOk()).andExpect(jsonPath("$.goingCount").value(4));
        MatchResponse promoted = getMatch(match, first);
        assertThat(promoted.myAttendance()).isEqualTo(AttendanceStatus.GOING);
        assertThat(promoted.myPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(promoted.myWaitlistPosition()).isNull();
        assertThat(getMatch(match, second).myWaitlistPosition()).isEqualTo(1);
        attend(match, a, "GOING").andExpect(status().isOk()).andExpect(jsonPath("$.myWaitlistPosition").value(2));
        assertThat(jobs(match, "WAITLIST_PROMOTED")).isEqualTo(1);
    }

    @Test
    void shouldKeepKeeperVacancyReservedAndAdmitTheFirstWaitingKeeper() throws Exception {
        UUID match = create(4, false, "NONE");
        AuthResponse keeper = member("Goleiro", "GOALKEEPER");
        AuthResponse secondKeeper = member("Goleiro 2", "GOALKEEPER");
        AuthResponse field = member("Linha", "DEFENDER"), field2 = member("Linha 2", "DEFENDER");
        AuthResponse waitingField = member("Linha esperando", "DEFENDER");
        AuthResponse waitingKeeper = member("Goleiro esperando", "GOALKEEPER");
        for (AuthResponse player : List.of(keeper, secondKeeper, field, field2, waitingField, waitingKeeper)) {
            attend(match, player, "GOING").andExpect(status().isOk());
        }
        attend(match, keeper, "NOT_GOING").andExpect(status().isOk());
        assertThat(getMatch(match, waitingKeeper).myAttendance()).isEqualTo(AttendanceStatus.GOING);
        assertThat(getMatch(match, waitingField).myWaitlistPosition()).isEqualTo(1);
        assertThat(getMatch(match, waitingField).goingCount()).isEqualTo(4);
    }

    @Test
    void shouldNotifyManagersAtDeadlineAndRequireExplicitFifoApprovalForReserveInGoal() throws Exception {
        UUID match = create(4, true, "NONE");
        List<AuthResponse> players = new ArrayList<>();
        for (int n = 0; n < 4; n++) {
            AuthResponse player = member("Reserva " + n, "DEFENDER");
            players.add(player); attend(match, player, "GOING").andExpect(status().isOk());
        }
        useKeeper(match, admin, players.get(2)).andExpect(status().isConflict());
        expireSignup(match);
        lifecycle.openDueAttendances(); lifecycle.openDueAttendances();
        assertThat(jobs(match, "WAITLIST_GOALKEEPER_DECISION")).isEqualTo(1);
        useKeeper(match, players.get(0), players.get(2)).andExpect(status().isForbidden());
        useKeeper(match, admin, players.get(3)).andExpect(status().isConflict());
        useKeeper(match, admin, players.get(2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentGoalkeepers").value(1));
        MatchResponse first = getMatch(match, players.get(2));
        assertThat(first.myAttendance()).isEqualTo(AttendanceStatus.GOING);
        assertThat(first.myPaymentStatus()).isNull(); // goalkeeperPays=false
        assertThat(first.attendances().stream().filter(a -> a.currentUser()).findFirst().orElseThrow().primaryPosition().name())
                .isEqualTo("DEFENDER");
        useKeeper(match, admin, players.get(3)).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentGoalkeepers").value(2))
                .andExpect(jsonPath("$.waitlistCount").value(0));
        assertThat(jobs(match, "WAITLIST_PROMOTED")).isEqualTo(2);
        attend(match, member("Atrasado", "GOALKEEPER"), "GOING").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIGNUP_DEADLINE_PASSED"));
    }

    @Test
    void shouldRequireGuestPreferenceAndKeepOrReplaceOnlyWithFirstMember() throws Exception {
        UUID match = create(3, false, "NONE");
        rentals(match);
        UUID guest = addGuest(match, "Convidado", "DEFENDER");
        AuthResponse first = member("Primeiro", "DEFENDER"), second = member("Segundo", "DEFENDER");
        attend(match, first, "GOING").andExpect(status().isOk());
        attend(match, second, "GOING").andExpect(status().isOk());
        assertThat(getMatch(match, admin).guestPreferenceDecisionRequired()).isTrue();
        assertThat(getMatch(match, admin).guests()).hasSize(1);
        decideGuest(match, first, first, guest, "PROMOTE_MEMBER").andExpect(status().isForbidden());
        decideGuest(match, admin, second, guest, "PROMOTE_MEMBER").andExpect(status().isConflict());
        decideGuest(match, admin, first, guest, "KEEP_GUESTS").andExpect(status().isOk())
                .andExpect(jsonPath("$.guestPreferenceDecisionRequired").value(false));
        assertThat(getMatch(match, first).myWaitlistPosition()).isEqualTo(1);
        attend(match, first, "NOT_GOING").andExpect(status().isOk());
        assertThat(getMatch(match, admin).guestPreferenceDecisionRequired()).isTrue();
        decideGuest(match, admin, second, guest, "PROMOTE_MEMBER").andExpect(status().isOk())
                .andExpect(jsonPath("$.guests.length()").value(0))
                .andExpect(jsonPath("$.goingCount").value(3));
        assertThat(getMatch(match, second).myAttendance()).isEqualTo(AttendanceStatus.GOING);
        assertThat(jobs(match, "WAITLIST_GUEST_DECISION")).isEqualTo(2);
    }

    @Test
    void shouldPromoteAfterPaymentDeadlineWithReplacementWindowAndNeverChargeWaitingMember() throws Exception {
        UUID match = create(3, true, "NONE");
        rentals(match);
        AuthResponse unpaid = member("Não pagou", "DEFENDER"), waiting = member("Fila", "DEFENDER");
        attend(match, unpaid, "GOING").andExpect(status().isOk());
        attend(match, waiting, "GOING").andExpect(status().isOk());
        assertThat(getMatch(match, waiting).myPaymentStatus()).isNull();
        jdbc.update("UPDATE football_matches SET signup_deadline=NOW()-INTERVAL '2 minutes', payment_deadline=NOW()-INTERVAL '1 minute' WHERE id=?", match);
        lifecycle.openDueAttendances(); lifecycle.openDueAttendances();
        MatchResponse promoted = getMatch(match, waiting);
        assertThat(promoted.myAttendance()).isEqualTo(AttendanceStatus.GOING);
        assertThat(promoted.canReportPayment()).isTrue();
        assertThat(getMatch(match, unpaid).myPaymentDeadlineRemovedAt()).isNotNull();
        mvc.perform(put("/api/matches/{id}/payment/reported", match).header(HttpHeaders.AUTHORIZATION, bearer(waiting)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.myPaymentStatus").value("REPORTED"));
        assertThat(jobs(match, "PAYMENT_DEADLINE_REMOVAL")).isEqualTo(1);
    }

    @Test
    void shouldUnlockPaidDepartureSettlementWhenWaiterFillsTheVacancy() throws Exception {
        UUID match = create(3, true, "NONE");
        rentals(match);
        AuthResponse paid = member("Pagou", "DEFENDER"), waiting = member("Fila", "DEFENDER");
        attend(match, paid, "GOING").andExpect(status().isOk());
        mvc.perform(put("/api/matches/{id}/payment/reported", match).header(HttpHeaders.AUTHORIZATION, bearer(paid)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/matches/{id}/payments/{userId}/confirm", match, paid.user().id())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isOk());
        attend(match, waiting, "GOING").andExpect(status().isOk());
        attend(match, paid, "NOT_GOING").andExpect(status().isOk());
        var departure = getMatch(match, admin).attendances().stream()
                .filter(a -> a.userId().equals(paid.user().id())).findFirst().orElseThrow();
        assertThat(departure.replacementUserId()).isEqualTo(waiting.user().id());
        assertThat(departure.settlementAvailable()).isTrue();
        assertThat(jobs(match, "REPLACEMENT_FILLED")).isEqualTo(1);
    }

    @Test
    void shouldSerializeConcurrentQueueEntriesAndKeepWeeklyOccurrencesIndependent() throws Exception {
        UUID match = create(3, false, "WEEKLY");
        rentals(match);
        AuthResponse playing = member("Titular", "DEFENDER");
        attend(match, playing, "GOING").andExpect(status().isOk());
        AuthResponse a = member("A", "DEFENDER"), b = member("B", "DEFENDER");
        var first = CompletableFuture.runAsync(() -> joinUnchecked(match, a));
        var second = CompletableFuture.runAsync(() -> joinUnchecked(match, b));
        CompletableFuture.allOf(first, second).join();
        MatchResponse before = getMatch(match, admin);
        var waiting = before.attendances().stream().filter(p -> p.status() == AttendanceStatus.WAITLISTED)
                .sorted(java.util.Comparator.comparing(MatchModels.AttendanceResponse::waitlistPosition)).toList();
        assertThat(waiting).hasSize(2);
        assertThat(waiting.get(0).waitlistPosition()).isEqualTo(1);
        assertThat(waiting.get(1).waitlistPosition()).isEqualTo(2);
        attend(match, playing, "NOT_GOING").andExpect(status().isOk());
        MatchResponse after = getMatch(match, admin);
        assertThat(after.goingCount()).isEqualTo(3);
        assertThat(after.attendances().stream().filter(p -> p.userId().equals(waiting.get(0).userId()))
                .findFirst().orElseThrow().status()).isEqualTo(AttendanceStatus.GOING);
        MatchResponse[] occurrences = read(mvc.perform(get("/api/groups/{id}/matches", groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isOk()), MatchResponse[].class);
        assertThat(occurrences).hasSize(4);
        assertThat(List.of(occurrences).stream().filter(p -> !p.id().equals(match))).allMatch(p -> p.waitlistCount() == 0);
    }

    @Test
    void shouldReserveConfiguredGoalkeeperCountAndAllowAnInvitedKeeperOnlyInReservedPlace() throws Exception {
        UUID match = create(3, false, "NONE");
        attend(match, member("Linha", "DEFENDER"), "GOING").andExpect(status().isOk());
        mvc.perform(post("/api/matches/{id}/guests", match).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Linha extra\",\"primaryPosition\":\"DEFENDER\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WAITLIST_CONFLICT"));
        addGuest(match, "Goleiro convidado", "GOALKEEPER");
        assertThat(getMatch(match, admin).reservedGoalkeeperSlots()).isEqualTo(1);
        AuthResponse waiting = member("Fila", "DEFENDER");
        attend(match, waiting, "GOING").andExpect(status().isOk()).andExpect(jsonPath("$.myAttendance").value("WAITLISTED"));
        assertThat(getMatch(match, admin).replaceableGuestIds()).isEmpty();
        mvc.perform(put("/api/matches/{id}/attendance", match).header(HttpHeaders.AUTHORIZATION, bearer(waiting))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"WAITLISTED\"}"))
                .andExpect(status().isBadRequest());
    }

    private void joinUnchecked(UUID match, AuthResponse user) {
        try { attend(match, user, "GOING").andExpect(status().isOk()); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void shouldReleaseWaitingCreditToNextMatchAndApplyItOnceWhenPromoted() throws Exception {
        UUID match = create(3, true, "NONE");
        rentals(match);
        AuthResponse playing = member("Titular", "DEFENDER"), waiting = member("Com crédito", "DEFENDER");
        attend(match, playing, "GOING").andExpect(status().isOk());
        jdbc.update("INSERT INTO group_player_credits(id,group_id,user_id,balance,created_at,updated_at) VALUES(?,?,?,20,NOW(),NOW())",
                UUID.randomUUID(), groupId, waiting.user().id());
        UUID next = create(3, true, "NONE", 12);
        assertThat(getMatch(match, waiting).myCreditAppliedAmount()).isEqualByComparingTo("20");
        attend(match, waiting, "GOING").andExpect(status().isOk());
        assertThat(getMatch(match, waiting).myPaymentStatus()).isNull();
        assertThat(getMatch(next, waiting).myCreditAppliedAmount()).isEqualByComparingTo("20");
        attend(match, playing, "NOT_GOING").andExpect(status().isOk());
        MatchResponse promoted = getMatch(match, waiting);
        assertThat(promoted.myPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(promoted.myCreditAppliedAmount()).isEqualByComparingTo("20");
        assertThat(getMatch(next, waiting).myCreditAppliedAmount()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT balance FROM group_player_credits WHERE group_id=? AND user_id=?",
                java.math.BigDecimal.class, groupId, waiting.user().id())).isEqualByComparingTo("0");
    }

    private UUID create(int max, boolean paid, String recurrence) throws Exception {
        return create(max, paid, recurrence, 5);
    }

    private UUID create(int max, boolean paid, String recurrence, int daysAhead) throws Exception {
        LocalDate day = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(daysAhead);
        return read(mvc.perform(post("/api/groups/{id}/matches", groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.ofEntries(
                        Map.entry("date", day.toString()), Map.entry("startTime", "20:00:00"),
                        Map.entry("timeZone", "America/Sao_Paulo"), Map.entry("venue", "Arena"),
                        Map.entry("maxPlayers", max), Map.entry("minimumPlayers", 2),
                        Map.entry("paymentRequired", paid), Map.entry("goalkeeperPays", false),
                        Map.entry("paymentAmount", 20), Map.entry("pixKey", "pix@onze.test"),
                        Map.entry("recurrence", recurrence)))))
                .andExpect(status().isCreated()), MatchResponse.class).id();
    }

    private void rentals(UUID match) throws Exception {
        for (String name : List.of("Goleiro aluguel 1", "Goleiro aluguel 2")) {
            mvc.perform(post("/api/matches/{id}/rental-goalkeepers", match)
                    .header(HttpHeaders.AUTHORIZATION, bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("displayName", name)))).andExpect(status().isOk());
        }
    }

    private UUID addGuest(UUID match, String name, String position) throws Exception {
        MatchResponse updated = read(mvc.perform(post("/api/matches/{id}/guests", match)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("displayName", name, "primaryPosition", position))))
                .andExpect(status().isOk()), MatchResponse.class);
        return updated.guests().stream().filter(g -> g.displayName().equals(name)).findFirst().orElseThrow().id();
    }

    private ResultActions attend(UUID match, AuthResponse user, String state) throws Exception {
        return mvc.perform(put("/api/matches/{id}/attendance", match).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("status", state))));
    }

    private ResultActions useKeeper(UUID match, AuthResponse actor, AuthResponse first) throws Exception {
        return mvc.perform(post("/api/matches/{id}/waitlist/goalkeeper", match).header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedFirstUserId", first.user().id()))));
    }

    private ResultActions decideGuest(UUID match, AuthResponse actor, AuthResponse first, UUID guest, String action) throws Exception {
        return mvc.perform(post("/api/matches/{id}/waitlist/guest-preference", match).header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "expectedFirstUserId", first.user().id(), "guestId", guest, "action", action))));
    }

    private void expireSignup(UUID match) { jdbc.update("UPDATE football_matches SET signup_deadline=NOW()-INTERVAL '1 minute' WHERE id=?", match); }
    private int jobs(UUID match, String type) { return jdbc.queryForObject("SELECT count(*) FROM match_notification_jobs WHERE match_id=? AND notification_type=?", Integer.class, match, type); }
    private MatchResponse getMatch(UUID match, AuthResponse user) throws Exception {
        return read(mvc.perform(get("/api/matches/{id}", match).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk()), MatchResponse.class);
    }
    private AuthResponse member(String name, String position) throws Exception {
        AuthResponse user = register(name);
        mvc.perform(post("/api/groups/join").header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("code", invite))))
                .andExpect(status().isOk());
        position(user, position);
        return user;
    }
    private void position(AuthResponse user, String position) throws Exception {
        mvc.perform(put("/api/groups/{id}/members/me/sports-profile", groupId).header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "primaryPosition", position, "dominantFoot", "RIGHT", "canPlayGoalkeeper", false))))
                .andExpect(status().isOk());
    }
    private AuthResponse register(String name) throws Exception {
        return read(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "waitlist-" + ++serial + "@onze.test",
                        "password", "StrongPass123!", "displayName", name))))
                .andExpect(status().isCreated()), AuthResponse.class);
    }
    private String bearer(AuthResponse user) { return "Bearer " + user.accessToken(); }
    private <T> T read(ResultActions result, Class<T> type) throws Exception {
        return json.readValue(result.andReturn().getResponse().getContentAsString(), type);
    }
}
