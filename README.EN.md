# Spring Swagger MCP Server

English | [한국어](README.md)

A Spring Boot library that provides API documentation via MCP (Model Context Protocol) server based on Swagger/OpenAPI annotations.

## Features

- ✅ **Swagger 2.x & OpenAPI 3.x Support**: Compatible with both versions
- ✅ **Automatic SSE Connection**: MCP server activation just by adding the library
- ✅ **Memory Caching**: Ultra-fast API information delivery with zero server load
- ✅ **Java 17+ Compatible**: Supports Spring Boot 2.7+ and 3.x
- ✅ **Search Functionality**: Search APIs by keyword, category, and HTTP method

## Quick Start

### 1. Add Dependency

```gradle
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.hyuck0221:spring-api-docs-mcp:0.1.0")
}
```

### 2. Add Swagger Annotations

```kotlin
@RestController
@Tag(name = "User")  // Controller-level category
class UserController {

    @Operation(
        summary = "Get User",
        description = "Retrieve user information by ID",
        tags = ["User"]  // Method-level category (higher priority)
    )
    @GetMapping("/users/{id}")
    fun getUser(
        @Parameter(description = "User ID")
        @PathVariable id: Long
    ): User {
        // ...
    }

    @Operation(summary = "Search Users")
    @GetMapping("/users")
    fun searchUsers(
        @RequestParam name: String,
        @RequestParam(required = false) age: Int?
    ): List<User> {
        // ...
    }
}

data class User(
    @Schema(description = "User ID")
    val id: Long,

    @Schema(description = "User name")
    val name: String
)
```

### 3. Configuration (application.yml)

```yaml
mcp:
  # Enable/disable MCP server (default: true)
  enabled: true

  # SSE endpoint path configuration
  sse-path: /mcp/sse

  # Message endpoint path configuration
  message-path: /mcp/message

  # MCP server information
  server-info:
    name: My API Docs Server
    version: 1.0.0
```

### 4. Run Application

```bash
./gradlew bootRun
```

**MCP Endpoints:**
- SSE: `http://localhost:8080/mcp/sse` (path can be changed in configuration)
- Message: `http://localhost:8080/mcp/message` (path can be changed in configuration)

> **Note:** Domain and port are automatically extracted from the current request; only the path can be configured in the settings file.

## MCP Tools

### 1. getApiCount
Get API count (filterable by category)

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
Get detailed information about a specific API endpoint

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
  "title": "Get User",
  "description": "Retrieve user information by ID",
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
Search APIs by keyword, category, or HTTP method

```json
{
  "name": "searchApis",
  "arguments": {
    "keyword": "user",      // Optional: searches in title, description, URL
    "category": "User",     // Optional: filter by category
    "method": "GET"         // Optional: filter by HTTP method
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

## Performance Optimization

### Caching Strategy
- **Initialization**: All API information is cached in memory at application startup
- **Lookup**: O(1) - Direct memory access
- **Search**: O(n) - In-memory list scan (very fast)
- **CPU Load**: Zero (after initialization)
- **Memory Usage**: O(n) - Proportional to the number of APIs

## Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `mcp.enabled` | `true` | Enable/disable MCP server |
| `mcp.sse-path` | `/mcp/sse` | SSE endpoint path (excluding domain) |
| `mcp.message-path` | `/mcp/message` | Message endpoint path (excluding domain) |
| `mcp.server-info.name` | `Spring API Docs MCP Server` | MCP server name |
| `mcp.server-info.version` | `1.0.0` | MCP server version |

### Path Configuration Examples

**Keep default paths:**
```yaml
mcp:
  sse-path: /mcp/sse
  message-path: /mcp/message
```

**API version paths:**
```yaml
mcp:
  sse-path: /api/v1/mcp/sse
  message-path: /api/v1/mcp/message
```

**Custom paths:**
```yaml
mcp:
  sse-path: /docs/stream
  message-path: /docs/query
```

> **Note:** Domain and port are automatically extracted from the current HTTP request, so you only need to configure the path.

### Disable MCP Server

```yaml
mcp:
  enabled: false
```

## Security Configuration

### CORS and Authentication Exception Handling

MCP SSE endpoints **must be excluded from CORS restrictions and authentication/authorization checks**. Add the following configuration to allow MCP clients to connect properly.

#### With Spring Security

```kotlin
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    // Exclude MCP endpoints from authentication
                    .requestMatchers("/mcp/**").permitAll()
                    .anyRequest().authenticated()
            }
            .csrf { csrf ->
                // Exclude MCP endpoints from CSRF protection
                csrf.ignoringRequestMatchers("/mcp/**")
            }

        return http.build()
    }
}
```

#### CORS Configuration

```kotlin
@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/mcp/**")
            .allowedOriginPatterns("*")  // Allow MCP clients
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}
```

> ⚠️ **Security Notice**: MCP endpoints only provide API documentation metadata and do not include actual business data. However, since internal API structure may be exposed, it is recommended to disable it with `mcp.enabled=false` in production environments or restrict access through firewall rules.

## Supported Annotations

### OpenAPI 3.x
- `@Operation`: Method-level API information
- `@Tag`: Controller-level category
- `@Parameter`: Parameter description
- `@Schema`: Field description

### Swagger 2.x
- `@ApiOperation`: Method-level API information
- `@Api`: Controller-level category
- `@ApiParam`: Parameter description
- `@ApiModelProperty`: Field description

## Requirements

- Java 17+
- Spring Boot 2.7+ or 3.x
- Kotlin 1.9+

## License

MIT License
