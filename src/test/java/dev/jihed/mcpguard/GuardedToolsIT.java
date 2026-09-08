package dev.jihed.mcpguard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import dev.jihed.mcpguard.tools.GuardedTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end proof that per-tool authorization actually works: a real Keycloak-issued JWT, run
 * through {@link JwtAuthenticationConverter}, gates real {@code @PreAuthorize} checks on {@link
 * GuardedTools}. No mocks — a reader token can call the read-only tool but is rejected on the
 * admin tool; an admin token can call both.
 */
@Testcontainers
@SpringBootTest
class GuardedToolsIT {

  private static final String KEYCLOAK_IMAGE =
      System.getProperty("keycloak.container.image", "quay.io/keycloak/keycloak:26.7.1");

  @Container
  private static final KeycloakContainer KEYCLOAK =
      new KeycloakContainer(KEYCLOAK_IMAGE).withRealmImportFile("mcp-guard-test-realm.json");

  private static final RestTemplate REST_TEMPLATE = new RestTemplate();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @DynamicPropertySource
  static void configureKeycloakProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        () -> KEYCLOAK.getAuthServerUrl() + "/realms/mcp-guard-test");
  }

  @Autowired private GuardedTools tools;
  @Autowired private JwtDecoder jwtDecoder;
  @Autowired private JwtAuthenticationConverter jwtAuthenticationConverter;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private void authenticateAs(String username, String password) throws Exception {
    String token = obtainAccessToken(username, password);
    Jwt jwt = jwtDecoder.decode(token);
    Authentication authentication = jwtAuthenticationConverter.convert(jwt);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private static String obtainAccessToken(String username, String password) throws Exception {
    String tokenUrl =
        KEYCLOAK.getAuthServerUrl() + "/realms/mcp-guard-test/protocol/openid-connect/token";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "password");
    body.add("client_id", "mcp-guard-client");
    body.add("username", username);
    body.add("password", password);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
    ResponseEntity<String> response = REST_TEMPLATE.postForEntity(tokenUrl, request, String.class);

    JsonNode json = OBJECT_MAPPER.readTree(response.getBody());
    return json.get("access_token").asText();
  }

  @Test
  void readerCanCallReadOnlyToolButNotAdminTool() throws Exception {
    authenticateAs("reader", "reader");

    assertThat(tools.getServerStatus()).contains("up at");
    assertThatThrownBy(() -> tools.restartDownstreamService("billing"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void adminCanCallBothTools() throws Exception {
    authenticateAs("admin", "admin");

    assertThat(tools.getServerStatus()).contains("up at");
    assertThat(tools.restartDownstreamService("billing")).contains("Restart signal sent");
  }

  @Test
  void unauthenticatedCallerIsRejected() {
    // No Authentication in the SecurityContext at all (never logged in) surfaces as
    // AuthenticationCredentialsNotFoundException, a sibling of AccessDeniedException under
    // Spring Security's AuthenticationException hierarchy -- both mean "rejected", so accept
    // either rather than pinning to the exact exception Spring Security happens to throw here.
    assertThatThrownBy(() -> tools.getServerStatus())
        .isInstanceOfAny(
            AccessDeniedException.class,
            org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
                .class);
  }
}
