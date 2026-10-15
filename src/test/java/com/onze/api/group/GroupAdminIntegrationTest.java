package com.onze.api.group;

import com.onze.api.IntegrationTestSupport;
import com.onze.api.auth.AuthModels.AuthResponse;
import com.onze.api.auth.PasswordResetCodeRepository;
import com.onze.api.group.GroupInviteModels.InviteResponse;
import com.onze.api.group.GroupModels.GroupMemberResponse;
import com.onze.api.group.GroupModels.GroupResponse;
import com.onze.api.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.jwt.secret=onze-admin-integration-secret-with-at-least-32-bytes",
        "security.jwt.issuer=onze-api-admin-integration-test",
        "app.public-base-url=https://test.onze.local"
})
@AutoConfigureMockMvc
class GroupAdminIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

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
    void shouldEnforceDelegatedPermissionsAndKeepFormerPrimaryAsAdmin() throws Exception {
        AuthResponse creator = register("primary@example.com", "Principal");
        AuthResponse first = register("first-admin@example.com", "Admin Um");
        AuthResponse second = register("second-admin@example.com", "Admin Dois");
        AuthResponse third = register("third-member@example.com", "Membro Três");

        GroupResponse group = createGroup(creator, "Pelada dos Admins");
        assertThat(group.role()).isEqualTo(GroupRole.PRIMARY_ADMIN);

        InviteResponse invite = createInvite(creator, group.id());
        join(first, invite.code());
        join(second, invite.code());
        join(third, invite.code());

        GroupMember firstMembership = membership(group.id(), first);
        GroupMember secondMembership = membership(group.id(), second);
        GroupMember thirdMembership = membership(group.id(), third);

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/promote", group.id(), firstMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.permissions").isEmpty());

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/promote", group.id(), secondMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_ACCESS_DENIED"));

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/permissions", group.id(), firstMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions": ["PROMOTE_MEMBERS"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("PROMOTE_MEMBERS"));

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/promote", group.id(), secondMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.permissions").isEmpty());

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/demote", group.id(), secondMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIMARY_ADMIN_REQUIRED"));

        GroupMember creatorMembership = membership(group.id(), creator);
        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/demote", group.id(), creatorMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRIMARY_ADMIN_TRANSFER_REQUIRED"));

        mockMvc.perform(put("/api/groups/{groupId}/primary-admin", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"replacementMemberId": "%s"}
                                """.formatted(firstMembership.getId())))
                .andExpect(status().isOk());

        assertThat(membership(group.id(), creator).getRole()).isEqualTo(GroupRole.ADMIN);
        assertThat(membership(group.id(), creator).getPermissions()).isEmpty();
        assertThat(membership(group.id(), first).getRole()).isEqualTo(GroupRole.PRIMARY_ADMIN);
        assertThat(membership(group.id(), second).getRole()).isEqualTo(GroupRole.ADMIN);

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/promote", group.id(), thirdMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_ACCESS_DENIED"));

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/demote", group.id(), secondMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void shouldListMembersForAdminsAndHideManagementFromMembers() throws Exception {
        AuthResponse creator = register("creator@example.com", "Criador");
        AuthResponse member = register("member@example.com", "Jogador");
        GroupResponse group = createGroup(creator, "Pelada Lista");
        InviteResponse invite = createInvite(creator, group.id());
        join(member, invite.code());

        var result = mockMvc.perform(get("/api/groups/{groupId}/members", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("PRIMARY_ADMIN"))
                .andExpect(jsonPath("$[0].permissions.length()").value(6))
                .andExpect(jsonPath("$[0].currentUser").value(true))
                .andExpect(jsonPath("$[1].role").value("MEMBER"))
                .andExpect(jsonPath("$[1].permissions").isEmpty())
                .andReturn();

        GroupMemberResponse[] members = jsonMapper.readValue(
                result.getResponse().getContentAsString(),
                GroupMemberResponse[].class);
        assertThat(members).hasSize(2);

        mockMvc.perform(get("/api/groups/{groupId}/members", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_ACCESS_DENIED"));
    }

    @Test
    void shouldAuthorizeOnlyFunctionsSelectedByPrimaryAdmin() throws Exception {
        AuthResponse creator = register("permissions-primary@example.com", "Principal");
        AuthResponse admin = register("permissions-admin@example.com", "Admin");
        AuthResponse removable = register("permissions-remove@example.com", "Removível");
        AuthResponse promotable = register("permissions-promote@example.com", "Promovível");

        GroupResponse group = createGroup(creator, "Pelada das Permissões");
        InviteResponse invite = createInvite(creator, group.id());
        join(admin, invite.code());
        join(removable, invite.code());
        join(promotable, invite.code());

        GroupMember adminMembership = membership(group.id(), admin);
        GroupMember removableMembership = membership(group.id(), removable);
        GroupMember promotableMembership = membership(group.id(), promotable);

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/promote", group.id(), adminMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/groups/{groupId}/invite", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/groups/{groupId}/details", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"city": "Curitiba", "schedules": []}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/groups/{groupId}/members/{memberId}", group.id(), removableMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/permissions", group.id(), adminMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissions": [
                                    "ADD_MEMBERS",
                                    "REMOVE_MEMBERS",
                                    "EDIT_GROUP",
                                    "SCHEDULE_GAMES"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(4));

        mockMvc.perform(post("/api/groups/{groupId}/invite", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/groups/{groupId}/details", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"city": "Curitiba", "schedules": []}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Curitiba"));

        mockMvc.perform(delete("/api/groups/{groupId}/members/{memberId}", group.id(), removableMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
        assertThat(groupMemberRepository.findById(removableMembership.getId())).isEmpty();

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/promote", group.id(), promotableMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/permissions", group.id(), adminMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions": ["PROMOTE_MEMBERS"]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIMARY_ADMIN_REQUIRED"));

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/permissions", group.id(), adminMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permissions": [
                                    "ADD_MEMBERS",
                                    "REMOVE_MEMBERS",
                                    "PROMOTE_MEMBERS",
                                    "EDIT_GROUP",
                                    "SCHEDULE_GAMES"
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/promote", group.id(), promotableMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(delete("/api/groups/{groupId}/members/{memberId}", group.id(), promotableMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_ROLE_REQUIRED"));
    }

    @Test
    void shouldConfigureEveryOperationalPermissionWithoutDelegatingOwnership() throws Exception {
        AuthResponse primary = register("all-primary@example.com", "Principal");
        AuthResponse admin = register("all-admin@example.com", "Admin");
        GroupResponse group = createGroup(primary, "Todas as permissões");
        join(admin, createInvite(primary, group.id()).code());
        GroupMember target = membership(group.id(), admin);
        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/promote", group.id(), target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary))).andExpect(status().isOk());
        String body = jsonMapper.writeValueAsString(java.util.Map.of(
                "permissions", Set.of(GroupAdminPermission.values())));
        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/permissions", group.id(), target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(GroupAdminPermission.values().length));
        assertThat(membership(group.id(), admin).getPermissions()).containsExactlyInAnyOrder(GroupAdminPermission.values());
        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/permissions", group.id(), target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/demote", group.id(), membership(group.id(), primary).getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isForbidden());
    }

    @Test
    void shouldMigrateExistingDelegationsWithoutGrantingAccessToOrdinaryMembers() throws Exception {
        AuthResponse primary = register("migration-primary@example.com", "Principal");
        AuthResponse scheduler = register("migration-scheduler@example.com", "Organizador");
        AuthResponse evaluator = register("migration-evaluator@example.com", "Avaliador");
        AuthResponse member = register("migration-member@example.com", "Membro");
        GroupResponse group = createGroup(primary, "Migração de permissões");
        String code = createInvite(primary, group.id()).code();
        join(scheduler, code);
        join(evaluator, code);
        join(member, code);
        GroupMember scheduled = membership(group.id(), scheduler);
        scheduled.changeRole(GroupRole.ADMIN);
        scheduled.replacePermissions(Set.of(GroupAdminPermission.SCHEDULE_GAMES));
        groupMemberRepository.saveAndFlush(scheduled);
        GroupMember evaluated = membership(group.id(), evaluator);
        evaluated.changeRole(GroupRole.ADMIN);
        evaluated.replacePermissions(Set.of(GroupAdminPermission.EDIT_PLAYER_PROFILES));
        groupMemberRepository.saveAndFlush(evaluated);
        jdbcTemplate.update("INSERT INTO group_admin_permissions (group_member_id, permission) VALUES (?, 'SCHEDULE_GAMES')",
                membership(group.id(), member).getId());
        String sql = new ClassPathResource("db/migration/V35__split_operational_admin_permissions.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);
        jdbcTemplate.execute(sql);
        assertThat(membership(group.id(), scheduler).getPermissions()).containsExactlyInAnyOrder(
                GroupAdminPermission.SCHEDULE_GAMES, GroupAdminPermission.CANCEL_GAMES,
                GroupAdminPermission.MANAGE_MATCH_PLAYERS, GroupAdminPermission.MANAGE_PAYMENTS,
                GroupAdminPermission.MANAGE_LIVE_MATCH);
        assertThat(membership(group.id(), evaluator).getPermissions()).containsExactlyInAnyOrder(
                GroupAdminPermission.EDIT_PLAYER_PROFILES, GroupAdminPermission.MANAGE_TEAMS);
        assertThat(membership(group.id(), member).getPermissions()).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_admin_permissions WHERE group_member_id = ?", Long.class,
                membership(group.id(), member).getId())).isEqualTo(1L);
    }

    private GroupMember membership(java.util.UUID groupId, AuthResponse user) {
        return groupMemberRepository.findByGroupIdAndUserId(groupId, user.user().id()).orElseThrow();
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
