package dev.jihed.mcpguard.config;

import io.github.jihedbfr_art.keycloak.error.ProblemDetailAccessDeniedHandler;
import io.github.jihedbfr_art.keycloak.error.ProblemDetailAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every MCP tool call must carry a Keycloak-issued JWT. Authentication (is this a valid token?)
 * happens here; per-tool authorization (which role can call which tool?) happens on the tool
 * methods themselves via {@code @PreAuthorize} — see {@link dev.jihed.mcpguard.tools.GuardedTools}.
 * Health checks stay open so container/orchestrator probes don't need a token.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      JwtAuthenticationConverter jwtAuthenticationConverter,
      ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
      ProblemDetailAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }
}
