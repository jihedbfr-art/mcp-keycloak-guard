# mcp-keycloak-guard

[Read in English](README.md)

Serveur MCP de démonstration qui contrôle l'accès à chaque outil selon les rôles Keycloak de l'appelant.

## Problème traité

Un jeton valide identifie l'appelant. Il ne suffit pas à déterminer quels outils il peut exécuter. Ce projet associe le serveur MCP de Spring AI au serveur de ressources OAuth2 de Spring Security. Les rôles du jeton Keycloak deviennent des autorisations Spring ; chaque méthode exposée comme outil porte sa propre règle `@PreAuthorize`.

Le rôle `mcp-reader` donne accès à l'outil de lecture. L'outil d'administration exige `mcp-admin`. Le test d'intégration couvre le trajet SSE/MCP complet : une connexion sans jeton reçoit HTTP 401, le lecteur obtient une erreur MCP sur l'outil d'administration et l'administrateur peut l'appeler.

## Essai local

Docker, Java 17 et Maven sont nécessaires. Depuis la racine du dépôt :

```bash
docker compose -f docker/docker-compose.yml up -d
mvn spring-boot:run
```

Cette version de Spring AI expose une connexion **SSE** sur `/sse` et reçoit les messages JSON-RPC sur `/mcp/message`. Le client MCP doit envoyer son jeton sur les deux requêtes. Un POST direct sur `/mcp` renvoie 404 avec cette version.

Pour reproduire le parcours lecteur/administrateur automatisé avec Docker actif :

```bash
mvn verify
```

Le test obtient les jetons du domaine de démonstration, ouvre les connexions SSE authentifiées et appelle les deux outils avec un client MCP. Il essaie de nouveau le jeton lecteur après l'appel administrateur pour détecter toute fuite de contexte.

## Organisation

- [`SecurityConfig`](src/main/java/dev/jihed/mcpguard/config/SecurityConfig.java) exige un JWT valide pour les requêtes autres que les contrôles de santé.
- [`GuardedTools`](src/main/java/dev/jihed/mcpguard/tools/GuardedTools.java) applique une règle de rôle à chacun des deux outils de démonstration.
- [`SecurityContextPropagationConfig`](src/main/java/dev/jihed/mcpguard/config/SecurityContextPropagationConfig.java) transmet le contexte de sécurité de la requête au worker Reactor qui exécute l'outil.
- [`spring-keycloak-toolkit`](https://github.com/jihedbfr-art/spring-keycloak-toolkit) convertit les rôles du domaine Keycloak en autorisations Spring Security.
- Le transport MCP est fourni par `spring-ai-starter-mcp-server-webmvc` sur `/sse` et `/mcp/message`.

## Limites

Le projet ne gère pas l'échange de jetons OAuth2 pour la délégation d'un agent au nom d'un utilisateur. Le domaine Keycloak fourni est une démonstration à deux rôles et deux utilisateurs, sans configuration de production.

Le test `GuardedToolsIT` utilise Keycloak avec Testcontainers et un client MCP SSE. Il nécessite un moteur Docker actif. Le refus d'un outil est transmis comme erreur MCP, pas comme HTTP 403 ; la connexion non authentifiée reçoit HTTP 401.

## Suite envisagée

- Tester le comportement avec plusieurs clients simultanés et des transports futurs.
- Ajouter un second jeu d'outils indépendant pour tester le même modèle d'accès.
- Étudier les portées OAuth2 par outil et la délégation par échange de jetons.

## Licence

MIT — voir [LICENSE](LICENSE).
