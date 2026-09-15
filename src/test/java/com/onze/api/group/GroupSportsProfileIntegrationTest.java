package com.onze.api.group;

import com.onze.api.auth.AuthModels.AuthResponse;
import com.onze.api.auth.PasswordResetCodeRepository;
import com.onze.api.group.GroupInviteModels.InviteResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "security.jwt.secret=onze-sports-profile-integration-secret-with-at-least-32-bytes",
        "security.jwt.issuer=onze-api-sports-profile-integration-test",
        "app.public-base-url=https://test.onze.local"
})
@AutoConfigureMockMvc
class GroupSportsProfileIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("onze_sports_profile_test")
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
    void shouldLetMemberCompleteOwnProfileWithoutChangingTechnicalLevel() throws Exception {
        AuthResponse player = register("player-profile@example.com", "Jogador");
        GroupResponse group = createGroup(player, "Pelada Perfil");

        mockMvc.perform(get("/api/groups/{groupId}/members/me/sports-profile", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Jogador"))
                .andExpect(jsonPath("$.primaryPosition").doesNotExist())
                .andExpect(jsonPath("$.secondaryPosition").doesNotExist())
                .andExpect(jsonPath("$.positions").isEmpty())
                .andExpect(jsonPath("$.canPlayGoalkeeper").value(false))
                .andExpect(jsonPath("$.dominantFoot").doesNotExist())
                .andExpect(jsonPath("$.technicalLevel").doesNotExist())
                .andExpect(jsonPath("$.complete").value(false));

        mockMvc.perform(put("/api/groups/{groupId}/members/me/sports-profile", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(player))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "primaryPosition": null,
                                  "canPlayGoalkeeper": false,
                                  "dominantFoot": "RIGHT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SPORTS_PROFILE"));

        mockMvc.perform(put("/api/groups/{groupId}/members/me/sports-profile", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(player))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "primaryPosition": "DEFENDER",
                                  "secondaryPosition": "RIGHT_WINGER",
                                  "canPlayGoalkeeper": true,
                                  "dominantFoot": "LEFT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryPosition").value("DEFENDER"))
                .andExpect(jsonPath("$.secondaryPosition").value("RIGHT_WINGER"))
                .andExpect(jsonPath("$.positions.length()").value(2))
                .andExpect(jsonPath("$.canPlayGoalkeeper").value(true))
                .andExpect(jsonPath("$.dominantFoot").value("LEFT"))
                .andExpect(jsonPath("$.technicalLevel").doesNotExist())
                .andExpect(jsonPath("$.complete").value(true));

        GroupMember persisted = membership(group.id(), player);
        assertThat(persisted.getPositions()).containsExactly(
                PlayerPosition.DEFENDER,
                PlayerPosition.RIGHT_WINGER);
        assertThat(persisted.canPlayGoalkeeper()).isTrue();
        assertThat(persisted.getDominantFoot()).isEqualTo(DominantFoot.LEFT);
        assertThat(persisted.getTechnicalLevel()).isNull();

        mockMvc.perform(put("/api/groups/{groupId}/members/me/sports-profile", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(player))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "primaryPosition": "GOALKEEPER",
                                  "secondaryPosition": "MIDFIELDER",
                                  "canPlayGoalkeeper": true,
                                  "dominantFoot": "BOTH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryPosition").value("GOALKEEPER"))
                .andExpect(jsonPath("$.canPlayGoalkeeper").value(false));

        mockMvc.perform(put("/api/groups/{groupId}/members/me/sports-profile", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(player))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "primaryPosition": "MIDFIELDER",
                                  "secondaryPosition": "MIDFIELDER",
                                  "canPlayGoalkeeper": false,
                                  "dominantFoot": "RIGHT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SPORTS_PROFILE"));

        mockMvc.perform(put("/api/groups/{groupId}/members/me/sports-profile", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(player))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "positions": ["STRIKER"],
                                  "canPlayGoalkeeper": false,
                                  "dominantFoot": "RIGHT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryPosition").value("ATTACKER"));

        mockMvc.perform(put("/api/groups/{groupId}/members/me/sports-profile", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(player))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "positions": ["DEFENDER", "MIDFIELDER", "ATTACKER"],
                                  "canPlayGoalkeeper": false,
                                  "dominantFoot": "RIGHT"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRestrictFullProfileEditingToAuthorizedAdmins() throws Exception {
        AuthResponse primary = register("profile-primary@example.com", "Principal");
        AuthResponse admin = register("profile-admin@example.com", "Admin");
        AuthResponse player = register("profile-member@example.com", "Atleta");
        GroupResponse group = createGroup(primary, "Pelada Administrada");
        InviteResponse invite = createInvite(primary, group.id());
        join(admin, invite.code());
        join(player, invite.code());

        GroupMember adminMembership = membership(group.id(), admin);
        GroupMember playerMembership = membership(group.id(), player);
        promote(primary, group.id(), adminMembership.getId());

        mockMvc.perform(get("/api/groups/{groupId}/members/{memberId}/sports-profile",
                        group.id(), playerMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(player)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/sports-profile",
                        group.id(), playerMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullProfile("MIDFIELDER", null, false, "BOTH", 3)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/sports-profile",
                        group.id(), playerMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullProfile("CENTER_FORWARD", null, true, "RIGHT", 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicalLevel").value(4))
                .andExpect(jsonPath("$.primaryPosition").value("CENTER_FORWARD"));

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/permissions",
                        group.id(), adminMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions": ["EDIT_PLAYER_PROFILES"]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/sports-profile",
                        group.id(), playerMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullProfile("MIDFIELDER", "GOALKEEPER", true, "BOTH", 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dominantFoot").value("BOTH"))
                .andExpect(jsonPath("$.secondaryPosition").value("GOALKEEPER"))
                .andExpect(jsonPath("$.canPlayGoalkeeper").value(false))
                .andExpect(jsonPath("$.technicalLevel").value(3));

        mockMvc.perform(get("/api/groups/{groupId}/members", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].positions[0]").value("MIDFIELDER"))
                .andExpect(jsonPath("$[2].technicalLevel").value(3))
                .andExpect(jsonPath("$[2].sportsProfileComplete").value(true));
    }

    @Test
    void shouldValidateLevelAndKeepProfilesIsolatedByGroup() throws Exception {
        AuthResponse primary = register("isolation-primary@example.com", "Principal");
        AuthResponse player = register("isolation-player@example.com", "Jogador");
        GroupResponse firstGroup = createGroup(primary, "Grupo Um");
        GroupResponse secondGroup = createGroup(primary, "Grupo Dois");
        InviteResponse invite = createInvite(primary, firstGroup.id());
        join(player, invite.code());
        GroupMember playerMembership = membership(firstGroup.id(), player);

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/sports-profile",
                        firstGroup.id(), playerMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullProfile("DEFENDER", null, false, "RIGHT", 6)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/groups/{groupId}/members/{memberId}/sports-profile",
                        secondGroup.id(), playerMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_MEMBER_NOT_FOUND"));
    }

    @Test
    void shouldKeepTechnicalRatingsPrivateAndPreserveNullSkills() throws Exception {
        AuthResponse primary = register("ratings-primary@example.com", "Principal Avaliação");
        AuthResponse admin = register("ratings-admin@example.com", "Admin Avaliação");
        AuthResponse player = register("ratings-player@example.com", "Jogador Avaliado");
        GroupResponse group = createGroup(primary, "Pelada avaliações");
        InviteResponse invite = createInvite(primary, group.id());
        join(admin, invite.code());
        join(player, invite.code());
        GroupMember adminMembership = membership(group.id(), admin);
        GroupMember playerMembership = membership(group.id(), player);
        promote(primary, group.id(), adminMembership.getId());

        mockMvc.perform(get("/api/groups/{groupId}/members/{memberId}/technical-profile",
                        group.id(), playerMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(player)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/technical-profile",
                        group.id(), playerMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ratings": {"PASSING": 10}}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/permissions",
                        group.id(), adminMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions": ["EDIT_PLAYER_PROFILES"]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/technical-profile",
                        group.id(), playerMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ratings": {
                                    "PASSING": 10,
                                    "FINISHING": 2,
                                    "GOALKEEPER_REFLEXES": 1,
                                    "CROSSING": null
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratings.PASSING").value(10))
                .andExpect(jsonPath("$.ratings.FINISHING").value(2))
                .andExpect(jsonPath("$.ratings.GOALKEEPER_REFLEXES").value(1))
                .andExpect(jsonPath("$.ratings.CROSSING").doesNotExist())
                .andExpect(jsonPath("$.generalOverall.overall").value(30))
                .andExpect(jsonPath("$.generalOverall.coverage").value(14))
                .andExpect(jsonPath("$.technicalProfileUpdatedAt").exists());

        mockMvc.perform(get("/api/groups/{groupId}/members/{memberId}/technical-profile",
                        group.id(), playerMembership.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratings.PASSING").value(10));
    }

    @Test
    void shouldRejectTechnicalRatingsOutsideHalfStarScale() throws Exception {
        AuthResponse primary = register("ratings-invalid@example.com", "Principal Inválido");
        GroupResponse group = createGroup(primary, "Pelada nota inválida");
        GroupMember membership = membership(group.id(), primary);

        for (int invalid : new int[]{0, 11}) {
            mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/technical-profile",
                            group.id(), membership.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(primary))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"ratings": {"PASSING": %d}}
                                    """.formatted(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_TECHNICAL_RATING"));
        }
    }

    private String fullProfile(
            String primaryPosition,
            String secondaryPosition,
            boolean goalkeeper,
            String foot,
            int level) {
        String secondaryProperty = secondaryPosition == null
                ? ""
                : "\"secondaryPosition\": \"" + secondaryPosition + "\",";
        return """
                {
                  "primaryPosition": "%s",
                  %s
                  "canPlayGoalkeeper": %s,
                  "dominantFoot": "%s",
                  "technicalLevel": %d
                }
                """.formatted(primaryPosition, secondaryProperty, goalkeeper, foot, level);
    }

    private GroupMember membership(java.util.UUID groupId, AuthResponse user) {
        return groupMemberRepository.findByGroupIdAndUserId(groupId, user.user().id()).orElseThrow();
    }

    private void promote(AuthResponse primary, java.util.UUID groupId, java.util.UUID memberId) throws Exception {
        mockMvc.perform(put("/api/groups/{groupId}/members/{memberId}/promote", groupId, memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary)))
                .andExpect(status().isOk());
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

    private InviteResponse createInvite(AuthResponse primary, java.util.UUID groupId) throws Exception {
        var result = mockMvc.perform(post("/api/groups/{groupId}/invite", groupId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(primary)))
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
