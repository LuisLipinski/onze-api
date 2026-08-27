package com.onze.api.group;

import com.onze.api.auth.AuthModels.AuthResponse;
import com.onze.api.auth.PasswordResetCodeRepository;
import com.onze.api.group.GroupInviteModels.InviteResponse;
import com.onze.api.group.GroupInviteModels.JoinGroupResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "security.jwt.secret=onze-join-integration-secret-with-at-least-32-bytes",
        "security.jwt.issuer=onze-api-join-integration-test",
        "app.public-base-url=https://test.onze.local"
})
@AutoConfigureMockMvc
class GroupJoinIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("onze_join_test")
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
    void shouldJoinGroupAsMemberUsingInviteCodeAndBeIdempotent() throws Exception {
        AuthResponse creator = register("creator@example.com", "Criador");
        AuthResponse invited = register("invited@example.com", "Convidado");
        GroupResponse group = createGroup(creator, "Pelada do convite");
        InviteResponse invite = createInvite(creator, group.id());

        var joinResult = mockMvc.perform(post("/api/groups/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invited))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s"}
                                """.formatted(invite.code().toLowerCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(group.id().toString()))
                .andExpect(jsonPath("$.groupName").value("Pelada do convite"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.alreadyMember").value(false))
                .andReturn();

        JoinGroupResponse joined = jsonMapper.readValue(
                joinResult.getResponse().getContentAsString(),
                JoinGroupResponse.class);
        assertThat(joined.role()).isEqualTo(GroupRole.MEMBER);
        assertThat(groupMemberRepository.findByGroupIdAndUserId(group.id(), invited.user().id()))
                .get()
                .extracting(GroupMember::getRole)
                .isEqualTo(GroupRole.MEMBER);

        mockMvc.perform(post("/api/groups/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invited))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s"}
                                """.formatted(invite.code())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyMember").value(true))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        assertThat(groupMemberRepository.findAll()).hasSize(2);

        mockMvc.perform(get("/api/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invited)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(group.id().toString()))
                .andExpect(jsonPath("$[0].role").value("MEMBER"));
    }

    @Test
    void shouldExposePublicHttpsLandingPageForInvite() throws Exception {
        AuthResponse creator = register("creator@example.com", "Criador");
        GroupResponse group = createGroup(creator, "Pelada WhatsApp");
        InviteResponse invite = createInvite(creator, group.id());

        assertThat(invite.deepLink()).isEqualTo("onze://join/" + invite.code());
        assertThat(invite.shareUrl()).isEqualTo("https://test.onze.local/join/" + invite.code());

        var landing = mockMvc.perform(get("/join/{code}", invite.code()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(landing.getResponse().getContentType()).startsWith("text/html");
        assertThat(landing.getResponse().getContentAsString())
                .contains("Abrir no Onze")
                .contains(invite.code())
                .contains("onze://join/" + invite.code());

        mockMvc.perform(get("/join/ABCDEFGH"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAllowMultipleUsersToJoinUsingTheSameReusableInvite() throws Exception {
        AuthResponse creator = register("creator@example.com", "Criador");
        AuthResponse first = register("first@example.com", "Primeiro");
        AuthResponse second = register("second@example.com", "Segundo");
        GroupResponse group = createGroup(creator, "Pelada WhatsApp");
        InviteResponse invite = createInvite(creator, group.id());

        join(first, invite.code())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyMember").value(false))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        join(second, invite.code())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyMember").value(false))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        assertThat(groupMemberRepository.findAll()).hasSize(3);
        assertThat(groupMemberRepository.findByGroupIdAndUserId(group.id(), first.user().id())).isPresent();
        assertThat(groupMemberRepository.findByGroupIdAndUserId(group.id(), second.user().id())).isPresent();
        assertThat(groupInviteRepository.findByGroupId(group.id()))
                .get()
                .extracting(GroupInvite::getCode)
                .isEqualTo(invite.code());
    }

    @Test
    void shouldRegenerateInviteAndInvalidatePreviousCode() throws Exception {
        AuthResponse creator = register("creator@example.com", "Criador");
        AuthResponse oldInviteUser = register("old@example.com", "Convite antigo");
        AuthResponse newInviteUser = register("new@example.com", "Convite novo");
        GroupResponse group = createGroup(creator, "Pelada regenerada");
        InviteResponse original = createInvite(creator, group.id());

        var regenerateResult = mockMvc.perform(post("/api/groups/{groupId}/invite/regenerate", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(group.id().toString()))
                .andReturn();
        InviteResponse regenerated = jsonMapper.readValue(
                regenerateResult.getResponse().getContentAsString(),
                InviteResponse.class);

        assertThat(regenerated.code()).isNotEqualTo(original.code());
        assertThat(regenerated.deepLink()).isEqualTo("onze://join/" + regenerated.code());
        assertThat(regenerated.shareUrl()).isEqualTo("https://test.onze.local/join/" + regenerated.code());

        mockMvc.perform(get("/join/{code}", original.code()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/join/{code}", regenerated.code()))
                .andExpect(status().isOk());

        join(oldInviteUser, original.code())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GROUP_INVITE"));

        join(newInviteUser, regenerated.code())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyMember").value(false))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void shouldKeepPrimaryAdminRoleWhenCreatorUsesOwnInvite() throws Exception {
        AuthResponse creator = register("creator@example.com", "Criador");
        GroupResponse group = createGroup(creator, "Pelada Admin");
        InviteResponse invite = createInvite(creator, group.id());

        mockMvc.perform(post("/api/groups/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "%s"}
                                """.formatted(invite.code())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyMember").value(true))
                .andExpect(jsonPath("$.role").value("PRIMARY_ADMIN"));
    }

    @Test
    void shouldRejectInvalidInviteCodeAndUnauthenticatedJoin() throws Exception {
        AuthResponse invited = register("invited@example.com", "Convidado");

        mockMvc.perform(post("/api/groups/join")
                        .header(HttpHeaders.AUTHORIZATION, bearer(invited))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "ABCDEFGH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GROUP_INVITE"));

        mockMvc.perform(post("/api/groups/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "ABCDEFGH"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions join(AuthResponse user, String code) throws Exception {
        return mockMvc.perform(post("/api/groups/join")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s"}
                        """.formatted(code)));
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

    private InviteResponse createInvite(AuthResponse creator, java.util.UUID groupId) throws Exception {
        var result = mockMvc.perform(post("/api/groups/{groupId}/invite", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readValue(result.getResponse().getContentAsString(), InviteResponse.class);
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
