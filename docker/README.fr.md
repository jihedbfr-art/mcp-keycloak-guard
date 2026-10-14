# Lancer la démo Keycloak locale et vérifier sa disponibilité

[English version](README.md)

Depuis la racine du dépôt, attendre que Keycloak soit prêt avant de démarrer l'application :

```bash
docker compose -f docker/docker-compose.yml up -d --wait --wait-timeout 180
docker compose -f docker/docker-compose.yml ps
mvn spring-boot:run
```

La démo publie Keycloak sur le port 8080. L'issuer par défaut de l'application est `http://localhost:8080/realms/mcp-guard` ; son port HTTP est 8081. Ces commandes utilisent le realm de développement et les identifiants de démonstration fournis. Remplacer cette configuration pour un déploiement en production.

## Ce que vérifie la sonde

`KC_HEALTH_ENABLED=true` active les endpoints de santé. La sonde envoie une requête HTTP HEAD à `/health/ready` sur le port de gestion 9000 du conteneur et exige HTTP 200. Elle utilise la redirection TCP de Bash, car l'image Keycloak ne contient pas curl. Le port 9000 reste interne au conteneur ; la démo ne le publie pas sur l'hôte.

Ce contrôle vérifie la disponibilité de Keycloak. Il ne prouve pas qu'un utilisateur MCP possède le rôle requis pour un outil. Exécuter les tests d'autorisation séparément :

```bash
mvn verify
```

## Diagnostiquer un conteneur unhealthy

```bash
docker compose -f docker/docker-compose.yml ps
docker compose -f docker/docker-compose.yml logs --tail 60 keycloak
```

Vérifier que l'import du realm se termine et que le processus démarre correctement. L'ouverture du port 8080 ne prouve pas que l'endpoint de disponibilité est activé. L'ancienne sonde interrogeait `/health/ready` sur 8080 sans activer les contrôles de santé ; le realm pouvait être disponible alors que cette URL répondait 404.

Utiliser le même fichier Compose pour arrêter cette démo :

```bash
docker compose -f docker/docker-compose.yml down
```

Source : [contrôles de santé Keycloak](https://www.keycloak.org/observability/health). La sonde reprend l'exemple de conteneur de l'éditeur. L'image `26.0` fournie a été testée localement pour cette configuration ; ce changement conserve le tag.

---

<div align="center">
  <img src="https://raw.githubusercontent.com/jihedbfr-art/cyber-skills/main/assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120">
  <br>
  <sub>Un projet <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a></sub>
</div>
