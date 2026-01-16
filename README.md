# Spring Swagger MCP Server

[English](README.EN.md) | 한국어

Swagger/OpenAPI annotation 기반 API 문서를 MCP(Model Context Protocol) 서버로 제공하는 Spring Boot 라이브러리입니다.

## 특징

- ✅ **Swagger 2.x & OpenAPI 3.x 지원**: 두 버전 모두 호환
- ✅ **자동 SSE 연결**: 라이브러리 추가만으로 MCP 서버 활성화
- ✅ **메모리 캐싱**: 서버 부하 없는 API 정보 제공
- ✅ **Java 17+ 호환**: Spring Boot 2.7+ 및 3.x 지원
- ✅ **검색 기능**: 키워드, 카테고리, HTTP 메서드로 API 검색

## 빠른 시작

### 1. 의존성 추가

```gradle
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.hyuck0221:spring-api-docs-mcp:0.1.0")
}
```

### 2. Swagger Annotation 추가

```kotlin
@RestController
@Tag(name = "User")  // Controller 레벨 카테고리
class UserController {

    @Operation(
        summary = "사용자 조회",
        description = "ID로 사용자 정보를 조회합니다",
        tags = ["User"]  // Method 레벨 카테고리 (우선순위 높음)
    )
    @GetMapping("/users/{id}")
    fun getUser(
        @Parameter(description = "사용자 ID")
        @PathVariable id: Long
    ): User {
        // ...
    }

    @Operation(summary = "사용자 검색")
    @GetMapping("/users")
    fun searchUsers(
        @RequestParam name: String,
        @RequestParam(required = false) age: Int?
    ): List<User> {
        // ...
    }
}

data class User(
    @Schema(description = "사용자 ID")
    val id: Long,

    @Schema(description = "사용자 이름")
    val name: String
)
```

### 3. 설정 (application.yml)

```yaml
mcp:
  # MCP 서버 활성화 여부 (기본값: true)
  enabled: true

  # SSE 엔드포인트 경로 설정
  sse-path: /mcp/sse

  # Message 엔드포인트 경로 설정
  message-path: /mcp/message

  # MCP 서버 정보
  server-info:
    name: My API Docs Server
    version: 1.0.0
```

### 4. 애플리케이션 실행

```bash
./gradlew bootRun
```

**MCP 엔드포인트:**
- SSE: `http://localhost:8080/mcp/sse` (경로는 설정으로 변경 가능)
- Message: `http://localhost:8080/mcp/message` (경로는 설정으로 변경 가능)

> 도메인과 포트는 자동으로 현재 요청에서 추출되며, 경로만 설정 파일에서 변경할 수 있습니다.

## MCP Tools

### 1. getApiCount
API 개수 조회 (카테고리별 필터링 가능)

```json
{
  "name": "getApiCount",
  "arguments": {
    "category": "User"  // Optional
  }
}
```

**Response:**
```json
{
  "count": 5,
  "category": "User",
  "message": "Total 5 APIs in category 'User'"
}
```

### 2. getApiDetail
특정 API의 상세 정보 조회

```json
{
  "name": "getApiDetail",
  "arguments": {
    "url": "/users/{id}",
    "method": "GET"
  }
}
```

**Response:**
```json
{
  "url": "/users/{id}",
  "method": "GET",
  "category": "User",
  "title": "사용자 조회",
  "description": "ID로 사용자 정보를 조회합니다",
  "requestSchema": {
    "id": "Long"
  },
  "responseSchema": {
    "id": "Long",
    "name": "String"
  },
  "requestInfos": [...],
  "responseInfos": [...]
}
```

### 3. searchApis
키워드, 카테고리, HTTP 메서드로 API 검색

```json
{
  "name": "searchApis",
  "arguments": {
    "keyword": "user",      // Optional: 제목, 설명, URL에서 검색
    "category": "User",     // Optional: 카테고리 필터
    "method": "GET"         // Optional: HTTP 메서드 필터
  }
}
```

**Response:**
```json
{
  "summary": "Found 3 API(s) matching keyword 'user' in category 'User' with method 'GET'",
  "count": 3,
  "apis": [...]
}
```

## 성능 최적화

### 캐싱 전략
- **초기화**: 애플리케이션 시작 시 모든 API 정보를 메모리에 캐싱
- **조회**: O(1) - 직접 메모리 접근
- **검색**: O(n) - 인메모리 리스트 스캔 (매우 빠름)
- **CPU 부하**: 제로 (초기화 후)
- **메모리 사용**: O(n) - API 개수에 비례

## 설정 옵션

| 옵션 | 기본값 | 설명 |
|------|--------|------|
| `mcp.enabled` | `true` | MCP 서버 활성화 여부 |
| `mcp.sse-path` | `/mcp/sse` | SSE 엔드포인트 경로 (도메인 제외) |
| `mcp.message-path` | `/mcp/message` | Message 엔드포인트 경로 (도메인 제외) |
| `mcp.server-info.name` | `Spring API Docs MCP Server` | MCP 서버 이름 |
| `mcp.server-info.version` | `1.0.0` | MCP 서버 버전 |

### 경로 설정 예시

**기본 경로 유지:**
```yaml
mcp:
  sse-path: /mcp/sse
  message-path: /mcp/message
```

**API 버전 경로:**
```yaml
mcp:
  sse-path: /api/v1/mcp/sse
  message-path: /api/v1/mcp/message
```

**커스텀 경로:**
```yaml
mcp:
  sse-path: /docs/stream
  message-path: /docs/query
```

> **참고:** 도메인과 포트는 자동으로 현재 HTTP 요청에서 추출되므로 경로만 설정하면 됩니다.

### MCP 서버 비활성화

```yaml
mcp:
  enabled: false
```

## 보안 설정

### CORS 및 인증 예외 처리

MCP SSE 엔드포인트는 **CORS 제한과 인증/인가 검사를 제외**해야 합니다. MCP 클라이언트가 정상적으로 연결할 수 있도록 다음 설정을 추가하세요.

#### Spring Security 사용 시

```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    // MCP 엔드포인트는 인증 제외
                    .requestMatchers("/mcp/**").permitAll()
                    .anyRequest().authenticated()
            }
            .csrf { csrf ->
                // MCP 엔드포인트는 CSRF 보호 제외
                csrf.ignoringRequestMatchers("/mcp/**")
            }

        return http.build()
    }
}
```

#### CORS 설정

```kotlin
@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/mcp/**")
            .allowedOriginPatterns("*")  // MCP 클라이언트 허용
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}
```

> ⚠️ **보안 주의사항**: MCP 엔드포인트는 API 문서 메타데이터만 제공하며, 실제 비즈니스 데이터는 포함하지 않습니다. 하지만 내부 API 구조가 노출될 수 있으므로, 프로덕션 환경에서는 `mcp.enabled=false`로 비활성화하거나 방화벽 규칙을 통해 접근을 제한하는 것을 권장합니다.

## 지원 Annotation

### OpenAPI 3.x
- `@Operation`: 메서드 레벨 API 정보
- `@Tag`: Controller 레벨 카테고리
- `@Parameter`: 파라미터 설명
- `@Schema`: 필드 설명

### Swagger 2.x
- `@ApiOperation`: 메서드 레벨 API 정보
- `@Api`: Controller 레벨 카테고리
- `@ApiParam`: 파라미터 설명
- `@ApiModelProperty`: 필드 설명

## 요구사항

- Java 17+
- Spring Boot 2.7+ or 3.x
- Kotlin 1.9+

## 라이센스

MIT License
