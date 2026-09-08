# mcp-keycloak-guard

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
`mcp-admin` token can call a tool that does something privileged. Wrong role gets a 403, not a
silent success.

## Try it in three commands

```bash
docker compose -f docker/docker-compose.yml up -d   # starts Keycloak with the demo realm imported
mvn spring-boot:run                                  # starts the MCP server on :8081
```

Get a token for the `reader` user (role `mcp-reader` only) and call the read-only tool:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/mcp-guard/protocol/openid-connect/token \
  -d grant_type=password -d client_id=mcp-guard-client -d username=reader -d password=reader \
  | jq -r .access_token)

curl -s http://localhost:8081/mcp -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"getServerStatus","arguments":{}}}'
```

Now try the privileged tool with the same reader token — it's rejected:

```bash
curl -s http://localhost:8081/mcp -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"restartDownstreamService","arguments":{"serviceName":"billing"}}}'
```

Get a token for `admin` (roles `mcp-reader` + `mcp-admin`) instead and the same call succeeds.

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
- **MCP transport**: Spring AI's `spring-ai-starter-mcp-server-webmvc`, same starter used in
  [`social-publisher-mcp`](https://github.com/jihedbfr-art/engineering-library/tree/main/projects/top-projects/social-publisher-mcp),
  exposing the tools over streamable HTTP at `/mcp`.

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
