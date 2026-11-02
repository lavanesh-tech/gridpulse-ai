package com.gridpulse.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
@ConfigurationPropertiesScan
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    GridPulseClient gridPulseClient(GridPulseProperties props) {
        return new GridPulseClient(RestClient.builder(), props);
    }

    /** Every @Tool method on GridPulseTools becomes an MCP tool. */
    @Bean
    ToolCallbackProvider gridPulseToolProvider(GridPulseTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
