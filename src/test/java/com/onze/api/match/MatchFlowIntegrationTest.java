package com.onze.api.match;

import java.time.LocalDate;
import java.time.ZoneId;

import com.onze.api.auth.AuthModels.AuthResponse;
import com.onze.api.auth.PasswordResetCodeRepository;
import com.onze.api.group.GroupInviteModels.InviteResponse;
import com.onze.api.group.GroupInviteRepository;
import com.onze.api.group.GroupMember;
import com.onze.api.group.GroupMemberRepository;
import com.onze.api.group.GroupRepository;
import com.onze.api.group.GroupScheduleRepository;
import com.onze.api.group.GroupModels.GroupResponse;
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
        "security.jwt.secret=onze-match-integration-secret-with-at-least-32-bytes",
        "security.jwt.issuer=onze-api-match-integration-test",
        "notifications.expo.enabled=false",
        "matches.processing.initial-delay-ms=3600000"
})
@AutoConfigureMockMvc
class MatchFlowIntegrationTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("onze_match_test")
            .withUsername("onze")
            .withPassword("onze");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Autowired
    private FootballMatchRepository matchRepository;

    @Autowired
    private MatchNotificationJobRepository notificationJobRepository;

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
    void shouldCreateOneOffMatchLetMembersConfirmAndEnforcePlayerLimit() throws Exception {
        AuthResponse creator = register("match-primary@example.com", "Principal");
        AuthResponse firstMember = register("match-first@example.com", "Primeiro");
        AuthResponse secondMember = register("match-second@example.com", "Segundo");
        GroupResponse group = createGroup(creator, "Pelada avulsa");
        InviteResponse invite = createInvite(creator, group.id());
        join(firstMember, invite.code());
        join(secondMember, invite.code());

        LocalDate date = LocalDate.now(SAO_PAULO).plusDays(3);
        var createResult = mockMvc.perform(post("/api/groups/{groupId}/matches", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(matchBody(date, "NONE", 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrence").value("NONE"))
                .andExpect(jsonPath("$.attendanceOpen").value(true))
                .andExpect(jsonPath("$.goingCount").value(0))
                .andReturn();
        MatchResponse match = jsonMapper.readValue(
                createResult.getResponse().getContentAsString(),
                MatchResponse.class);

        mockMvc.perform(post("/api/groups/{groupId}/matches", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstMember))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(matchBody(date, "NONE", 10)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_ACCESS_DENIED"));

        confirmAttendance(match.id(), creator, "GOING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goingCount").value(1))
                .andExpect(jsonPath("$.myAttendance").value("GOING"));
        confirmAttendance(match.id(), firstMember, "GOING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goingCount").value(2));
        confirmAttendance(match.id(), secondMember, "GOING")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_FULL"));

        confirmAttendance(match.id(), creator, "NOT_GOING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goingCount").value(1))
                .andExpect(jsonPath("$.notGoingCount").value(1));
        confirmAttendance(match.id(), secondMember, "GOING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goingCount").value(2));

        mockMvc.perform(get("/api/matches/upcoming")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupName").value("Pelada avulsa"))
                .andExpect(jsonPath("$[0].attendances.length()").value(3));

        mockMvc.perform(delete("/api/matches/{matchId}", match.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstMember)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/matches/{matchId}", match.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/matches/{matchId}", match.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstMember)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.attendanceOpen").value(false));
    }

    @Test
    void shouldOpenNextWeeklyAttendanceAtNineAndAllowDelegatedAdminToManageSeries() throws Exception {
        AuthResponse creator = register("weekly-primary@example.com", "Principal Semanal");
        AuthResponse admin = register("weekly-admin@example.com", "Admin Semanal");
        GroupResponse group = createGroup(creator, "Pelada semanal");
        InviteResponse invite = createInvite(creator, group.id());
        join(admin, invite.code());
        GroupMember adminMembership = groupMemberRepository
                .findByGroupIdAndUserId(group.id(), admin.user().id())
                .orElseThrow();

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/promote", group.id(), adminMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk());

        LocalDate date = LocalDate.now(SAO_PAULO).plusDays(4);
        mockMvc.perform(post("/api/groups/{groupId}/matches", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(matchBody(date, "WEEKLY", 18)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/permissions", group.id(), adminMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions": ["SCHEDULE_GAMES"]}
                                """))
                .andExpect(status().isOk());

        var createResult = mockMvc.perform(post("/api/groups/{groupId}/matches", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(matchBody(date, "WEEKLY", 18)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrence").value("WEEKLY"))
                .andExpect(jsonPath("$.seriesActive").value(true))
                .andExpect(jsonPath("$.attendanceOpen").value(true))
                .andReturn();
        MatchResponse first = jsonMapper.readValue(
                createResult.getResponse().getContentAsString(),
                MatchResponse.class);

        MatchResponse[] initialMatches = listGroupMatches(admin, group.id());
        assertThat(initialMatches).hasSize(2);
        MatchResponse second = initialMatches[1];
        assertThat(second.attendanceOpen()).isFalse();
        assertThat(second.attendanceOpensAt().atZone(SAO_PAULO).toLocalTime().getHour()).isEqualTo(9);
        assertThat(second.attendanceOpensAt().atZone(SAO_PAULO).toLocalDate())
                .isEqualTo(first.startsAt().atZone(SAO_PAULO).toLocalDate().plusDays(1));

        jdbcTemplate.update(
                "UPDATE football_matches SET attendance_opens_at = NOW() - INTERVAL '1 minute' WHERE id = ?",
                second.id());

        mockMvc.perform(get("/api/matches/{matchId}", second.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceOpen").value(true));

        MatchResponse[] afterOpening = listGroupMatches(admin, group.id());
        assertThat(afterOpening).hasSize(3);
        assertThat(matchRepository.count()).isEqualTo(3);
        assertThat(notificationJobRepository.count()).isEqualTo(2);

        MatchResponse third = afterOpening[2];
        mockMvc.perform(delete("/api/matches/{matchId}", second.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
        assertThat(notificationJobRepository.findAll())
                .extracting(MatchNotificationJob::getNotificationType)
                .contains(MatchNotificationType.MATCH_CANCELLED);
        mockMvc.perform(get("/api/matches/{matchId}", third.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seriesActive").value(true));

        mockMvc.perform(delete("/api/match-series/{seriesId}", first.seriesId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/matches/{matchId}", third.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.seriesActive").value(false));
        assertThat(notificationJobRepository.findAll())
                .extracting(MatchNotificationJob::getNotificationType)
                .contains(MatchNotificationType.SERIES_CANCELLED);
    }

    @Test
    void shouldTrackPaymentsNotifyFullTeamAndQueueCancellation() throws Exception {
        AuthResponse creator = register("payment-primary@example.com", "Principal Pagamentos");
        AuthResponse member = register("payment-member@example.com", "Jogador Pagamentos");
        GroupResponse group = createGroup(creator, "Pelada com PIX");
        InviteResponse invite = createInvite(creator, group.id());
        join(member, invite.code());

        mockMvc.perform(put("/api/groups/{groupId}/details", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "defaultPaymentEnabled": true,
                                  "defaultPaymentAmount": 25.50,
                                  "defaultPixKey": "pix@onze.app",
                                  "schedules": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultPaymentAmount").value(25.50))
                .andExpect(jsonPath("$.defaultPixKey").value("pix@onze.app"));

        LocalDate date = LocalDate.now(SAO_PAULO).plusDays(3);
        var createResult = mockMvc.perform(post("/api/groups/{groupId}/matches", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paidMatchBody(date, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentRequired").value(true))
                .andExpect(jsonPath("$.paymentAmount").value(25.50))
                .andExpect(jsonPath("$.pixKey").value("pix@onze.app"))
                .andReturn();
        MatchResponse match = jsonMapper.readValue(
                createResult.getResponse().getContentAsString(),
                MatchResponse.class);

        confirmAttendance(match.id(), creator, "GOING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myPaymentStatus").value("PENDING"));
        confirmAttendance(match.id(), member, "GOING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myPaymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.goingCount").value(2));

        mockMvc.perform(put("/api/matches/{matchId}/payment/reported", match.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myPaymentStatus").value("REPORTED"));

        mockMvc.perform(put(
                        "/api/matches/{matchId}/payments/{playerUserId}/confirm",
                        match.id(),
                        member.user().id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(
                        "/api/matches/{matchId}/payments/{playerUserId}/confirm",
                        match.id(),
                        member.user().id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendances[1].paymentStatus").value("PAID"));

        mockMvc.perform(get("/api/matches/{matchId}", match.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myPaymentStatus").value("PAID"))
                .andExpect(jsonPath("$.attendances[0].paymentStatus").value(nullValue()));

        assertThat(notificationJobRepository.findAll())
                .extracting(MatchNotificationJob::getNotificationType)
                .contains(
                        MatchNotificationType.MATCH_CREATED,
                        MatchNotificationType.TEAM_FULL,
                        MatchNotificationType.PAYMENT_REPORTED,
                        MatchNotificationType.PAYMENT_CONFIRMED);

        mockMvc.perform(delete("/api/matches/{matchId}", match.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isNoContent());

        assertThat(notificationJobRepository.findAll())
                .extracting(MatchNotificationJob::getNotificationType)
                .contains(MatchNotificationType.MATCH_CANCELLED);
        assertThat(notificationJobRepository.findAll())
                .extracting(MatchNotificationJob::getDeduplicationKey)
                .doesNotHaveDuplicates();
    }

    private org.springframework.test.web.servlet.ResultActions confirmAttendance(
            java.util.UUID matchId,
            AuthResponse user,
            String attendance) throws Exception {
        return mockMvc.perform(put("/api/matches/{matchId}/attendance", matchId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "%s"}
                        """.formatted(attendance)));
    }

    private MatchResponse[] listGroupMatches(AuthResponse user, java.util.UUID groupId) throws Exception {
        var result = mockMvc.perform(get("/api/groups/{groupId}/matches", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readValue(result.getResponse().getContentAsString(), MatchResponse[].class);
    }

    private String matchBody(LocalDate date, String recurrence, int maxPlayers) {
        return """
                {
                  "date": "%s",
                  "startTime": "20:30:00",
                  "timeZone": "America/Sao_Paulo",
                  "venue": "Arena Onze",
                  "maxPlayers": %d,
                  "notes": "Levar colete verde",
                  "recurrence": "%s"
                }
                """.formatted(date, maxPlayers, recurrence);
    }

    private String paidMatchBody(LocalDate date, int maxPlayers) {
        return """
                {
                  "date": "%s",
                  "startTime": "20:30:00",
                  "timeZone": "America/Sao_Paulo",
                  "venue": "Arena Onze",
                  "maxPlayers": %d,
                  "paymentRequired": true,
                  "notes": "Pagamento via PIX",
                  "recurrence": "NONE"
                }
                """.formatted(date, maxPlayers);
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

    private InviteResponse createInvite(AuthResponse creator, java.util.UUID groupId) throws Exception {
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
