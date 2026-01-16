package com.hshim.swaggermcpserverlibrary.service

import com.hshim.swaggermcpserverlibrary.config.McpProperties
import com.hshim.swaggermcpserverlibrary.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import util.ClassUtil.classToJson

@Service
class McpService(
    private val apiInfoComponent: APIInfoComponent,
    private val mcpProperties: McpProperties
) {
    private val logger = LoggerFactory.getLogger(McpService::class.java)

    fun handleMessage(requestBody: Map<String, Any>): JsonRpcResponse? {
        val method = requestBody["method"] as? String ?: return null
        val id = requestBody["id"]

        logger.info("MCP Request: method=$method, id=$id")

        return when (method) {
            "initialize" -> handleInitialize(id)
            "notifications/initialized" -> null // No response needed
            "tools/list" -> handleToolsList(id)
            "tools/call" -> handleToolsCall(id, requestBody)
            else -> {
                // Ignore internal JSON-RPC methods
                if (method.startsWith("$")) null
                else errorResponse(id, -32601, "Method not found: $method")
            }
        }
    }

    private fun handleInitialize(id: Any?): JsonRpcResponse {
        val result = InitializeResult(
            protocolVersion = "2024-11-05",
            capabilities = Capabilities(
                tools = ToolsCapability(listChanged = true)
            ),
            serverInfo = ServerInfo(
                name = mcpProperties.serverInfo.name,
                version = mcpProperties.serverInfo.version
            )
        )

        return JsonRpcResponse(
            id = id,
            result = result
        )
    }

    private fun handleToolsList(id: Any?): JsonRpcResponse {
        val tools = listOf(
            ToolDefinition(
                name = "getApiCount",
                description = "Get total count of available APIs, optionally filtered by category.",
                inputSchema = InputSchema(
                    properties = mapOf(
                        "category" to PropertyDefinition(
                            type = "string",
                            description = "Optional category to filter API count"
                        )
                    )
                )
            ),
            ToolDefinition(
                name = "getApiDetail",
                description = "Get detailed information about a specific API endpoint.",
                inputSchema = InputSchema(
                    properties = mapOf(
                        "url" to PropertyDefinition(
                            type = "string",
                            description = "API endpoint URL"
                        ),
                        "method" to PropertyDefinition(
                            type = "string",
                            description = "HTTP method (GET, POST, PUT, DELETE, etc.)"
                        )
                    ),
                    required = listOf("url", "method")
                )
            ),
            ToolDefinition(
                name = "searchApis",
                description = "Search APIs by keyword, category, or URL pattern. Returns matching API information.",
                inputSchema = InputSchema(
                    properties = mapOf(
                        "keyword" to PropertyDefinition(
                            type = "string",
                            description = "Search keyword (searches in title, description, URL)"
                        ),
                        "category" to PropertyDefinition(
                            type = "string",
                            description = "Filter by category"
                        ),
                        "method" to PropertyDefinition(
                            type = "string",
                            description = "Filter by HTTP method (GET, POST, PUT, DELETE, etc.)"
                        )
                    )
                )
            )
        )

        return JsonRpcResponse(
            id = id,
            result = ToolsListResult(tools = tools)
        )
    }

    private fun handleToolsCall(id: Any?, requestBody: Map<String, Any>): JsonRpcResponse {
        val params = requestBody["params"] as? Map<String, Any?>
        val name = params?.get("name") as? String
        val arguments = params?.get("arguments") as? Map<String, Any?>

        return when (name) {
            "getApiCount" -> handleGetApiCount(id, arguments)
            "getApiDetail" -> handleGetApiDetail(id, arguments)
            "searchApis" -> handleSearchApis(id, arguments)
            else -> errorResponse(id, -32601, "Tool not found: $name")
        }
    }

    /**
     * Get API count (optimized with cached data)
     */
    private fun handleGetApiCount(id: Any?, arguments: Map<String, Any?>?): JsonRpcResponse {
        val category = arguments?.get("category") as? String

        // Use cached data from APIInfoComponent
        val allApis = apiInfoComponent.getAPIInfos()

        val count = if (!category.isNullOrBlank()) {
            allApis.count { it.category.equals(category, ignoreCase = true) }
        } else {
            allApis.size
        }

        val message = if (!category.isNullOrBlank()) {
            "Total $count APIs in category '$category'"
        } else {
            "Total $count APIs available"
        }

        val data = ApiCountData(
            count = count,
            category = category,
            message = message
        )

        return JsonRpcResponse(
            id = id,
            result = ToolCallResult(
                content = listOf(
                    ContentItem(text = data.classToJson())
                )
            )
        )
    }

    /**
     * Get API detail (optimized with cached data)
     */
    private fun handleGetApiDetail(id: Any?, arguments: Map<String, Any?>?): JsonRpcResponse {
        val url = arguments?.get("url") as? String
        val method = arguments?.get("method") as? String

        if (url.isNullOrBlank() || method.isNullOrBlank()) {
            return errorResponse(id, -32602, "Missing required parameters: url and method")
        }

        // Use cached data from APIInfoComponent
        val allApis = apiInfoComponent.getAPIInfos()
        val apiDetail = allApis.find {
            it.url == url && it.method.equals(method, ignoreCase = true)
        }

        if (apiDetail == null) {
            return errorResponse(id, -32600, "API not found: $method $url")
        }

        return JsonRpcResponse(
            id = id,
            result = ToolCallResult(
                content = listOf(
                    ContentItem(text = apiDetail.classToJson())
                )
            )
        )
    }

    /**
     * Search APIs (optimized with cached data)
     */
    private fun handleSearchApis(id: Any?, arguments: Map<String, Any?>?): JsonRpcResponse {
        val keyword = arguments?.get("keyword") as? String
        val category = arguments?.get("category") as? String
        val method = arguments?.get("method") as? String

        // Use cached data from APIInfoComponent
        var results = apiInfoComponent.getAPIInfos()

        // Apply filters (all operations on in-memory cached data)
        if (!category.isNullOrBlank()) {
            results = results.filter { it.category.equals(category, ignoreCase = true) }
        }

        if (!method.isNullOrBlank()) {
            results = results.filter { it.method.equals(method, ignoreCase = true) }
        }

        if (!keyword.isNullOrBlank()) {
            val lowerKeyword = keyword.lowercase()
            results = results.filter { api ->
                api.title.lowercase().contains(lowerKeyword) ||
                        api.description.lowercase().contains(lowerKeyword) ||
                        api.url.lowercase().contains(lowerKeyword)
            }
        }

        val summary = buildString {
            append("Found ${results.size} API(s)")
            if (!keyword.isNullOrBlank()) append(" matching keyword '$keyword'")
            if (!category.isNullOrBlank()) append(" in category '$category'")
            if (!method.isNullOrBlank()) append(" with method '$method'")
        }

        val data = SearchApisData(
            summary = summary,
            count = results.size,
            apis = results
        )

        return JsonRpcResponse(
            id = id,
            result = ToolCallResult(
                content = listOf(
                    ContentItem(text = data.classToJson())
                )
            )
        )
    }

    private fun errorResponse(id: Any?, code: Int, message: String): JsonRpcResponse {
        return JsonRpcResponse(
            id = id,
            error = JsonRpcError(
                code = code,
                message = message
            )
        )
    }
}
