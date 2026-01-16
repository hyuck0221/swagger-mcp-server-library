package com.hshim.swaggermcpserverlibrary.model

data class APIInfoResponse (
    val url: String,
    val method: String,
    val category: String = "",
    val title: String,
    val description: String,
    val requestSchema: Map<String, Any>,
    val responseSchema: Any,
    val requestInfos: List<FieldInfo>,
    val responseInfos: List<FieldInfo>
)