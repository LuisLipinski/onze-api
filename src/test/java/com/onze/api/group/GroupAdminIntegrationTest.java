package com.onze.api.group;

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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "security.jwt.secret=onze-admin-integration-secret-with-at-least-32-bytes",
        "security.jwt.issuer=onze-api-admin-integration-test",
        "app.public-base-url=https://test.onze.local"
})
@AutoConfigureMockMvc
class GroupAdminIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("onze_admin_test")
            .withUsername("onze")
            .withPassword("onze");

    @Autowired
    private MockMvc mockMvc;

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
                .andExpect(jsonPath("$[0].permissions.length()").value(5))
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
