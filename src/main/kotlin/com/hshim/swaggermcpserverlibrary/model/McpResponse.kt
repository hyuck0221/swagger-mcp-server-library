package com.hshim.swaggermcpserverlibrary.model

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * JSON-RPC 2.0 Base Response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Any?,
    val result: Any? = null,
    val error: JsonRpcError? = null
)

/**
 * JSON-RPC 2.0 Error
 */
data class JsonRpcError(
    val code: Int,
    val message: String
)

/**
 * Initialize Response
 */
data class InitializeResult(
    val protocolVersion: String,
    val capabilities: Capabilities,
    val serverInfo: ServerInfo
)

data class Capabilities(
    val tools: ToolsCapability
)

data class ToolsCapability(
    val listChanged: Boolean
)

data class ServerInfo(
    val name: String,
    val version: String
)

/**
 * Tools List Response
 */
data class ToolsListResult(
    val tools: List<ToolDefinition>
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: InputSchema
)

data class InputSchema(
    val type: String = "object",
    val properties: Map<String, PropertyDefinition>,
    val required: List<String>? = null
)

data class PropertyDefinition(
    val type: String,
    val description: String
)

/**
 * Tool Call Response
 */
data class ToolCallResult(
    val content: List<ContentItem>
)

data class ContentItem(
    val type: String = "text",
    val text: String
)

/**
 * API Count Response Data
 */
data class ApiCountData(
    val count: Int,
    val category: String?,
    val message: String
)

/**
 * Search APIs Response Data
 */
data class SearchApisData(
    val summary: String,
    val count: Int,
    val apis: List<APIInfoResponse>
)
