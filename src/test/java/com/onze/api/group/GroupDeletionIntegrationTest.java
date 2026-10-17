package com.onze.api.group;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.onze.api.IntegrationTestSupport;
import com.onze.api.auth.AuthModels.AuthResponse;
import com.onze.api.group.GroupModels.GroupResponse;
import com.onze.api.match.MatchNotificationStatus;
import com.onze.api.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.jwt.secret=onze-group-deletion-integration-secret-with-at-least-32-bytes",
        "security.jwt.issuer=onze-api-group-deletion-test",
        "notifications.expo.enabled=false",
        "matches.processing.initial-delay-ms=3600000"
})
@AutoConfigureMockMvc
class GroupDeletionIntegrationTest extends IntegrationTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private GroupRepository groups;
    @Autowired private GroupMemberRepository members;
    @Autowired private GroupDeletionNoticeRepository notices;
    @Autowired private GroupDeletionNotificationProcessor processor;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        groups.deleteAll();
        users.deleteAll();
    }

    @Test
    void principalDeletesOnlyTheSelectedGroupAndEveryMemberKeepsANotice() throws Exception {
        AuthResponse owner = register("owner");
        AuthResponse member = register("member");
        AuthResponse admin = register("admin");
        GroupResponse group = createGroup(owner, "Pelada de sexta");
        GroupResponse other = createGroup(owner, "Outro grupo");
        members.save(new GroupMember(group.id(), member.user().id(), GroupRole.MEMBER));
        members.save(new GroupMember(group.id(), admin.user().id(), GroupRole.ADMIN));
        jdbc.update("""
                INSERT INTO football_matches
                  (id, group_id, starts_at, time_zone, venue, max_players, minimum_players, status, attendance_opens_at,
                   signup_deadline, created_by)
                VALUES (?, ?, NOW() + INTERVAL '7 days', 'America/Sao_Paulo', 'Arena', 14, 14, 'SCHEDULED',
                        NOW(), NOW() + INTERVAL '6 days', ?)
                """, UUID.randomUUID(), group.id(), owner.user().id());
        String invite = jsonMapper.readTree(mvc.perform(post("/api/groups/{id}/invite", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("code").asText();

        mvc.perform(delete("/api/groups/{id}", group.id()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        assertThat(groups.existsById(group.id())).isFalse();
        assertThat(groups.existsById(other.id())).isTrue();
        assertThat(members.findAllByGroupIdOrderByCreatedAtAsc(group.id())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM football_matches WHERE group_id = ?",
                Long.class, group.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_ranking_seasons WHERE group_id = ?",
                Long.class, group.id())).isZero();
        assertThat(notices.findAll()).hasSize(3).allSatisfy(notice -> {
            assertThat(notice.getGroupId()).isEqualTo(group.id());
            assertThat(notice.getGroupName()).isEqualTo("Pelada de sexta");
        });
        for (AuthResponse recipient : List.of(owner, member, admin)) {
            mvc.perform(get("/api/groups/deletion-notices")
                            .header(HttpHeaders.AUTHORIZATION, bearer(recipient)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].groupId").value(group.id().toString()))
                    .andExpect(jsonPath("$[0].groupName").value("Pelada de sexta"));
        }
        mvc.perform(post("/api/groups/join").header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + invite + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/groups").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(other.id().toString()));
        assertThat(processor.processPending()).isEqualTo(3);
        assertThat(notices.findAll()).allSatisfy(notice ->
                assertThat(notice.getPushStatus()).isEqualTo(MatchNotificationStatus.SENT));
    }

    @Test
    void ordinaryAdminsEvenWithEveryPermissionMembersAndOutsidersCannotDelete() throws Exception {
        AuthResponse owner = register("owner");
        AuthResponse admin = register("admin");
        AuthResponse member = register("member");
        AuthResponse outsider = register("outsider");
        GroupResponse group = createGroup(owner, "Grupo protegido");
        GroupMember administrator = new GroupMember(group.id(), admin.user().id(), GroupRole.ADMIN);
        administrator.replacePermissions(Set.of(GroupAdminPermission.values()));
        members.save(administrator);
        members.save(new GroupMember(group.id(), member.user().id(), GroupRole.MEMBER));

        for (AuthResponse user : List.of(admin, member, outsider)) {
            mvc.perform(delete("/api/groups/{id}", group.id()).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                    .andExpect(status().isForbidden());
        }
        assertThat(groups.existsById(group.id())).isTrue();
        assertThat(notices.count()).isZero();
    }

    @Test
    void deletionAndNoticesRequireAuthentication() throws Exception {
        mvc.perform(delete("/api/groups/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/groups/deletion-notices")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/groups/deletion-notices/{id}/acknowledge", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noticesArePrivateAndAcknowledgementIsIdempotent() throws Exception {
        AuthResponse owner = register("owner");
        AuthResponse member = register("member");
        AuthResponse outsider = register("outsider");
        GroupResponse group = createGroup(owner, "Grupo encerrado");
        members.save(new GroupMember(group.id(), member.user().id(), GroupRole.MEMBER));
        mvc.perform(delete("/api/groups/{id}", group.id()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
        GroupDeletionNotice memberNotice = notices
                .findTop50ByRecipientUserIdAndAcknowledgedAtIsNullOrderByCreatedAtAsc(member.user().id()).getFirst();

        mvc.perform(get("/api/groups/deletion-notices").header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(post("/api/groups/deletion-notices/{id}/acknowledge", memberNotice.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/groups/deletion-notices/{id}/acknowledge", memberNotice.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/groups/deletion-notices").header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assertThat(notices.findById(memberNotice.getId()).orElseThrow().getAcknowledgedAt()).isNotNull();
        mvc.perform(get("/api/groups/deletion-notices").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void repeatedDeletionDoesNotDuplicateNotices() throws Exception {
        AuthResponse owner = register("owner");
        GroupResponse group = createGroup(owner, "Grupo encerrado");
        mvc.perform(delete("/api/groups/{id}", group.id()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/groups/{id}", group.id()).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
        assertThat(notices.count()).isEqualTo(1);
    }

    @Test
    void unsuccessfulDeletionDoesNotLeaveAnIncorrectNotice() throws Exception {
        AuthResponse owner = register("owner");
        GroupResponse group = createGroup(owner, "Grupo com transação protegida");
        jdbc.execute("CREATE TABLE deletion_test_guard (group_id UUID REFERENCES groups(id))");
        try {
            jdbc.update("INSERT INTO deletion_test_guard (group_id) VALUES (?)", group.id());
            mvc.perform(delete("/api/groups/{id}", group.id())
                            .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                    .andExpect(status().isInternalServerError());
            assertThat(groups.existsById(group.id())).isTrue();
            assertThat(notices.count()).isZero();
        } finally {
            jdbc.execute("DROP TABLE deletion_test_guard");
        }
    }

    private AuthResponse register(String name) throws Exception {
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"%s","email":"%s@example.com","password":"StrongPass123!"}
                                """.formatted(name, name)))
                .andExpect(status().isCreated()).andReturn();
        return jsonMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private GroupResponse createGroup(AuthResponse owner, String name) throws Exception {
        var result = mvc.perform(post("/api/groups").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return jsonMapper.readValue(result.getResponse().getContentAsString(), GroupResponse.class);
    }

    private String bearer(AuthResponse auth) { return "Bearer " + auth.accessToken(); }
}
