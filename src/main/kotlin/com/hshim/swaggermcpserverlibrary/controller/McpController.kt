package com.hshim.swaggermcpserverlibrary.controller

import com.hshim.swaggermcpserverlibrary.service.McpService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import util.ClassUtil.classToJson
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/mcp")
@ConditionalOnProperty(prefix = "mcp", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class McpController(private val mcpService: McpService) {
    private val logger = LoggerFactory.getLogger(McpController::class.java)

    // Session ID -> SseEmitter 맵
    private val emitters = ConcurrentHashMap<String, SseEmitter>()

    @EventListener(ContextClosedEvent::class)
    fun onShutdown() {
        logger.info("Closing all SSE emitters due to shutdown...")
        emitters.forEach { (_, emitter) ->
            try {
                emitter.complete()
            } catch (_: Exception) {
            }
        }
        emitters.clear()
    }

    @GetMapping("/sse")
    fun connectSse(): SseEmitter {
        val sessionId = UUID.randomUUID().toString()
        val emitter = SseEmitter(Long.MAX_VALUE) // 무제한 타임아웃

        emitters[sessionId] = emitter

        emitter.onCompletion {
            logger.info("SSE Completed: $sessionId")
            emitters.remove(sessionId)
        }
        emitter.onTimeout {
            logger.info("SSE Timeout: $sessionId")
            emitters.remove(sessionId)
        }
        emitter.onError {
            logger.error("SSE Error: $sessionId")
            emitters.remove(sessionId)
        }

        // 1. 엔드포인트 이벤트 전송
        try {
            val endpointUri = "/mcp/message?sessionId=$sessionId"
            emitter.send(SseEmitter.event().name("endpoint").data(endpointUri))
            logger.info("SSE Connected: $sessionId")
        } catch (e: Exception) {
            emitters.remove(sessionId)
        }

        return emitter
    }

    @PostMapping("/message")
    fun handleMessage(
        @RequestParam sessionId: String,
        @RequestBody requestBody: Map<String, Any>
    ) {
        val emitter = emitters[sessionId]
        if (emitter == null) {
            logger.warn("Session not found: $sessionId")
            throw IllegalArgumentException("Session not found")
        }

        // 비동기 처리를 위해 별도 스레드 풀을 쓰거나 여기서는 간단히 동기 실행 후 전송
        try {
            val response = mcpService.handleMessage(requestBody)
            if (response != null) {
                val json = response.classToJson()
                emitter.send(SseEmitter.event().name("message").data(json))
            }
        } catch (e: Exception) {
            logger.error("Error handling message for session $sessionId", e)
            // 에러 발생 시 JSON-RPC 에러 전송 시도
            try {
                val errorRes = mapOf(
                    "jsonrpc" to "2.0",
                    "id" to requestBody["id"],
                    "error" to mapOf("code" to -32603, "message" to "Internal error: ${e.message}")
                )
                emitter.send(SseEmitter.event().name("message").data(errorRes.classToJson()))
            } catch (ignore: Exception) {
            }
        }
    }
}
