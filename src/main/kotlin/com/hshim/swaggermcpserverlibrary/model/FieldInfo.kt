package com.hshim.swaggermcpserverlibrary.model

import com.hshim.swaggermcpserverlibrary.enums.ParameterType

data class FieldInfo(
    val path: String,              // 필드 경로 (예: "b.c", "themes.id")
    val type: String,              // 필드 타입 (예: "String", "Int")
    val description: String,       // 필드 설명
    val nullable: Boolean,         // null 가능 여부
    val parameterType: ParameterType? = null  // request인 경우에만 사용
)

