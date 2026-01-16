package com.hshim.swaggermcpserverlibrary.config

import com.hshim.swaggermcpserverlibrary.controller.McpController
import com.hshim.swaggermcpserverlibrary.service.APIInfoComponent
import com.hshim.swaggermcpserverlibrary.service.McpService
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan

/**
 * Spring Boot Auto-configuration for MCP Server
 *
 * This configuration is automatically loaded when the library is added as a dependency.
 * It can be disabled by setting `mcp.enabled=false` in application.yml/properties.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "mcp", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(McpProperties::class)
@ComponentScan(basePackageClasses = [APIInfoComponent::class, McpService::class, McpController::class])
class McpAutoConfiguration
