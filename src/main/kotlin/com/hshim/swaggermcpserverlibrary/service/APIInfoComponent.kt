package com.hshim.swaggermcpserverlibrary.service

import com.hshim.swaggermcpserverlibrary.enums.ParameterType
import com.hshim.swaggermcpserverlibrary.model.APIInfoResponse
import com.hshim.swaggermcpserverlibrary.model.FieldInfo
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.lang.reflect.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaType

/**
 * API Information Component
 *
 * Caching Strategy:
 * - All API information is cached in memory at application startup
 * - Cache is immutable after initialization (no cache invalidation needed)
 * - Zero CPU overhead for subsequent lookups (simple list access)
 * - Memory usage: O(n) where n = number of API endpoints
 *
 * Performance Characteristics:
 * - Initialization: O(n) - scans all handler methods once at startup
 * - Lookup: O(1) - direct cache access
 * - Search/Filter: O(n) - linear scan of cached list (fast due to in-memory)
 */
@Component
class APIInfoComponent(private val handlerMapping: RequestMappingHandlerMapping) {

    /**
     * Immutable cache storing all API information
     * Populated once during initialization, never modified afterward
     */
    private var apiInfoCache: List<APIInfoResponse> = emptyList()
    private val logger = LoggerFactory.getLogger(APIInfoComponent::class.java)

    init {
        // Build cache at application startup
        getSwaggerAnnotatedApis()
        logger.info("API cache initialized with ${apiInfoCache.size} endpoints")
    }

    /**
     * Get all cached API information
     * Zero-cost operation after initialization
     */
    fun getAPIInfos() = apiInfoCache.takeIf { it.isNotEmpty() } ?: getSwaggerAnnotatedApis()

    private fun getSwaggerAnnotatedApis(): List<APIInfoResponse> {
        val result = mutableListOf<APIInfoResponse>()

        handlerMapping.handlerMethods.forEach { (mapping, handlerMethod: HandlerMethod) ->
            // Check for Swagger annotations (both v2 and v3)
            val controllerClass = handlerMethod.beanType
            val swaggerInfo = extractSwaggerInfo(handlerMethod.method, controllerClass)

            if (swaggerInfo != null) {
                val requestParams = handlerMethod.method.parameters
                    .filter { !it.type.name.contains("Model") }

                val requestSchema = mutableMapOf<String, Any>()
                val requestInfos = mutableListOf<FieldInfo>()

                requestParams.forEach { param ->
                    val paramType = detectParameterType(param)

                    when {
                        // Pageable 처리
                        param.type == Pageable::class.java -> {
                            requestInfos.add(FieldInfo("page", "int", "page number", true, ParameterType.QUERY))
                            requestInfos.add(FieldInfo("size", "int", "page size", true, ParameterType.QUERY))
                            requestInfos.add(
                                FieldInfo(
                                    "sort",
                                    "String",
                                    "page sort (ex: name,asc)",
                                    true,
                                    ParameterType.QUERY
                                )
                            )
                            requestSchema["page"] = "int"
                            requestSchema["size"] = "int"
                            requestSchema["sort"] = "String"
                        }
                        // @PathVariable 처리
                        paramType == ParameterType.PATH -> {
                            val pathVariableName = getPathVariableName(param)
                            val typeName = param.type.simpleName
                            val description = extractParameterDescription(param)
                            requestInfos.add(
                                FieldInfo(
                                    pathVariableName,
                                    typeName,
                                    description,
                                    false,
                                    ParameterType.PATH
                                )
                            )
                            requestSchema[pathVariableName] = typeName
                        }
                        // @RequestParam, @RequestHeader 등 단순 타입 파라미터 처리
                        paramType != ParameterType.BODY && isSimpleType(param.type) -> {
                            val paramName = getParameterName(param, paramType)
                            val typeName = param.type.simpleName
                            val description = extractParameterDescription(param)
                            val required = getParameterRequired(param, paramType)
                            requestInfos.add(FieldInfo(paramName, typeName, description, !required, paramType))
                            requestSchema[paramName] = typeName
                        }
                        // RequestBody 또는 복잡한 객체 처리
                        else -> {
                            // @RequestBody 파라미터 자체를 먼저 추가
                            if (paramType == ParameterType.BODY) {
                                val paramName = param.name
                                val typeName = param.type.simpleName
                                val description = extractParameterDescription(param)
                                requestInfos.add(FieldInfo(paramName, typeName, description, false, ParameterType.BODY))
                            }

                            // 필드 정보 추출
                            val fields = extractFieldInfos(param.type, paramType)
                            requestInfos.addAll(fields)
                            requestSchema.putAll(buildSchema(param.type))
                        }
                    }
                }

                val resType = extractActualResponseClass(handlerMethod.method.genericReturnType)
                val responseSchema = buildSchemaFromType(resType)
                val responseInfos = extractFieldInfosFromType(resType, null)

                result.add(
                    APIInfoResponse(
                        url = mapping.patternValues.first(),
                        method = mapping.methodsCondition.methods.first().name,
                        category = swaggerInfo.category,
                        title = swaggerInfo.title,
                        description = swaggerInfo.description,
                        requestSchema = requestSchema,
                        responseSchema = responseSchema,
                        requestInfos = requestInfos,
                        responseInfos = responseInfos
                    )
                )
            }
        }

        apiInfoCache = result
        return result
    }

    /**
     * Extract Swagger annotation information from method
     * Supports both Swagger 2.x (@ApiOperation) and OpenAPI 3.x (@Operation)
     * Priority: @Operation tags (method level) > @Tag (controller level)
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractSwaggerInfo(method: Method, controllerClass: Class<*>): SwaggerInfo? {
        var title = ""
        var description = ""
        var category = ""

        // Try OpenAPI 3.x annotation first (io.swagger.v3.oas.annotations.Operation)
        try {
            val operationClass = Class.forName("io.swagger.v3.oas.annotations.Operation") as Class<out Annotation>
            val annotation = method.getAnnotation(operationClass)
            if (annotation != null) {
                title = annotation.javaClass.getMethod("summary").invoke(annotation) as? String ?: ""
                description = annotation.javaClass.getMethod("description").invoke(annotation) as? String ?: ""
                val tags = annotation.javaClass.getMethod("tags").invoke(annotation) as? Array<*>
                category = tags?.firstOrNull()?.toString() ?: ""

                // If category is empty, try to get from controller @Tag
                if (category.isEmpty()) {
                    category = extractCategoryFromController(controllerClass)
                }

                return SwaggerInfo(
                    title = title,
                    description = description,
                    category = category
                )
            }
        } catch (_: ClassNotFoundException) {
            // OpenAPI 3.x not available, try Swagger 2.x
        } catch (_: Exception) {
            // Ignore and try Swagger 2.x
        }

        // Try Swagger 2.x annotation (io.swagger.annotations.ApiOperation)
        try {
            val apiOperationClass = Class.forName("io.swagger.annotations.ApiOperation") as Class<out Annotation>
            val annotation = method.getAnnotation(apiOperationClass)
            if (annotation != null) {
                title = annotation.javaClass.getMethod("value").invoke(annotation) as? String ?: ""
                description = annotation.javaClass.getMethod("notes").invoke(annotation) as? String ?: ""
                val tags = annotation.javaClass.getMethod("tags").invoke(annotation) as? Array<*>
                category = tags?.firstOrNull()?.toString() ?: ""

                // If category is empty, try to get from controller @Api
                if (category.isEmpty()) {
                    category = extractCategoryFromController(controllerClass)
                }

                return SwaggerInfo(
                    title = title,
                    description = description,
                    category = category
                )
            }
        } catch (_: ClassNotFoundException) {
            // Swagger 2.x not available
        } catch (_: Exception) {
            // Ignore
        }

        return null
    }

    /**
     * Extract category from controller-level @Tag or @Api annotation
     * OpenAPI 3.x: @Tag, Swagger 2.x: @Api
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractCategoryFromController(controllerClass: Class<*>): String {
        // Try OpenAPI 3.x @Tag
        try {
            val tagClass = Class.forName("io.swagger.v3.oas.annotations.tags.Tag") as Class<out Annotation>
            val annotation = controllerClass.getAnnotation(tagClass)
            if (annotation != null) {
                val name = annotation.javaClass.getMethod("name").invoke(annotation) as? String
                if (!name.isNullOrBlank()) return name
            }
        } catch (_: Exception) {
            // Ignore
        }

        // Try Swagger 2.x @Api
        try {
            val apiClass = Class.forName("io.swagger.annotations.Api") as Class<out Annotation>
            val annotation = controllerClass.getAnnotation(apiClass)
            if (annotation != null) {
                val tags = annotation.javaClass.getMethod("tags").invoke(annotation) as? Array<*>
                val tag = tags?.firstOrNull()?.toString()
                if (!tag.isNullOrBlank()) return tag

                // Fallback to value if tags is empty
                val value = annotation.javaClass.getMethod("value").invoke(annotation) as? String
                if (!value.isNullOrBlank()) return value
            }
        } catch (_: Exception) {
            // Ignore
        }

        return ""
    }

    /**
     * Extract parameter description from Swagger annotations
     * Supports both @Parameter (OpenAPI 3.x) and @ApiParam (Swagger 2.x)
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractParameterDescription(param: Parameter): String {
        // Try OpenAPI 3.x @Parameter
        try {
            val parameterClass = Class.forName("io.swagger.v3.oas.annotations.Parameter") as Class<out Annotation>
            val annotation = param.getAnnotation(parameterClass)
            if (annotation != null) {
                return annotation.javaClass.getMethod("description").invoke(annotation) as? String ?: ""
            }
        } catch (_: Exception) {
            // Ignore
        }

        // Try Swagger 2.x @ApiParam
        try {
            val apiParamClass = Class.forName("io.swagger.annotations.ApiParam") as Class<out Annotation>
            val annotation = param.getAnnotation(apiParamClass)
            if (annotation != null) {
                return annotation.javaClass.getMethod("value").invoke(annotation) as? String ?: ""
            }
        } catch (_: Exception) {
            // Ignore
        }

        return ""
    }

    private fun detectParameterType(param: Parameter): ParameterType {
        return when {
            param.isAnnotationPresent(RequestBody::class.java) -> ParameterType.BODY
            param.isAnnotationPresent(PathVariable::class.java) -> ParameterType.PATH
            param.isAnnotationPresent(RequestHeader::class.java) -> ParameterType.HEADER
            param.isAnnotationPresent(RequestParam::class.java) -> ParameterType.QUERY
            else -> ParameterType.QUERY
        }
    }

    private fun getPathVariableName(param: Parameter): String {
        val pathVariableAnnotation = param.getAnnotation(PathVariable::class.java)
        return when {
            pathVariableAnnotation.value.isNotEmpty() -> pathVariableAnnotation.value
            pathVariableAnnotation.name.isNotEmpty() -> pathVariableAnnotation.name
            else -> param.name
        }
    }

    private fun getParameterName(param: Parameter, paramType: ParameterType): String {
        return when (paramType) {
            ParameterType.QUERY -> {
                val annotation = param.getAnnotation(RequestParam::class.java)
                when {
                    annotation?.value?.isNotEmpty() == true -> annotation.value
                    annotation?.name?.isNotEmpty() == true -> annotation.name
                    else -> param.name
                }
            }

            ParameterType.HEADER -> {
                val annotation = param.getAnnotation(RequestHeader::class.java)
                when {
                    annotation?.value?.isNotEmpty() == true -> annotation.value
                    annotation?.name?.isNotEmpty() == true -> annotation.name
                    else -> param.name
                }
            }

            ParameterType.PATH -> {
                val annotation = param.getAnnotation(PathVariable::class.java)
                when {
                    annotation?.value?.isNotEmpty() == true -> annotation.value
                    annotation?.name?.isNotEmpty() == true -> annotation.name
                    else -> param.name
                }
            }

            else -> param.name
        }
    }

    private fun getParameterRequired(param: Parameter, paramType: ParameterType): Boolean {
        return when (paramType) {
            ParameterType.QUERY -> {
                param.getAnnotation(RequestParam::class.java)?.required ?: true
            }

            ParameterType.HEADER -> {
                param.getAnnotation(RequestHeader::class.java)?.required ?: true
            }

            ParameterType.PATH -> true // PathVariable은 항상 required
            ParameterType.BODY -> true // RequestBody는 항상 required (별도 체크)
        }
    }

    private fun extractActualResponseClass(returnType: Type): Type {
        return when (returnType) {
            is ParameterizedType -> {
                val rawType = returnType.rawType as Class<*>

                if (rawType.name.contains("ResponseEntity")) {
                    val innerType = returnType.actualTypeArguments.firstOrNull()
                    return extractActualResponseClass(innerType ?: Any::class.java)
                }

                returnType
            }

            is Class<*> -> returnType
            else -> Any::class.java
        }
    }

    private fun extractFieldInfos(
        clazz: Class<*>,
        paramType: ParameterType?,
        prefix: String = ""
    ): List<FieldInfo> {
        val fields = mutableListOf<FieldInfo>()

        // Skip reflection for simple types, arrays, and primitives
        if (isSimpleType(clazz) || clazz.isArray || clazz.isPrimitive) {
            return fields
        }

        try {
            val kClass = clazz.kotlin
            kClass.memberProperties.forEach { prop ->
                val path = if (prefix.isEmpty()) prop.name else "$prefix.${prop.name}"
                val type = prop.returnType
                val nullable = type.isMarkedNullable

                // Try to extract description from Swagger Schema annotation
                val description = extractFieldDescription(prop.name, clazz)

                val typeClass = getTypeClass(type.javaType)

                if (isSimpleType(typeClass)) {
                    fields.add(
                        FieldInfo(
                            path = path,
                            type = getSimpleTypeName(type.javaType),
                            description = description,
                            nullable = nullable,
                            parameterType = paramType
                        )
                    )
                } else {
                    fields.add(
                        FieldInfo(
                            path = path,
                            type = typeClass.simpleName,
                            description = description,
                            nullable = nullable,
                            parameterType = paramType
                        )
                    )
                    fields.addAll(extractFieldInfos(typeClass, paramType, path))
                }
            }
        } catch (_: Exception) {
            // Kotlin reflection이 실패하는 경우 무시
        }

        return fields
    }

    /**
     * Extract field description from Swagger @Schema annotation
     * Supports both Kotlin property annotations and Java field annotations
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractFieldDescription(fieldName: String, clazz: Class<*>): String {
        // Skip reflection for simple types, arrays, and primitives
        if (isSimpleType(clazz) || clazz.isArray || clazz.isPrimitive) {
            return ""
        }

        // Try Kotlin reflection first (for property annotations)
        try {
            val kClass = clazz.kotlin
            val property = kClass.memberProperties.find { it.name == fieldName }

            if (property != null) {
                // Try OpenAPI 3.x @Schema on property
                try {
                    val schemaClass =
                        Class.forName("io.swagger.v3.oas.annotations.media.Schema") as Class<out Annotation>
                    val annotations = property.annotations
                    val schemaAnnotation = annotations.find { schemaClass.isInstance(it) }
                    if (schemaAnnotation != null) {
                        val description =
                            schemaAnnotation.javaClass.getMethod("description").invoke(schemaAnnotation) as? String
                        if (!description.isNullOrBlank()) return description
                    }
                } catch (_: Exception) {
                    // Ignore
                }

                // Try Swagger 2.x @ApiModelProperty on property
                try {
                    val apiModelPropertyClass =
                        Class.forName("io.swagger.annotations.ApiModelProperty") as Class<out Annotation>
                    val annotations = property.annotations
                    val apiAnnotation = annotations.find { apiModelPropertyClass.isInstance(it) }
                    if (apiAnnotation != null) {
                        val value = apiAnnotation.javaClass.getMethod("value").invoke(apiAnnotation) as? String
                        if (!value.isNullOrBlank()) return value
                    }
                } catch (_: Exception) {
                    // Ignore
                }

                // Try to get annotation from backing field
                val javaField = property.javaField
                if (javaField != null) {
                    return extractFieldDescriptionFromJavaField(javaField)
                }
            }
        } catch (_: Exception) {
            // Kotlin reflection failed, try Java reflection
        }

        // Fallback to Java reflection (for Java classes or field annotations)
        try {
            val field = clazz.getDeclaredField(fieldName)
            return extractFieldDescriptionFromJavaField(field)
        } catch (_: Exception) {
            // Field not found or error
        }

        return ""
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractFieldDescriptionFromJavaField(field: Field): String {
        // Try OpenAPI 3.x @Schema
        try {
            val schemaClass = Class.forName("io.swagger.v3.oas.annotations.media.Schema") as Class<out Annotation>
            val annotation = field.getAnnotation(schemaClass)
            if (annotation != null) {
                val description = annotation.javaClass.getMethod("description").invoke(annotation) as? String
                if (!description.isNullOrBlank()) return description
            }
        } catch (_: Exception) {
            // Ignore
        }

        // Try Swagger 2.x @ApiModelProperty
        try {
            val apiModelPropertyClass =
                Class.forName("io.swagger.annotations.ApiModelProperty") as Class<out Annotation>
            val annotation = field.getAnnotation(apiModelPropertyClass)
            if (annotation != null) {
                val value = annotation.javaClass.getMethod("value").invoke(annotation) as? String
                if (!value.isNullOrBlank()) return value
            }
        } catch (_: Exception) {
            // Ignore
        }

        return ""
    }

    private fun getTypeClass(javaType: Type): Class<*> {
        return when (javaType) {
            is Class<*> -> javaType
            is ParameterizedType -> {
                val rawType = javaType.rawType as Class<*>
                if (rawType == List::class.java || rawType == Page::class.java) {
                    val actualType = javaType.actualTypeArguments.firstOrNull()
                    (actualType as? Class<*>) ?: Any::class.java
                } else {
                    rawType
                }
            }

            else -> Any::class.java
        }
    }

    private fun isSimpleType(clazz: Class<*>): Boolean {
        return clazz.isPrimitive ||
                clazz.`package`?.name?.startsWith("java.") == true ||
                clazz.isEnum ||
                clazz == String::class.java ||
                Number::class.java.isAssignableFrom(clazz) ||
                clazz == Boolean::class.java
    }

    private fun getSimpleTypeName(javaType: Type): String {
        return when (javaType) {
            is Class<*> -> javaType.simpleName
            is ParameterizedType -> {
                val raw = (javaType.rawType as Class<*>).simpleName
                val args = javaType.actualTypeArguments.joinToString(", ") {
                    getSimpleTypeName(it)
                }
                "$raw<$args>"
            }

            else -> javaType.typeName
        }
    }

    private fun buildSchemaFromType(type: Type): Any {
        return when (type) {
            is ParameterizedType -> {
                val rawType = type.rawType as Class<*>
                if (rawType == Page::class.java || rawType == List::class.java) {
                    val innerType = type.actualTypeArguments.firstOrNull()
                    val innerSchema = if (innerType is Class<*>) {
                        buildSchema(innerType)
                    } else if (innerType is ParameterizedType) {
                        buildSchemaFromType(innerType)
                    } else {
                        mapOf("item" to "Any")
                    }

                    if (rawType == Page::class.java) {
                        mapOf(
                            "content" to listOf(innerSchema),
                            "pageable" to "Pageable",
                            "totalPages" to "int",
                            "totalElements" to "long",
                            "last" to "boolean",
                            "size" to "int",
                            "number" to "int",
                            "numberOfElements" to "int",
                            "first" to "boolean",
                            "empty" to "boolean"
                        )
                    } else {
                        listOf(innerSchema)
                    }
                } else {
                    buildSchema(rawType)
                }
            }

            is Class<*> -> buildSchema(type)
            else -> emptyMap<String, Any>()
        }
    }

    private fun buildSchema(clazz: Class<*>): Map<String, Any> {
        // Skip reflection for simple types, arrays, and primitives
        if (isSimpleType(clazz) || clazz.isArray || clazz.isPrimitive) {
            return emptyMap()
        }

        return try {
            clazz.kotlin.memberProperties.associate { prop ->
                val name = prop.name
                val type = prop.returnType.javaType

                fun parseType(javaType: Type): Any {
                    return when (javaType) {
                        is Class<*> -> {
                            if (isSimpleType(javaType)) {
                                javaType.simpleName
                            } else {
                                buildSchema(javaType)
                            }
                        }

                        is ParameterizedType -> {
                            val raw = javaType.rawType as Class<*>
                            if (raw == List::class.java) {
                                val innerType = javaType.actualTypeArguments.firstOrNull()
                                if (innerType is Class<*> && !isSimpleType(innerType)) {
                                    listOf(buildSchema(innerType))
                                } else {
                                    val args = if (innerType != null) getSimpleTypeName(innerType) else "Any"
                                    "List<$args>"
                                }
                            } else {
                                val rawName = raw.simpleName
                                val innerType = javaType.actualTypeArguments.firstOrNull()
                                val args = if (innerType != null) getSimpleTypeName(innerType) else "Any"
                                "$rawName<$args>"
                            }
                        }

                        else -> javaType.typeName
                    }
                }

                name to parseType(type)
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun extractFieldInfosFromType(
        type: Type,
        paramType: ParameterType?,
        prefix: String = ""
    ): List<FieldInfo> {
        return when (type) {
            is ParameterizedType -> {
                val rawType = type.rawType as Class<*>
                if (rawType == Page::class.java || rawType == List::class.java) {
                    val innerType = type.actualTypeArguments.firstOrNull()
                    val clazz = when (innerType) {
                        is Class<*> -> innerType
                        is ParameterizedType -> innerType.rawType as Class<*>
                        else -> Any::class.java
                    }

                    val fields = mutableListOf<FieldInfo>()

                    if (rawType == Page::class.java) {
                        fields.add(FieldInfo("content", rawType.simpleName, "페이지 컨텐츠", false, paramType))
                        fields.add(FieldInfo("totalPages", "int", "전체 페이지 수", false, paramType))
                        fields.add(FieldInfo("totalElements", "long", "전체 요소 수", false, paramType))
                        fields.add(FieldInfo("size", "int", "페이지 크기", false, paramType))
                        fields.add(FieldInfo("number", "int", "현재 페이지 번호", false, paramType))
                    }

                    fields.addAll(
                        extractFieldInfos(
                            clazz,
                            paramType,
                            if (rawType == Page::class.java) "content" else "items"
                        )
                    )
                    fields
                } else {
                    extractFieldInfos(rawType, paramType, prefix)
                }
            }

            is Class<*> -> extractFieldInfos(type, paramType, prefix)
            else -> emptyList()
        }
    }

    private data class SwaggerInfo(
        val title: String,
        val description: String,
        val category: String
    )
}