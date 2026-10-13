# mcp-keycloak-guard

[Read in English](README.md)

Serveur MCP de démonstration qui contrôle l'accès à chaque outil selon les rôles Keycloak de l'appelant.

## Problème traité

Un jeton valide identifie l'appelant. Il ne suffit pas à déterminer quels outils il peut exécuter. Ce projet associe le serveur MCP de Spring AI au serveur de ressources OAuth2 de Spring Security. Les rôles du jeton Keycloak deviennent des autorisations Spring ; chaque méthode exposée comme outil porte sa propre règle `@PreAuthorize`.

Le rôle `mcp-reader` donne accès à l'outil de lecture. L'outil d'administration exige `mcp-admin`. Le test d'intégration confirme le refus au niveau de la méthode protégée. La réponse HTTP/MCP correspondante reste à vérifier par un test de bout en bout.

## Essai local

Docker, Java 17 et Maven sont nécessaires. Depuis la racine du dépôt :

```bash
docker compose -f docker/docker-compose.yml up -d
mvn spring-boot:run
```

Dans un autre terminal, obtenir un jeton pour l'utilisateur de démonstration `reader` :

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/mcp-guard/protocol/openid-connect/token \
  -d grant_type=password -d client_id=mcp-guard-client -d username=reader -d password=reader \
  | jq -r .access_token)
```

Appeler l'outil de lecture :

```bash
curl -s http://localhost:8081/mcp -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"getServerStatus","arguments":{}}}'
```

Le même jeton ne possède pas le rôle requis pour `restartDownstreamService`. Le compte de démonstration `admin` possède les deux rôles. Ces identifiants servent uniquement au domaine Keycloak local fourni avec l'exemple.

## Organisation

- [`SecurityConfig`](src/main/java/dev/jihed/mcpguard/config/SecurityConfig.java) exige un JWT valide pour les requêtes autres que les contrôles de santé.
- [`GuardedTools`](src/main/java/dev/jihed/mcpguard/tools/GuardedTools.java) applique une règle de rôle à chacun des deux outils de démonstration.
- [`spring-keycloak-toolkit`](https://github.com/jihedbfr-art/spring-keycloak-toolkit) convertit les rôles du domaine Keycloak en autorisations Spring Security.
- Le transport MCP est fourni par `spring-ai-starter-mcp-server-webmvc` sur `/mcp`.

## Limites

Le projet ne gère pas l'échange de jetons OAuth2 pour la délégation d'un agent au nom d'un utilisateur. Le domaine Keycloak fourni est une démonstration à deux rôles et deux utilisateurs, sans configuration de production.

Le test `GuardedToolsIT` utilise Keycloak avec Testcontainers et vérifie l'accès aux méthodes protégées. Il nécessite un moteur Docker actif. Il ne vérifie pas encore l'ensemble du trajet HTTP/MCP ni le code de réponse observé par le client.

## Suite envisagée

- Vérifier les refus et les autorisations sur de vrais appels HTTP/MCP automatisés.
- Ajouter un second jeu d'outils indépendant pour tester le même modèle d'accès.
- Étudier les portées OAuth2 par outil et la délégation par échange de jetons.

## Licence

MIT — voir [LICENSE](LICENSE).
