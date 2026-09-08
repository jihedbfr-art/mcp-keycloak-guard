package dev.jihed.mcpguard.tools;

import java.time.Instant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * Two demo tools, two different Keycloak realm roles. This is the point of the whole project:
 * MCP transport-level auth only answers "is this caller authenticated at all?" — it says nothing
 * about which of the server's tools that caller should be allowed to invoke. {@code @PreAuthorize}
 * on each tool method, backed by roles from the caller's Keycloak JWT (mapped by
 * spring-keycloak-toolkit's {@code KeycloakRealmRoleConverter}), closes that gap. Note the
 * converter uppercases role names before prefixing ({@code mcp-reader} realm role becomes the
 * {@code ROLE_MCP-READER} authority), so the {@code hasRole(...)} checks below use the uppercase
 * form even though the realm role itself is defined lowercase.
 */
@Component
public class GuardedTools {

  @Tool(description = "Read-only server status check. Requires the mcp-reader realm role.")
  @PreAuthorize("hasRole('MCP-READER')")
  public String getServerStatus() {
    return "mcp-keycloak-guard is up at " + Instant.now();
  }

  @Tool(
      description =
          "Simulates a privileged administrative action (e.g. restarting a downstream service). "
              + "Requires the mcp-admin realm role — a reader-only caller gets a 403, not just a "
              + "'you're not logged in' error.")
  @PreAuthorize("hasRole('MCP-ADMIN')")
  public String restartDownstreamService(String serviceName) {
    return "Restart signal sent to '" + serviceName + "' at " + Instant.now();
  }
}
