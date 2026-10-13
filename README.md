# mcp-keycloak-guard

[Lire en français](README.fr.md)

Reference MCP server where every tool call is authorized against **Keycloak realm roles** —
not just "does this caller have a valid token at all", but "is this specific caller allowed to
call this specific tool".

## The gap this fills

MCP's transport-level auth (OAuth2 bearer token on the HTTP transport) answers one question:
*is the caller authenticated?* It says nothing about *which tools* an authenticated caller
should be allowed to invoke. In practice almost every MCP server either skips auth entirely
(local stdio transport) or treats "has a valid token" as "can call anything" — fine for a
single-user dev setup, not fine once an MCP server exposes anything that touches production
data or triggers a real action.

This project wires Spring AI's MCP server support to Spring Security's OAuth2 resource server
and puts `@PreAuthorize` on individual tool methods, backed by realm roles pulled straight out
of the caller's Keycloak-issued JWT. A `mcp-reader` token can call a read-only tool; only a
`mcp-admin` token can call a privileged tool. An SSE/MCP integration test now covers
the full request path: unauthenticated connection gets HTTP 401, a reader can call the
status tool but receives an MCP tool error for the admin tool, and an admin can call both.
The test also rejects an invalid token and denies both tools to a test user with no MCP role.

## Run the example

```bash
docker compose -f docker/docker-compose.yml up -d
mvn spring-boot:run
```

This version of Spring AI exposes **SSE**, with the connection at `/sse` and
JSON-RPC messages at `/mcp/message`. An MCP client must send the bearer token
on both requests. A direct POST to `/mcp` returns 404 in this version.

Run the automated reader/admin demonstration with Docker available:

```bash
mvn verify
```

The test obtains tokens from the bundled demo realm, opens authenticated SSE
connections, and calls both tools through an MCP client. It checks the result
after an admin call with the reader token again to catch context leakage.
It also starts a reader and an administrator client together; this is an isolation check,
not a load test.

## How it's wired

- **Role mapping**: [`spring-keycloak-toolkit`](https://github.com/jihedbfr-art/spring-keycloak-toolkit)'s
  `KeycloakRealmRoleConverter` turns `realm_access.roles` from the JWT into Spring Security
  authorities — reused as a dependency instead of hand-rolling the same converter a third time.
- **Authentication**: [`SecurityConfig`](src/main/java/dev/jihed/mcpguard/config/SecurityConfig.java)
  wires that role mapping into Spring Security's OAuth2 resource server. Health checks stay open;
  everything else needs a valid token.
- **Authorization**: [`GuardedTools`](src/main/java/dev/jihed/mcpguard/tools/GuardedTools.java) —
  each `@Tool` method carries its own `@PreAuthorize("hasRole(...)")`. This is the actual point of
  the project: authorization lives next to the tool it protects, not in one global filter.
- **Context propagation**: [`SecurityContextPropagationConfig`](src/main/java/dev/jihed/mcpguard/config/SecurityContextPropagationConfig.java)
  carries the authenticated request context to the Reactor worker that invokes the tool.
- **MCP transport**: Spring AI's `spring-ai-starter-mcp-server-webmvc`, same starter used in
  [`social-publisher-mcp`](https://github.com/jihedbfr-art/engineering-library/tree/main/projects/top-projects/social-publisher-mcp),
  exposing the tools through SSE at `/sse` and `/mcp/message`.

## What it deliberately does not do

It does not implement OAuth2 Token Exchange (RFC 8693) delegation for agent-acts-on-behalf-of-user
scenarios — that's a real, harder problem (see Roadmap) and conflating it with basic per-tool RBAC
would make this project's core point less clear. It also does not ship a production-grade Keycloak
realm — the bundled `realm-export.json` is a minimal two-role, two-user demo realm, meant to be
replaced by your own.

## Roadmap

- [ ] RFC 8693 token exchange for "agent acts on behalf of user" delegation, not just direct
      role checks
- [ ] Testcontainers-based example showing the same guard pattern against a second, independent
      MCP tool set
- [ ] Optional per-tool scope (not just realm role) authorization for finer-grained Keycloak client
      setups

## License

MIT — see [LICENSE](LICENSE).
