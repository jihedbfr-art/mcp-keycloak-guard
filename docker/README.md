# Run the local Keycloak demo and check readiness

[Version française](README.fr.md)

From the repository root, wait for Keycloak readiness before starting the application:

```bash
docker compose -f docker/docker-compose.yml up -d --wait --wait-timeout 180
docker compose -f docker/docker-compose.yml ps
mvn spring-boot:run
```

The demo publishes Keycloak on port 8080. The application's default issuer is `http://localhost:8080/realms/mcp-guard`; its HTTP port is 8081. These commands use the bundled development realm and demo credentials. Replace that setup for a production deployment.

## What the healthcheck verifies

`KC_HEALTH_ENABLED=true` enables the health endpoints. The probe sends an HTTP HEAD request to `/health/ready` on the container's management port 9000 and requires HTTP 200. It uses Bash's TCP redirection because the Keycloak image does not include curl. Port 9000 stays inside the container; the demo does not publish it on the host.

This verifies Keycloak readiness. It does not prove that an MCP user has the required tool role. Run the authorization tests separately:

```bash
mvn verify
```

## Diagnose an unhealthy container

```bash
docker compose -f docker/docker-compose.yml ps
docker compose -f docker/docker-compose.yml logs --tail 60 keycloak
```

Check that realm import completes and that the process starts successfully. An open port 8080 alone is not evidence that the readiness endpoint is enabled. The previous probe queried `/health/ready` on 8080 without enabling health checks; the realm could be available while that URL returned 404.

Use the same Compose file when stopping this demo:

```bash
docker compose -f docker/docker-compose.yml down
```

Source: [Keycloak health checks](https://www.keycloak.org/observability/health). The probe is based on the publisher's container example. The bundled `26.0` image was tested locally for this configuration; the tag is retained by this change.

---

<div align="center">
  <img src="https://raw.githubusercontent.com/jihedbfr-art/cyber-skills/main/assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120">
  <br>
  <sub>A <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a> project</sub>
</div>
