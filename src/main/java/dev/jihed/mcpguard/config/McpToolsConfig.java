package dev.jihed.mcpguard.config;

import dev.jihed.mcpguard.tools.GuardedTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolsConfig {

  @Bean
  public ToolCallbackProvider guardedToolCallbacks(GuardedTools tools) {
    return MethodToolCallbackProvider.builder().toolObjects(tools).build();
  }
}
