# Member 4 Implementation & Integration Guide: Rate Limiting, URL Shortener & Evaluation

> **Project:** Distributed Library Management System  
> **Course / Lab:** System Design Lab (SDL)  
> **Target Engineer:** Member 4  
> **Status:** Upstream Integration Complete (Member 1, Member 2, Member 3 are operational and verified)  
> **Date:** October 2026

---

## 1. Executive Summary & Context

Members 1, 2, and 3 have successfully implemented, tested, and integrated the core distributed library platform into the `SDL` directory:

| Member | Domain & Responsibilities | Integration Status |
| :--- | :--- | :--- |
| **Member 1** | MongoDB Data Layer, Snowflake ID Generator, Loan Concurrency & Optimistic Locking, Business Rules | **COMPLETE** (Passing in CI/CD test suite) |
| **Member 2** | Redis Cache-Aside Engine, Controlled Web Crawler, Trie In-Memory Autocomplete, Cache Invalidation | **COMPLETE** (Verified with live Redis cluster & tests) |
| **Member 3** | MongoDB Sharding Infrastructure (`docker-compose-sharding.yml`, `init-sharding.js`), Virtual Node Consistent Hash Ring (`ConsistentHashRouter`), Rebalancing Simulation, Admin UI & REST endpoints (`/api/admin/distribution/**`) | **COMPLETE** (Fully integrated in UI & REST API) |
| **Member 4** | **Token Bucket Rate Limiter (Redis Lua), URL Shortener (Base62), Apache JMeter / Postman Stress Tests, System Evaluation & Metrics** | **READY TO IMPLEMENT** |

Pre-configured integration points, security permits, and stub controllers have been put in place to ensure seamless merging without breaking existing endpoints.

---

## 2. Pre-Configured Integration Points Prepared for Member 4

The following files and contracts are already configured in the codebase for you:

### 2.1 Stub Controller Location
- File: [Member4ShortLinkStubController.java](file:///c:/Users/bhavi/BHAVI/sem%20V/system_design_lab/project_library_management_system/SDL/src/main/java/com/library/controller/stubs/Member4ShortLinkStubController.java)
- **Action for Member 4:** You can either enhance this controller or delete this stub file and replace it with your dedicated `ShortLinkController` and `RateLimitController`.

### 2.2 Pre-Configured Endpoints & Paths
1. `POST /api/short-links` &mdash; Creates a short link for books or catalogue paths.
2. `GET /s/{code}` &mdash; Resolves and redirects (HTTP 302) to the target URL.
3. `GET /api/admin/rate-limit/stats` &mdash; Live diagnostics and metrics for rate limiting.

### 2.3 Spring Security Whitelist
In [SecurityConfig.java](file:///c:/Users/bhavi/BHAVI/sem%20V/system_design_lab/project_library_management_system/SDL/src/main/java/com/library/config/SecurityConfig.java), the following paths are already permitted without authentication so evaluation tools (JMeter, Postman, browser) can test them directly:
```java
.requestMatchers("/s/**", "/api/short-links/**", "/api/admin/rate-limit/**").permitAll()
```

### 2.4 Infrastructure Connectivity
- **MongoDB:** Connected at `mongodb://127.0.0.1:27017/library_db` (or `mongodb://127.0.0.1:27017` via mongos router in sharded cluster mode).
- **Redis:** Connected at `127.0.0.1:6380` (configured in `application.properties` via `spring.data.redis.port=${REDIS_PORT:6380}`).

---

## 3. Detailed Work Breakdown for Member 4

### Workstream 1: Distributed Token Bucket Rate Limiter (Redis-Backed)

#### 1. Objective
Protect the system against denial-of-service, abusive crawler spikes, and resource starvation by enforcing per-client rate limits.

#### 2. Specifications
- **Algorithm:** Token Bucket Algorithm.
- **Bucket Capacity ($C$):** 100 tokens (configurable in `application.properties`).
- **Refill Rate ($r$):** 10 tokens / second (configurable in `application.properties`).
- **Storage:** Redis (using atomic Lua scripts to avoid race conditions between distributed application instances).
- **Client Identifier:** Client IP address (`X-Forwarded-For` or `HttpServletRequest.getRemoteAddr()`) or authenticated user ID.

#### 3. Recommended Redis Lua Script (`rate_limiter.lua`)
Place this script in `src/main/resources/scripts/rate_limiter.lua` or execute it directly via `StringRedisTemplate.execute(RedisScript, ...)`:

```lua
-- KEYS[1]: Rate limit key (e.g., rate:ip:127.0.0.1)
-- ARGV[1]: Bucket capacity (e.g., 100)
-- ARGV[2]: Refill rate per millisecond (e.g., 0.01 for 10 tokens/sec)
-- ARGV[3]: Current timestamp in milliseconds
-- ARGV[4]: Requested tokens (usually 1)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local data = redis.call("HMGET", key, "tokens", "last_updated")
local tokens = tonumber(data[1])
local last_updated = tonumber(data[2])

if tokens == nil then
    tokens = capacity
    last_updated = now
else
    local elapsed = math.max(0, now - last_updated)
    tokens = math.min(capacity, tokens + elapsed * refill_rate)
    last_updated = now
end

if tokens >= requested then
    tokens = tokens - requested
    redis.call("HMSET", key, "tokens", tokens, "last_updated", last_updated)
    redis.call("PEXPIRE", key, math.ceil(capacity / refill_rate))
    return {1, math.floor(tokens), 0} -- {allowed (1=true), remaining_tokens, retry_after_sec}
else
    local missing = requested - tokens
    local retry_after = math.ceil(missing / (refill_rate * 1000))
    redis.call("HMSET", key, "tokens", tokens, "last_updated", last_updated)
    redis.call("PEXPIRE", key, math.ceil(capacity / refill_rate))
    return {0, math.floor(tokens), retry_after} -- {allowed (0=false), remaining_tokens, retry_after_sec}
end
```

#### 4. Interceptor & Response Headers
Implement a Spring `HandlerInterceptor` (e.g. `RateLimitInterceptor`):
- Inject `RateLimiterService`.
- If allowed:
  - Add response header: `X-RateLimit-Limit: 100`
  - Add response header: `X-RateLimit-Remaining: <remaining>`
  - Return `true` to continue down the handler chain.
- If rejected:
  - Set HTTP Status: `429 Too Many Requests`.
  - Add response header: `X-RateLimit-Limit: 100`
  - Add response header: `X-RateLimit-Remaining: 0`
  - Add response header: `Retry-After: <retry_after_seconds>`
  - Write JSON response body:
    ```json
    {
      "success": false,
      "message": "Too Many Requests: Rate limit exceeded. Please retry after specified interval.",
      "data": {
        "retryAfterSeconds": 3
      },
      "timestamp": "2026-10-22T01:30:00Z"
    }
    ```
  - Return `false` to abort request processing.

#### 5. Configuration Properties to Add in `application.properties`
```properties
# Member 4: Token Bucket Rate Limiter
app.rate-limit.enabled=${RATE_LIMIT_ENABLED:true}
app.rate-limit.capacity=${RATE_LIMIT_CAPACITY:100}
app.rate-limit.refill-rate-per-sec=${RATE_LIMIT_REFILL_RATE:10}
app.rate-limit.protected-paths=/api/books/**,/api/loans/**,/api/search/**,/api/short-links/**
```

---

### Workstream 2: Book URL Shortener (Base62)

#### 1. Objective
Generate short, shareable URLs for catalogue items and books (e.g., `http://localhost:8080/s/a3x9B`) that redirect to book details, with click analytics and Redis cache acceleration.

#### 2. Architecture & Algorithms
1. **Base62 Character Set:**
   ```java
   private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
   ```
2. **Short Code Generation:**
   - Option A: Hash the target URI (or Snowflake Book ID) with Murmur3 or MD5 and encode into Base62.
   - Option B: Maintain an atomic Redis counter (`INCR url_shortener:counter`) and convert the 64-bit integer into Base62.
3. **Storage Strategy (Dual-Tier Persistence):**
   - **Tier 1 (Redis Cache):** Store `shortlink:{code} -> targetUrl` with a TTL (e.g., 24 hours) for sub-millisecond redirects.
   - **Tier 2 (MongoDB):** Store entity `ShortLink` in collection `short_links` for durable metadata and analytics.

#### 3. Recommended Document Entity (`ShortLink.java`)
```java
package com.library.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "short_links")
public class ShortLink {
    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String originalUrl;
    private String bookId;
    private long clickCount;
    private Instant createdAt;
    private Instant lastAccessedAt;

    // Constructors, Getters, Setters
}
```

#### 4. REST Endpoints to Implement
- `POST /api/short-links`
  - Body: `{ "targetUrl": "http://localhost:8080/#catalogue?id=BK-12345", "bookId": "BK-12345" }`
  - Returns: `{ "code": "bK9z1", "shortUrl": "/s/bK9z1", "targetUrl": "..." }`
- `GET /s/{code}`
  - Returns HTTP 302 Found with `Location: <targetUrl>`.
  - Asynchronously increments `clickCount` and updates `lastAccessedAt`.
- `GET /api/short-links/{code}/analytics`
  - Returns click count, creation time, and last access time.

---

### Workstream 3: System Evaluation & Performance Testing

#### 1. Objective
Run comprehensive stress and load tests to evaluate the performance of all 4 members' components under real-world traffic profiles.

#### 2. Test Scenarios (Apache JMeter / Postman Runner)
Implement 3 test plans (either in JMeter `.jmx` or Postman Runner collection):

| Test Scenario | Concurrency (Threads) | Target Endpoints | Expected Outcome |
| :--- | :--- | :--- | :--- |
| **Scenario 1: Low Load (Baseline)** | 10 concurrent threads, 100 total requests | `GET /api/search?q=clean`, `GET /s/{code}` | p95 latency < 15ms, 100% cache hits after warm-up, 0 errors. |
| **Scenario 2: Medium Load (Sustained)** | 50 concurrent threads, 1,000 total requests | `GET /api/search/suggest?prefix=cle`, `GET /api/books`, `POST /api/short-links` | Trie autocomplete latency < 5ms, Redis hit ratio > 80%, zero 500 errors. |
| **Scenario 3: High Load (Rate Limit Stress)** | 150 concurrent threads, 3,000 requests in bursts | `GET /api/search?q=clean`, `POST /api/loans` | First 100 requests succeed; subsequent requests return HTTP 429 Too Many Requests; DB connections remain healthy with 0 crashes. |

#### 3. Metrics to Collect & Report
In your final report or presentation:
- **Throughput (Requests / Second):** Peak RPS sustained by the server.
- **Latency Distribution:** Min, Mean, Median (p50), p90, p95, and p99 response times.
- **Cache Hit vs Miss Ratio:** Gathered directly from `GET /api/cache/stats`.
- **Throttling Accuracy:** Verify that HTTP 429 response count strictly matches requests exceeding $(C + r \times t)$.
- **Consistent Hash Ring Distribution:** Uniformity and rebalancing metrics from `GET /api/admin/distribution`.

---

## 4. Step-by-Step Implementation Sequence

1. **Clean up or Replace the Stub Controller:**
   - Inspect [Member4ShortLinkStubController.java](file:///c:/Users/bhavi/BHAVI/sem%20V/system_design_lab/project_library_management_system/SDL/src/main/java/com/library/controller/stubs/Member4ShortLinkStubController.java).
   - Implement your production service classes (`RateLimiterService`, `Base62Service`, `ShortLinkService`).
   - Replace the in-memory map in the stub with MongoDB repository and Redis cache calls.

2. **Register the WebMvc Interceptor:**
   - Create `WebMvcConfig.java` implementing `WebMvcConfigurer`.
   - Register your `RateLimitInterceptor` with `registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**")`.
   - Exclude static assets (`/index.html`, `/css/**`, `/js/**`) and admin inspection endpoints (`/api/admin/**`).

3. **Verify with Existing Tests:**
   - Run `mvn test` to ensure existing Member 1, 2, and 3 tests continue to pass with 0 regressions.
   - Add new JUnit tests:
     - `RateLimiterTest.java` (testing token depletion, refill, and 429 status).
     - `Base62ShortLinkTest.java` (testing encode/decode and redirect accuracy).

4. **Run the Demonstration UI:**
   - Start the server using: `mvn spring-boot:run`.
   - Open browser at `http://localhost:8080/`.
   - Verify all tabs work:
     - Books Catalogue (Member 1)
     - Members & Loans (Member 1)
     - Book Discovery, Search & Crawler (Member 2)
     - Distributed Storage & Consistent Hashing (Member 3)
     - Short Link & Rate Limiting (Member 4)

---

## 5. Summary of Architecture Dependencies

```
+-----------------------------------------------------------------------------------+
|                              L7 APPLICATION ROUTER                                |
|  - Consistent Hash Router (Member 3: Virtual Node Ring, 150 vnodes/node)          |
|  - Token Bucket Rate Limiter (Member 4: Atomic Redis Lua Script, Capacity 100)    |
|  - Base62 URL Shortener (Member 4: /s/{code} -> Redis Cache -> MongoDB)           |
+-----------------------------------------+-----------------------------------------+
                                          |
                +-------------------------+-------------------------+
                |                                                   |
                v                                                   v
+-------------------------------+                   +-------------------------------+
|       REDIS MEMORY TIER       |                   |      MONGODB SHARD CLUSTER    |
|  - Cache-Aside (Member 2)     |                   |  - Mongos Router (:27017)     |
|  - Trie Prefix Cache (Mem 2)  |                   |  - Config Server (:27019)     |
|  - Rate Limit Tokens (Mem 4)  |                   |  - Shard 1 (RS: :27018)       |
|  - Short URLs Cache (Mem 4)   |                   |  - Shard 2 (RS: :27028)       |
|  - Port: 6380                 |                   |  - Hashed Shard Keys (Mem 3)  |
+-------------------------------+                   +-------------------------------+
```

---

*End of Member 4 Instructions. Happy coding!*
