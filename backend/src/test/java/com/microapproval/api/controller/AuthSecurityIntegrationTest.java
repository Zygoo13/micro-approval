package com.microapproval.api.controller;

import com.microapproval.api.entity.User;
import com.microapproval.api.repository.UserRepository;
import com.microapproval.api.support.AbstractMySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthSecurityIntegrationTest extends AbstractMySqlIntegrationTest {

    private static final String LOCALHOST_ORIGIN = "http://localhost:3000";
    private static final String LOOPBACK_ORIGIN = "http://127.0.0.1:3000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registerIsPublicAndAllowsLoopbackFrontendOrigin() throws Exception {
        String email = uniqueEmail("register-public");

        mockMvc.perform(post("/api/v1/auth/register")
                        .header(HttpHeaders.ORIGIN, LOOPBACK_ORIGIN)
                        .contentType("application/json")
                        .content(registerPayload(email)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOOPBACK_ORIGIN))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginIsPublicWithoutJwt() throws Exception {
        String email = uniqueEmail("login-public");
        createUser(email, "CodexTest123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, LOCALHOST_ORIGIN)
                        .contentType("application/json")
                        .content(loginPayload(email, "CodexTest123!")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LOCALHOST_ORIGIN))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void duplicateRegisterReturnsConflict() throws Exception {
        String email = uniqueEmail("register-duplicate");
        createUser(email, "CodexTest123!");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(registerPayload(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void invalidLoginReturnsUnauthorized() throws Exception {
        String email = uniqueEmail("login-invalid");
        createUser(email, "CodexTest123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(loginPayload(email, "WrongPassword123!")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void workspaceAndMemberEndpointsRemainProtected() throws Exception {
        mockMvc.perform(get("/api/workspaces"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/workspaces/workspace-id/members"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidJwtIsRejectedOnProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/workspaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsPreflightAllowsBothLocalFrontendOrigins() throws Exception {
        assertPreflightAllowed(LOCALHOST_ORIGIN);
        assertPreflightAllowed(LOOPBACK_ORIGIN);
    }

    @Test
    void corsStillRejectsUntrustedOrigins() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .contentType("application/json")
                        .content(registerPayload(uniqueEmail("untrusted-origin"))))
                .andExpect(status().isForbidden());
    }

    private void assertPreflightAllowed(String origin) throws Exception {
        mockMvc.perform(options("/api/v1/auth/register")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("POST")));
    }

    private User createUser(String email, String password) {
        return userRepository.save(User.builder()
                .fullName("Security Test User")
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .build());
    }

    private String uniqueEmail(String label) {
        return label + "-" + UUID.randomUUID() + "@example.com";
    }

    private String registerPayload(String email) {
        return """
                {
                  "fullName": "Security Test User",
                  "email": "%s",
                  "password": "CodexTest123!"
                }
                """.formatted(email);
    }

    private String loginPayload(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
}
