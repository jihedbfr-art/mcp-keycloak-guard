package dev.jihed.mcpguard.config;

import io.micrometer.context.ContextRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Hooks;

@Configuration
public class SecurityContextPropagationConfig {

  @PostConstruct
  void enableSecurityContextPropagation() {
    // MCP tool callbacks run on Reactor workers, outside the HTTP request thread.
    // Carry each request's SecurityContext to that worker and clear it afterwards.
    ContextRegistry.getInstance()
        .registerThreadLocalAccessor(
            "spring.security.context",
            SecurityContextHolder::getContext,
            SecurityContextHolder::setContext,
            SecurityContextHolder::clearContext);
    Hooks.enableAutomaticContextPropagation();
  }
}
