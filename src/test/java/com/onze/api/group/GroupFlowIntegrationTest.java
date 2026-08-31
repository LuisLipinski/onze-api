package com.onze.api.group;

import com.onze.api.auth.AuthModels.AuthResponse;
import com.onze.api.auth.PasswordResetCodeRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "security.jwt.secret=onze-group-integration-secret-with-at-least-32-bytes",
        "security.jwt.issuer=onze-api-group-integration-test"
})
@AutoConfigureMockMvc
class GroupFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("onze_group_test")
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
    void shouldCreateGroupWithCreatorAsPrimaryAdminCompleteSetupAndGenerateInvite() throws Exception {
        AuthResponse creator = register("creator@example.com", "Criador");

        var createResult = mockMvc.perform(post("/api/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Pelada de Quinta  ",
                                  "description": "  Futebol dos amigos  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pelada de Quinta"))
                .andExpect(jsonPath("$.description").value("Futebol dos amigos"))
                .andExpect(jsonPath("$.photoUrl").doesNotExist())
                .andExpect(jsonPath("$.role").value("PRIMARY_ADMIN"))
                .andReturn();

        GroupResponse created = jsonMapper.readValue(
                createResult.getResponse().getContentAsString(),
                GroupResponse.class);

        var membership = groupMemberRepository.findByGroupIdAndUserId(
                created.id(), creator.user().id()).orElseThrow();
        assertThat(membership.getRole()).isEqualTo(GroupRole.PRIMARY_ADMIN);
        assertThat(groupRepository.findById(created.id()).orElseThrow().getCreatedBy())
                .isEqualTo(creator.user().id());

        mockMvc.perform(put("/api/groups/{groupId}/details", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "city": "Curitiba",
                                  "mascot": "Leão",
                                  "venue": "Arena dos Amigos",
                                  "schedules": [
                                    {"dayOfWeek": "THURSDAY", "startTime": "20:00:00"},
                                    {"dayOfWeek": "MONDAY", "startTime": "19:30:00"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Curitiba"))
                .andExpect(jsonPath("$.mascot").value("Leão"))
                .andExpect(jsonPath("$.venue").value("Arena dos Amigos"))
                .andExpect(jsonPath("$.schedules[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.schedules[1].dayOfWeek").value("THURSDAY"));

        var inviteResult = mockMvc.perform(post("/api/groups/{groupId}/invite", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(created.id().toString()))
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.deepLink").isString())
                .andReturn();

        String firstInviteBody = inviteResult.getResponse().getContentAsString();
        assertThat(groupInviteRepository.count()).isEqualTo(1);

        var repeatedInvite = mockMvc.perform(post("/api/groups/{groupId}/invite", created.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(repeatedInvite.getResponse().getContentAsString()).isEqualTo(firstInviteBody);
        assertThat(groupInviteRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(created.id().toString()))
                .andExpect(jsonPath("$[0].role").value("PRIMARY_ADMIN"))
                .andExpect(jsonPath("$[0].city").value("Curitiba"));
    }

    @Test
    void shouldUpdateGroupWithoutFailingWhenScheduleIsKeptUnchanged() throws Exception {
        AuthResponse creator = register("schedule-update@example.com", "Criador");
        GroupResponse group = createGroup(creator, "Pelada de sábado");

        mockMvc.perform(put("/api/groups/{groupId}/details", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "venue": "Arena zeroum",
                                  "schedules": [
                                    {"dayOfWeek": "SATURDAY", "startTime": "18:00:00"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/groups/{groupId}/details", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mascot": "Fenix",
                                  "venue": "Arena zeroum",
                                  "defaultPaymentEnabled": true,
                                  "defaultPaymentAmount": 20.00,
                                  "defaultPixKey": "teste@teste.com",
                                  "schedules": [
                                    {"dayOfWeek": "SATURDAY", "startTime": "18:00:00"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mascot").value("Fenix"))
                .andExpect(jsonPath("$.venue").value("Arena zeroum"))
                .andExpect(jsonPath("$.defaultPaymentAmount").value(20.00))
                .andExpect(jsonPath("$.defaultPixKey").value("teste@teste.com"))
                .andExpect(jsonPath("$.schedules[0].dayOfWeek").value("SATURDAY"))
                .andExpect(jsonPath("$.schedules[0].startTime").value("18:00:00"));

        assertThat(groupScheduleRepository.findAllByGroupId(group.id())).hasSize(1);
    }

    @Test
    void shouldKeepDefaultPhotoWhenCloudinaryIsNotConfigured() throws Exception {
        AuthResponse creator = register("photo@example.com", "Foto");
        GroupResponse group = createGroup(creator, "Grupo com foto");
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "time.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[] {1, 2, 3, 4});

        mockMvc.perform(multipart("/api/groups/{groupId}/photo", group.id())
                        .file(photo)
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PHOTO_STORAGE_NOT_CONFIGURED"));

        assertThat(groupRepository.findById(group.id()).orElseThrow().getPhotoUrl()).isNull();
    }

    @Test
    void shouldRejectGroupCreationWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Pelada"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectBlankGroupName() throws Exception {
        AuthResponse creator = register("creator@example.com", "Criador");

        mockMvc.perform(post("/api/groups")
                        .header(HttpHeaders.AUTHORIZATION, bearer(creator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "   "}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(groupRepository.count()).isZero();
    }

    @Test
    void shouldPreventAnotherUserFromChangingGroupOrGeneratingInvite() throws Exception {
        AuthResponse creator = register("creator@example.com", "Criador");
        AuthResponse anotherUser = register("other@example.com", "Outro Jogador");
        GroupResponse group = createGroup(creator, "Pelada privada");

        mockMvc.perform(put("/api/groups/{groupId}/details", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(anotherUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"city": "São Paulo", "schedules": []}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_ACCESS_DENIED"));

        mockMvc.perform(post("/api/groups/{groupId}/invite", group.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(anotherUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_ACCESS_DENIED"));
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
