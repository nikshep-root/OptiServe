package com.minor_project.optiserve_backend.authentication.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.minor_project.optiserve_backend.authentication.application.AuthApplicationService;
import com.minor_project.optiserve_backend.authentication.persistence.AuthUserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class AuthSecurityIntegrationTests {

    private static final String TEST_SECRET = "VGhpc0lzQVRlc3RPbmx5U2VjcmV0S2V5Rm9ySFRUUFN";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String MALFORMED_TOKEN = "not-a-jwt";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthApplicationService authService;
    @Autowired private AuthUserRepository users;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String protectedEmail;
    private String protectedToken;

    @BeforeAll
    void createProtectedUser() {
        protectedEmail = "protected-" + UUID.randomUUID() + "@example.com";
        protectedToken = authService.register(protectedEmail, PASSWORD).value();
    }

    @Test
    void registersAndLogsInWithHashedPassword() throws Exception {
        String email = "register-" + UUID.randomUUID() + "@example.com";
        String requestBody = objectMapper.writeValueAsString(new AuthRequest(email, PASSWORD));

        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String passwordHash = users.findByEmail(email).orElseThrow().getPassword();
        org.assertj.core.api.Assertions.assertThat(passwordHash).isNotEqualTo(PASSWORD);
        String registeredToken = tokenFrom(registration);
        org.assertj.core.api.Assertions.assertThat(registeredToken).isNotBlank();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        mockMvc.perform(get("/api/resources")
                        .header("Authorization", "Bearer " + tokenFrom(login)))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsValidJwtOnProtectedApiEndpoint() throws Exception {
        mockMvc.perform(get("/api/resources")
                        .header("Authorization", "Bearer " + protectedToken))
                .andExpect(status().isOk());
    }

    @Test
    void returnsApiErrorJsonForMissingInvalidAndExpiredTokens() throws Exception {
        expectUnauthorized(mockMvc.perform(get("/api/resources")));
        expectUnauthorized(mockMvc.perform(get("/api/resources")
                .header("Authorization", "Bearer not-a-jwt")));
        expectUnauthorized(mockMvc.perform(get("/api/resources")
                .header("Authorization", "Bearer " + expiredToken(protectedEmail))));
    }

    @Test
    void rejectsMalformedBearerJwtWithApiErrorJson() throws Exception {
        expectUnauthorized(mockMvc.perform(get("/api/resources")
                .header("Authorization", "Bearer " + MALFORMED_TOKEN)));
    }

    @Test
    void disabledUserCannotAuthenticateWithPreviouslyIssuedToken() throws Exception {
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE email = ?", protectedEmail);

        expectUnauthorized(mockMvc.perform(get("/api/resources")
                .header("Authorization", "Bearer " + protectedToken)));

        jdbcTemplate.update("UPDATE users SET enabled = TRUE WHERE email = ?", protectedEmail);
    }

    @Test
    void authenticatedUserWithoutAdminRoleGetsForbiddenApiErrorJson() throws Exception {
        mockMvc.perform(get("/api/admin/secret")
                        .header("Authorization", "Bearer " + protectedToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource."))
                .andExpect(jsonPath("$.path").value("/api/admin/secret"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    private void expectUnauthorized(ResultActions request) throws Exception {
        request
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource."))
                .andExpect(jsonPath("$.path").value("/api/resources"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    private String expiredToken(String subject) {
        Instant issuedAt = Instant.now().minusSeconds(120);
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET)))
                .compact();
    }

    private String tokenFrom(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("accessToken").asText();
    }
}
