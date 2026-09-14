package com.onze.api.match;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.onze.api.auth.AuthModels.AuthResponse;
import com.onze.api.auth.PasswordResetCodeRepository;
import com.onze.api.group.GroupInviteModels.InviteResponse;
import com.onze.api.group.GroupInviteRepository;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupModels.GroupResponse;
import com.onze.api.group.GroupRepository;
import com.onze.api.group.GroupScheduleRepository;
import com.onze.api.match.MatchModels.AttendanceResponse;
import com.onze.api.match.MatchModels.MatchResponse;
import com.onze.api.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "security.jwt.secret=onze-goalkeeper-integration-secret-with-at-least-32-bytes",
        "security.jwt.issuer=onze-api-goalkeeper-integration-test",
        "notifications.expo.enabled=false",
        "matches.processing.initial-delay-ms=3600000"
})
@AutoConfigureMockMvc
class MatchGoalkeeperIntegrationTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("onze_goalkeeper_test")
            .withUsername("onze")
            .withPassword("onze");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MatchLifecycleService lifecycleService;

    @Autowired
    private MatchNotificationJobRepository notificationJobRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private GroupScheduleRepository groupScheduleRepository;

    @Autowired
    private GroupInviteRepository groupInviteRepository;

    @Autowired
    private PasswordResetCodeRepository resetCodeRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        groupInviteRepository.deleteAll();
        groupScheduleRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        resetCodeRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldDefaultGoalkeepersToPayAndPreserveConfiguredRuleInWeeklySeries() throws Exception {
        AuthResponse creator = register("goalkeeper-config@example.com", "Principal Configuração");
        GroupResponse group = createGroup(creator, "Pelada configuração");
        LocalDate date = LocalDate.now(SAO_PAULO).plusDays(3);

        createMatch(creator, group.id(), paidMatchBody(date, 12, "NONE", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.goalkeeperPays").value(true));
        createMatch(creator, group.id(), paidMatchBody(date.plusDays(1), 12, "NONE", true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.goalkeeperPays").value(true));
        createMatch(creator, group.id(), paidMatchBody(date.plusDays(2), 12, "WEEKLY", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.goalkeeperPays").value(false));

        MatchResponse[] matches = listGroupMatches(creator, group.id());
        assertThat(matches)
                .filteredOn(match -> match.recurrence() == MatchRecurrence.WEEKLY)
                .hasSize(2)
                .allMatch(match -> !match.goalkeeperPays());
    }

    @Test
    void shouldManageConfirmedGoalkeeperIdempotentlyAndApplyTheConfiguredPaymentRule() throws Exception {
        AuthResponse creator = register("goalkeeper-admin@example.com", "Principal Goleiros");
        AuthResponse goalkeeper = register("goalkeeper-player@example.com", "Goleiro");
        AuthResponse otherMember = register("goalkeeper-other@example.com", "Outro membro");
        GroupResponse group = createGroup(creator, "Pelada goleiros");
        InviteResponse invite = createInvite(creator, group.id());
        join(goalkeeper, invite.code());
        join(otherMember, invite.code());

        MatchResponse freeGoalkeeperMatch = readMatch(createMatch(
                creator,
                group.id(),
                paidMatchBody(LocalDate.now(SAO_PAULO).plusDays(3), 10, "NONE", false))
                .andExpect(status().isCreated())
                .andReturn());
        confirmAttendance(freeGoalkeeperMatch.id(), goalkeeper, "GOING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myPaymentStatus").value("PENDING"));

        updateGoalkeeper(freeGoalkeeperMatch.id(), goalkeeper.user().id(), otherMember, true)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_ACCESS_DENIED"));

        for (int attempt = 0; attempt < 2; attempt++) {
            updateGoalkeeper(freeGoalkeeperMatch.id(), goalkeeper.user().id(), creator, true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.attendances[0].isGoalkeeper").value(true))
                    .andExpect(jsonPath("$.attendances[0].paymentExempt").value(true))
                    .andExpect(jsonPath("$.attendances[0].paymentStatus").value(nullValue()))
                    .andExpect(jsonPath("$.attendances[0].remainingPaymentAmount").value(0));
        }

        mockMvc.perform(get("/api/matches/{matchId}", freeGoalkeeperMatch.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(goalkeeper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myPaymentStatus").value(nullValue()))
                .andExpect(jsonPath("$.myRemainingPaymentAmount").value(0))
                .andExpect(jsonPath("$.canReportPayment").value(false));
        mockMvc.perform(put("/api/matches/{matchId}/payment/reported", freeGoalkeeperMatch.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(goalkeeper)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOALKEEPER_PAYMENT_EXEMPT"));

        updateGoalkeeper(freeGoalkeeperMatch.id(), goalkeeper.user().id(), creator, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendances[0].isGoalkeeper").value(false))
                .andExpect(jsonPath("$.attendances[0].paymentExempt").value(false))
                .andExpect(jsonPath("$.attendances[0].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.attendances[0].remainingPaymentAmount").value(20));

        MatchResponse payingGoalkeeperMatch = readMatch(createMatch(
                creator,
                group.id(),
                paidMatchBody(LocalDate.now(SAO_PAULO).plusDays(4), 10, "NONE", true))
                .andExpect(status().isCreated())
                .andReturn());
        confirmAttendance(payingGoalkeeperMatch.id(), goalkeeper, "GOING").andExpect(status().isOk());
        updateGoalkeeper(payingGoalkeeperMatch.id(), goalkeeper.user().id(), creator, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendances[0].isGoalkeeper").value(true))
                .andExpect(jsonPath("$.attendances[0].paymentExempt").value(false))
                .andExpect(jsonPath("$.attendances[0].paymentStatus").value("PENDING"));
    }

    @Test
    void shouldNeverSilentlyExemptReportedOrConfirmedCashPayments() throws Exception {
        AuthResponse creator = register("goalkeeper-money-admin@example.com", "Principal Financeiro");
        AuthResponse reported = register("goalkeeper-reported@example.com", "Goleiro Informado");
        AuthResponse paid = register("goalkeeper-paid@example.com", "Goleiro Pago");
        GroupResponse group = createGroup(creator, "Pelada dinheiro protegido");
        InviteResponse invite = createInvite(creator, group.id());
        join(reported, invite.code());
        join(paid, invite.code());
        MatchResponse match = readMatch(createMatch(
                creator,
                group.id(),
                paidMatchBody(LocalDate.now(SAO_PAULO).plusDays(3), 10, "NONE", false))
                .andExpect(status().isCreated())
                .andReturn());

        for (AuthResponse player : List.of(reported, paid)) {
            confirmAttendance(match.id(), player, "GOING").andExpect(status().isOk());
            mockMvc.perform(put("/api/matches/{matchId}/payment/reported", match.id())
                            .header(HttpHeaders.AUTHORIZATION, bearer(player)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(put(
                        "/api/matches/{matchId}/payments/{playerUserId}/confirm",
                        match.id(),
                        paid.user().id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk());

        for (AuthResponse player : List.of(reported, paid)) {
            updateGoalkeeper(match.id(), player.user().id(), creator, true)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GOALKEEPER_PAYMENT_ALREADY_RECORDED"));
        }
    }

    @Test
    void shouldReturnAppliedCreditAndExcludeExemptGoalkeeperFromDeadlineAndReminderFlows() throws Exception {
        AuthResponse creator = register("goalkeeper-credit-admin@example.com", "Principal Crédito");
        AuthResponse goalkeeper = register("goalkeeper-credit-player@example.com", "Goleiro Crédito");
        GroupResponse group = createGroup(creator, "Pelada crédito goleiro");
        InviteResponse invite = createInvite(creator, group.id());
        join(goalkeeper, invite.code());

        jdbcTemplate.update(
                """
                        INSERT INTO group_player_credits
                            (id, group_id, user_id, balance, created_at, updated_at)
                        VALUES (?, ?, ?, 20.00, NOW(), NOW())
                        """,
                UUID.randomUUID(),
                group.id(),
                goalkeeper.user().id());

        MatchResponse match = readMatch(createMatch(
                creator,
                group.id(),
                paidMatchBody(LocalDate.now(SAO_PAULO).plusDays(3), 10, "NONE", false))
                .andExpect(status().isCreated())
                .andReturn());
        confirmAttendance(match.id(), goalkeeper, "GOING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myPaymentStatus").value("PAID"))
                .andExpect(jsonPath("$.myCreditAllocationStatus").value("APPLIED"));

        updateGoalkeeper(match.id(), goalkeeper.user().id(), creator, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendances[0].paymentExempt").value(true))
                .andExpect(jsonPath("$.attendances[0].paymentStatus").value(nullValue()))
                .andExpect(jsonPath("$.attendances[0].creditAppliedAmount").value(0));
        mockMvc.perform(get("/api/groups/{groupId}/credits", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availableAmount").value(20))
                .andExpect(jsonPath("$[0].allocationStatus").value(nullValue()));

        jdbcTemplate.update(
                """
                        UPDATE football_matches
                        SET attendance_opened_at = NOW() - INTERVAL '2 days',
                            signup_deadline = NOW() - INTERVAL '2 minutes',
                            payment_deadline = NOW() - INTERVAL '1 minute'
                        WHERE id = ?
                """,
                match.id());
        lifecycleService.openDueAttendances();
        mockMvc.perform(get("/api/matches/{matchId}", match.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(goalkeeper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myAttendance").value("GOING"))
                .andExpect(jsonPath("$.myPaymentStatus").value(nullValue()))
                .andExpect(jsonPath("$.myPaymentDeadlineRemovedAt").value(nullValue()));

        lifecycleService.scheduleDueAttendanceReminders();
        assertThat(notificationJobRepository.findAll())
                .filteredOn(job -> job.getRecipientUserId() != null
                        && job.getRecipientUserId().equals(goalkeeper.user().id()))
                .extracting(MatchNotificationJob::getNotificationType)
                .doesNotContain(
                        MatchNotificationType.PAYMENT_REMINDER,
                        MatchNotificationType.PAYMENT_DEADLINE_REMOVAL);
    }

    @Test
    void shouldManageRentalGoalkeeperAsAnExternalOccupiedSpotOnlyInTheCurrentOccurrence() throws Exception {
        AuthResponse creator = register("rental-admin@example.com", "Principal Aluguel");
        AuthResponse member = register("rental-member@example.com", "Jogador do Grupo");
        GroupResponse group = createGroup(creator, "Pelada goleiro de aluguel");
        InviteResponse invite = createInvite(creator, group.id());
        join(member, invite.code());

        MatchResponse first = readMatch(createMatch(
                creator,
                group.id(),
                paidMatchBody(LocalDate.now(SAO_PAULO).plusDays(3), 2, "WEEKLY", false))
                .andExpect(status().isCreated())
                .andReturn());
        confirmAttendance(first.id(), creator, "GOING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goingCount").value(1));

        addRentalGoalkeeper(first.id(), member, "Goleiro externo")
                .andExpect(status().isForbidden());
        var addResult = addRentalGoalkeeper(first.id(), creator, "  Goleiro externo  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goingCount").value(2))
                .andExpect(jsonPath("$.rentalGoalkeepers.length()").value(1))
                .andExpect(jsonPath("$.rentalGoalkeepers[0].displayName").value("Goleiro externo"))
                .andReturn();
        MatchResponse withRental = readMatch(addResult);

        confirmAttendance(first.id(), member, "GOING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_FULL"));
        addRentalGoalkeeper(first.id(), creator, "Outro aluguel")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_FULL"));
        assertThat(notificationJobRepository.findAll())
                .extracting(MatchNotificationJob::getNotificationType)
                .contains(MatchNotificationType.TEAM_FULL);

        MatchResponse[] weeklyMatches = listGroupMatches(creator, group.id());
        MatchResponse second = Arrays.stream(weeklyMatches)
                .filter(candidate -> candidate.recurrence() == MatchRecurrence.WEEKLY)
                .filter(candidate -> !candidate.id().equals(first.id()))
                .findFirst()
                .orElseThrow();
        assertThat(second.goalkeeperPays()).isFalse();
        assertThat(second.rentalGoalkeepers()).isEmpty();

        UUID rentalId = withRental.rentalGoalkeepers().get(0).id();
        mockMvc.perform(delete(
                        "/api/matches/{matchId}/rental-goalkeepers/{rentalGoalkeeperId}",
                        first.id(),
                        rentalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goingCount").value(1))
                .andExpect(jsonPath("$.rentalGoalkeepers.length()").value(0));
        confirmAttendance(first.id(), member, "GOING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goingCount").value(2));
        mockMvc.perform(delete(
                        "/api/matches/{matchId}/rental-goalkeepers/{rentalGoalkeeperId}",
                        first.id(),
                        rentalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RENTAL_GOALKEEPER_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions createMatch(
            AuthResponse user,
            UUID groupId,
            String body) throws Exception {
        return mockMvc.perform(post("/api/groups/{groupId}/matches", groupId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions confirmAttendance(
            UUID matchId,
            AuthResponse user,
            String attendance) throws Exception {
        return mockMvc.perform(put("/api/matches/{matchId}/attendance", matchId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s"}
                        """.formatted(attendance)));
    }

    private org.springframework.test.web.servlet.ResultActions updateGoalkeeper(
            UUID matchId,
            UUID playerUserId,
            AuthResponse admin,
            boolean goalkeeper) throws Exception {
        return mockMvc.perform(put(
                        "/api/matches/{matchId}/goalkeepers/{playerUserId}",
                        matchId,
                        playerUserId)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"isGoalkeeper": %s}
                        """.formatted(goalkeeper)));
    }

    private org.springframework.test.web.servlet.ResultActions addRentalGoalkeeper(
            UUID matchId,
            AuthResponse admin,
            String displayName) throws Exception {
        return mockMvc.perform(post("/api/matches/{matchId}/rental-goalkeepers", matchId)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName": "%s"}
                        """.formatted(displayName)));
    }

    private MatchResponse[] listGroupMatches(AuthResponse user, UUID groupId) throws Exception {
        var result = mockMvc.perform(get("/api/groups/{groupId}/matches", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readValue(result.getResponse().getContentAsString(), MatchResponse[].class);
    }

    private MatchResponse readMatch(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return jsonMapper.readValue(result.getResponse().getContentAsString(), MatchResponse.class);
    }

    private String paidMatchBody(
            LocalDate date,
            int maxPlayers,
            String recurrence,
            Boolean goalkeeperPays) {
        String goalkeeperPaysProperty = goalkeeperPays == null
                ? ""
                : "\"goalkeeperPays\": " + goalkeeperPays + ",";
        return """
                {
                  "date": "%s",
                  "startTime": "20:30:00",
                  "timeZone": "America/Sao_Paulo",
                  "venue": "Arena Onze",
                  "maxPlayers": %d,
                  "paymentRequired": true,
                  %s
                  "paymentAmount": 20.00,
                  "pixKey": "goleiros@onze.app",
                  "notes": "Regra de goleiros",
                  "recurrence": "%s"
                }
                """.formatted(date, maxPlayers, goalkeeperPaysProperty, recurrence);
    }

    private void join(AuthResponse user, String code) throws Exception {
        mockMvc.perform(post("/api/groups/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s"}
                                """.formatted(code)))
                .andExpect(status().isOk());
    }

    private InviteResponse createInvite(AuthResponse creator, UUID groupId) throws Exception {
        var result = mockMvc.perform(post("/api/groups/{groupId}/invite", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readValue(result.getResponse().getContentAsString(), InviteResponse.class);
    }

    private GroupResponse createGroup(AuthResponse creator, String name) throws Exception {
        var result = mockMvc.perform(post("/api/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "%s"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonMapper.readValue(result.getResponse().getContentAsString(), GroupResponse.class);
    }

    private AuthResponse register(String email, String displayName) throws Exception {
        var result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "StrongPass123!",
                                  "displayName": "%s"
                                }
                                """.formatted(email, displayName)))
                .andExpect(status().isCreated())
                .andReturn();
        return jsonMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private String bearer(AuthResponse response) {
        return "Bearer " + response.accessToken();
    }
}
