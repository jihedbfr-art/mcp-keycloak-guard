package dev.jihed.mcpguard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import dev.jihed.mcpguard.tools.GuardedTools;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
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
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
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
  @LocalServerPort private int port;

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

  private CallToolResult callTool(String token, String toolName) {
    HttpClientSseClientTransport transport =
        HttpClientSseClientTransport.builder("http://localhost:" + port)
            .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
            .build();
    try (McpSyncClient client =
        McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build()) {
      client.initialize();
      return client.callTool(new CallToolRequest(toolName, Map.of("serviceName", "billing")));
    }
  }

  @Test
  void sseMcpRequiresAuthenticationAndEnforcesToolRoles() throws Exception {
    HttpResponse<String> anonymous =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/sse"))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(anonymous.statusCode()).isEqualTo(401);

    HttpResponse<String> invalid =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/sse"))
                    .header("Authorization", "Bearer invalid-token")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(invalid.statusCode()).isEqualTo(401);

    String readerToken = obtainAccessToken("reader", "reader");
    CallToolResult readerStatus = callTool(readerToken, "getServerStatus");
    assertThat(readerStatus.isError()).isFalse();
    assertThat(readerStatus.toString()).contains("up at");

    CallToolResult reader = callTool(readerToken, "restartDownstreamService");
    assertThat(reader.isError()).isTrue();
    assertThat(reader.toString()).contains("Access Denied").doesNotContain("Restart signal sent");

    CallToolResult admin =
        callTool(obtainAccessToken("admin", "admin"), "restartDownstreamService");
    assertThat(admin.isError()).isFalse();
    assertThat(admin.toString()).contains("Restart signal sent");

    assertThat(callTool(readerToken, "restartDownstreamService").isError()).isTrue();

    String observerToken = obtainAccessToken("observer", "observer");
    assertThat(callTool(observerToken, "getServerStatus").isError()).isTrue();
    assertThat(callTool(observerToken, "restartDownstreamService").isError()).isTrue();
  }

  @Test
  void concurrentMcpClientsKeepTheirOwnRoles() throws Exception {
    String readerToken = obtainAccessToken("reader", "reader");
    String adminToken = obtainAccessToken("admin", "admin");
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<CallToolResult> reader =
          executor.submit(
              () -> {
                start.await();
                return callTool(readerToken, "restartDownstreamService");
              });
      Future<CallToolResult> admin =
          executor.submit(
              () -> {
                start.await();
                return callTool(adminToken, "restartDownstreamService");
              });
      start.countDown();

      assertThat(reader.get(15, TimeUnit.SECONDS).isError()).isTrue();
      assertThat(admin.get(15, TimeUnit.SECONDS).isError()).isFalse();
    } finally {
      executor.shutdownNow();
    }
  }
}
