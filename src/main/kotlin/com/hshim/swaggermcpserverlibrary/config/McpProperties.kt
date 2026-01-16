package com.hshim.swaggermcpserverlibrary.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * MCP Server Configuration Properties
 *
 * Configurable properties in application.yml:
 * - mcp.enabled: Enable or disable the MCP server (default: true)
 * - mcp.server-info.name: MCP server name (default: "Spring API Docs MCP Server")
 * - mcp.server-info.version: MCP server version (default: "1.0.0")
 *
 * Note: SSE and Message endpoint paths are fixed and cannot be customized.
 */
@Configuration
@ConfigurationProperties(prefix = "mcp")
class McpProperties {

    companion object {
        /**
         * SSE endpoint path (fixed, cannot be changed)
         */
        const val SSE_PATH = "/mcp/sse"

        /**
         * Message endpoint path (fixed, cannot be changed)
         */
        const val MESSAGE_PATH = "/mcp/message"
    }

    /**
     * Enable or disable the MCP server
     *
     * Set to true to enable the MCP server, false to disable it.
     * Default: true
     *
     * Example:
     * ```yaml
     * mcp:
     *   enabled: false  # Disable MCP server
     * ```
     */
    var enabled: Boolean = true

    /**
     * MCP server information
     *
     * Configure the server name and version.
     *
     * Example:
     * ```yaml
     * mcp:
     *   server-info:
     *     name: "My Custom MCP Server"
     *     version: "2.0.0"
     * ```
     */
    var serverInfo = ServerInfo()

    /**
     * MCP server information configuration
     */
    class ServerInfo {
        /**
         * MCP server name
         *
         * The server name displayed to MCP clients.
         * Default: "Spring API Docs MCP Server"
         */
        var name: String = "Spring API Docs MCP Server"

        /**
         * MCP server version
         *
         * The server version displayed to MCP clients.
         * Default: "1.0.0"
         */
        var version: String = "1.0.0"
    }
}
