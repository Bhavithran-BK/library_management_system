# Scalable Library Management System — Member 1 Implementation

> **System Design Laboratory — Problem Statement 3**  
> **Team Allocation**: Member 1 — Frontend, Core Backend & Unique ID Generator

---

## 1. Project Overview & Member 1 Scope

This repository contains **Member 1's complete implementation** for the **Scalable Library Management System**. In accordance with the system design plan, Member 1 is responsible for:

1. **Frontend Development**: Modern, responsive, glassmorphic Single-Page Application (SPA) using HTML5, CSS3, and Vanilla JavaScript.
2. **Spring Boot Core Backend**: Clean, modular layered architecture (Controller, Service, Repository, Model, DTO, Config, Security).
3. **Core Library Workflows**:
   - Member registration & stateless authentication (BCrypt + JWT).
   - Book inventory management (CRUD, pagination, category filtering).
   - Concurrency-safe borrowing and returns (atomic inventory decrement & increment).
   - Reservation management (dynamic queue position tracking, cancellation, and auto-fulfillment).
   - Fine management (configurable per-day overdue rates, simulated idempotent payments).
4. **Assigned System Design Concept — Unique ID Generator**:
   - Dedicated 64-bit Distributed Snowflake ID Generator with Base62 compact encoding.
   - Pluggable strategy pattern (`IdGenerator` interface with Snowflake and TSID implementations).
   - Collision-resistant generation with database uniqueness enforcement and retry mechanism.
   - Built-in multi-threaded concurrency benchmark suite and live interactive playground.
5. **Shared Integration Interfaces**:
   - Stubs and agreed API contracts for Member 2 (Search/Autocomplete), Member 3 (Sharding/Distribution), and Member 4 (Rate Limiter/URL Shortener).

---

## 2. Technology Stack

| Layer | Technology | Details |
| :--- | :--- | :--- |
| **Frontend** | HTML5, CSS3, Modern JavaScript (ES6+) | Vanilla CSS design system, glassmorphism, responsive grid, zero external runtime UI dependencies |
| **Backend** | Java 17+ / Spring Boot 3.3.4 | Spring Web, Spring Data MongoDB, Spring Security, Validation |
| **Security** | Spring Security & JJWT (0.12.6) | BCrypt password hashing, stateless JWT Bearer token authentication, Role-Based Access Control (`MEMBER`, `ADMIN`) |
| **Database** | MongoDB | Document storage, atomic operations (`findAndModify`), unique indexes |
| **ID Generator** | Distributed Snowflake (Base62) | 41-bit custom epoch timestamp + 10-bit datacenter/worker ID + 12-bit sequence counter |
| **Build & Test** | Maven 3.9+ & JUnit 5 | Automated unit tests, concurrency tests, and Postman API collection |

---

## 3. Architecture & Project Structure

```text
c:/Users/abdie/Downloads/SDL/
├── pom.xml                                      # Maven configuration
├── docker-compose.yml                           # MongoDB & Redis container setup
├── .env.example                                 # Template for environment variables
├── postman/
│   └── Library_Management_System_Member1.postman_collection.json # Ready-to-import Postman collection
├── src/
│   ├── main/
│   │   ├── java/com/library/
│   │   │   ├── LibraryApplication.java          # Spring Boot main entrypoint
│   │   │   ├── config/                          # Security, MongoDB, DataInitializer
│   │   │   ├── controller/                      # REST API Controllers & Integration Stubs
│   │   │   ├── dto/                             # Request/Response DTOs & ApiResponse wrapper
│   │   │   ├── exception/                       # Custom exceptions & GlobalExceptionHandler
│   │   │   ├── idgenerator/                     # Snowflake & TSID ID Generation Engine
│   │   │   ├── model/                           # MongoDB Entities (Book, Member, Loan, Reservation, Fine)
│   │   │   ├── repository/                      # Spring Data MongoDB Repositories
│   │   │   ├── security/                        # JwtTokenProvider & JwtAuthenticationFilter
│   │   │   └── service/                         # Business Services & Benchmark Runner
│   │   └── resources/
│   │       ├── application.properties           # Server, DB, JWT, and Fine rates configuration
│   │       └── static/                          # Modern Frontend SPA
│   │           ├── index.html                   # 10 Views + Live Benchmark UI
│   │           ├── css/style.css                # Glassmorphism, animations, responsive layout
│   │           └── js/                          # API client, Auth, Books, Member, Admin, Benchmark
│   └── test/
│       └── java/com/library/
│           ├── idgenerator/IdGeneratorBenchmarkTest.java # 20,000 ID multi-threaded collision test
│           └── service/LoanConcurrencyTest.java          # Concurrent borrow race condition test
```

---

## 4. Member 1 System Design Concept: Unique ID Generator

### 4.1 Design & Bit Allocation

The system implements a **64-bit Distributed Snowflake Algorithm** adapted for high-throughput distributed environments:

```text
+---------------------------------------------------------------------------------------+
| 1 bit (0) |  41 bits (Timestamp in ms)  |  5 bits (Datacenter)  |  5 bits (Worker)  |  12 bits (Sequence) |
+---------------------------------------------------------------------------------------+
```

- **1 Unused Sign Bit**: Always `0` to guarantee positive 64-bit integers.
- **41 Bits Timestamp**: Milliseconds elapsed since custom epoch (`2024-01-01 00:00:00 UTC`). Supports **~69 years** of operation.
- **5 Bits Datacenter ID**: Supports up to 32 datacenters.
- **5 Bits Worker ID**: Supports up to 32 machine instances per datacenter (1,024 independent distributed nodes).
- **12 Bits Sequence Counter**: Allows **4,096 unique IDs per millisecond per node** (> 4.09 million IDs/sec/node).
- **Base62 Alphanumeric Encoding**: Transforms raw 64-bit numbers into compact, URL-safe identifiers (e.g., `1Yt8B7k`).
- **Domain Prefixes**:
  - `BK-` for Books
  - `MEM-` for Members
  - `LN-` for Loan transactions
  - `RES-` for Reservations
  - `TXN-` for Fine/Payment transactions

### 4.2 Pluggability & Strategy Pattern

The `IdGeneratorService` decouples entity persistence from concrete algorithms via the `IdGenerator` interface. The team can switch between:
- `SnowflakeIdGenerator` (Distributed Snowflake Base62)
- `TsidIdGenerator` (Time-Sorted Identifier Base32)

Configurable via `app.id-generator.strategy` in `application.properties`.

### 4.3 Persistence Layer Uniqueness & Collision Handling

- All entity ID fields (`bookId`, `memberId`, `loanId`, `reservationId`, `transactionId`) have `@Indexed(unique = true)` enforced at the MongoDB layer.
- Persistence services implement an automatic 3-attempt retry loop upon catching `DuplicateKeyException`.

---

## 5. Concurrency Safety: Atomic Inventory Control

To satisfy the critical requirement that **two concurrent requests cannot borrow the final copy twice**:

- The system uses MongoDB's atomic `findAndModify` operation with an availability constraint:
  ```java
  Query query = new Query(Criteria.where("bookId").is(bookId).and("availableCopies").gt(0));
  Update update = new Update().inc("availableCopies", -1).set("updatedAt", Instant.now());
  Book updated = mongoTemplate.findAndModify(query, update, options, Book.class);
  ```
- Because MongoDB executes single-document operations atomically, if 10 or 100 concurrent requests compete for the final copy (`availableCopies == 1`), exactly **one** thread will match `availableCopies > 0` and decrement it to `0`. All other threads receive `null` and throw a `409 Conflict` out-of-stock exception.
- Verified by automated test: `LoanConcurrencyTest.java`.

---

## 6. API Contract Reference

### Authentication & Members
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Register new member | Public |
| `POST` | `/api/auth/login` | Authenticate user & get JWT | Public |
| `GET` | `/api/auth/me` | Current user profile | Authenticated |
| `GET` | `/api/admin/members` | List all registered members | Admin |

### Book Catalogue
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/books` | List books (pagination & category filter) | Public |
| `GET` | `/api/books/{bookId}` | View book details | Public |
| `POST` | `/api/books` | Add new book | Admin |
| `PUT` | `/api/books/{bookId}` | Update book details | Admin |
| `DELETE` | `/api/books/{bookId}` | Delete book (safe check for active loans) | Admin |

### Borrowing & Returns
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/loans` | Borrow a book (atomic stock decrement) | Member / Admin |
| `POST` | `/api/loans/{loanId}/return` | Return a book (stock increment + fine check) | Member / Admin |
| `GET` | `/api/loans` | List loans (own loans or all for admin) | Authenticated |
| `GET` | `/api/loans/active` | List all active loans | Authenticated |

### Reservations
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/reservations` | Reserve unavailable book | Member / Admin |
| `GET` | `/api/members/{memberId}/reservations` | List member reservations | Member / Admin |
| `DELETE` | `/api/reservations/{reservationId}` | Cancel reservation & reorder queue | Member / Admin |
| `GET` | `/api/reservations` | List all reservations | Admin |

### Fine Management & Simulated Payments
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/members/{memberId}/fines` | Get member fines & active accrued fines | Member / Admin |
| `POST` | `/api/fines/{id}/pay` | Settle fine via simulated payment | Member / Admin |
| `GET` | `/api/fines` | List all fine transactions | Admin |

### Unique ID Generator & Dashboard Stats
| Method | Endpoint | Description | Access |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/id-generator/benchmark` | Run multi-threaded concurrency benchmark | Public |
| `GET` | `/api/id-generator/sample` | Generate sample IDs across all prefixes | Public |
| `GET` | `/api/stats/dashboard` | Dashboard summary metrics | Public |

### Integration Stubs for Members 2, 3 & 4
| Method | Endpoint | Target Concept | Owner |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/search?q=...` | Trie Autocomplete & Redis Search | Member 2 |
| `GET` | `/api/search/suggest?prefix=...` | Prefix suggestions | Member 2 |
| `GET` | `/api/admin/distribution` | MongoDB Shard & Consistent Hash Ring | Member 3 |
| `POST` | `/api/short-links` | Book URL Shortener | Member 4 |
| `GET` | `/s/{code}` | Resolve & redirect short link | Member 4 |
| `GET` | `/api/admin/rate-limit/stats` | Token Bucket Rate Limiter metrics | Member 4 |

---

## 7. How to Run the Application

### Prerequisites
- **Java 17+** (JDK 17 or higher)
- **Maven 3.8+**
- **MongoDB** running on `localhost:27017` (or Docker)

### Option A: Running with Local MongoDB
1. Ensure MongoDB service is running:
   ```powershell
   Get-Service MongoDB
   ```
2. Build and run the Spring Boot application:
   ```powershell
   mvn spring-boot:run
   ```
3. Open your browser and navigate to:
   ```text
   http://localhost:8080/
   ```

### Option B: Running with Docker Compose
If you prefer running MongoDB and Redis in Docker:
```powershell
docker compose up -d
mvn spring-boot:run
```

---

## 8. Default Seed Accounts for Testing

On startup, `DataInitializer` automatically provisions demo accounts and catalogue books:

| Email | Password | Role | Description |
| :--- | :--- | :--- | :--- |
| `admin@library.com` | `Admin@123` | **ADMIN** | Full management rights (Book CRUD, view all loans, process returns) |
| `alice@example.com` | `Alice@123` | **MEMBER** | Has 1 active loan and 1 overdue loan ($30.00 fine) to test returns and payments |
| `bob@example.com` | `Bob@123` | **MEMBER** | Has an active reservation (#1 in queue) on an out-of-stock book |

---

## 9. Automated Test Suite Execution

Run the complete Maven test suite:
```powershell
mvn clean test
```

### Key Tests Included:
1. `com.library.idgenerator.IdGeneratorBenchmarkTest`:
   - Spawns 16 concurrent threads generating 20,000 IDs simultaneously.
   - Asserts 100% uniqueness (`collisions == 0`) and measures throughput (typically 1,000,000+ IDs/sec in-memory).
2. `com.library.service.LoanConcurrencyTest`:
   - Configures a book with exactly 1 available copy.
   - Launches 10 concurrent threads simultaneously requesting to borrow it.
   - Asserts exactly 1 success, 9 rejections, and inventory remains strictly 0 (never negative).

---

## 10. Suggested Live Demonstration Sequence

Follow this step-by-step sequence during project evaluation:

1. **Dashboard Overview**:
   - Open `http://localhost:8080/`.
   - Observe live dashboard metrics: Total Titles, Available Books, Active Loans, Pending Fines.
2. **Unique ID Generator Live Demonstration**:
   - Click **Unique ID Generator** in the sidebar.
   - Click **Run Concurrent Benchmark** (generates 10,000 IDs across 8 threads).
   - Show the result: Throughput rate, Latency, and **0 Collisions**.
   - Click the playground buttons to show live generation of `BK-`, `MEM-`, `LN-`, `RES-`, and `TXN-` formatted IDs.
3. **Book Catalogue & Stock Availability**:
   - Click **Book Catalogue**.
   - Filter by categories (System Architecture, Databases, DevOps).
   - Type in the search box to see real-time book filtering.
   - Click on *Clean Code* to view details and location.
4. **Member Workflow (Borrowing & Returning)**:
   - Click **Quick Demo Accounts** on Dashboard and click **Login as Alice**.
   - Notice the sidebar updates with Alice's name and role badge.
   - Click **My Library & Fines**:
     - See Alice's active loans.
     - Click **Return Book** on one of the loans: confirms return, updates loan status to RETURNED, and increments catalogue stock.
5. **Overdue Fine Calculation & Simulated Payment**:
   - On Alice's portal, see the overdue fine of $30.00.
   - Click **Pay Fine (Simulated)**.
   - Confirm payment: status updates to **PAID** and generates a transaction receipt reference (`SIM-PAY-...`).
6. **Reservation Queue Demonstration**:
   - Log in as **Bob** (`bob@example.com`).
   - Open **My Library & Fines** to see Bob's active reservation for *System Design Interview* (Queue Position `#1`).
   - Open catalogue and observe that out-of-stock books show **Reserve Book (Place in Queue)** instead of Borrow.
7. **Staff Administration (Admin Dashboard)**:
   - Log in as **Admin** (`admin@library.com`).
   - Click **Admin Dashboard** in sidebar:
     - Click **+ Add New Book**: enter title, author, ISBN, copies, and location. Form submits, generates a unique `BK-...` ID, and immediately shows in the catalogue.
     - View all system loans and test manual staff return processing.
     - Inspect the registered members table.

---

## 11. Member 2 & Member 3 Integrated Modules

### Member 2: Search, Prefix Autocomplete & Controlled Crawler
- **Redis Cache-Aside**: High-performance caching for individual book records (ook:{id}) and search queries (search:{q}:{page}:{size}:{avail}). Automatically invalidated on catalogue updates.
- **Trie In-Memory Autocomplete**: Custom Trie data structure indexing all book titles in the catalogue, serving sub-millisecond prefix suggestions (GET /api/search/suggest?prefix=...).
- **Controlled Web Crawler**: Compliant, multi-threaded catalogue crawler (CrawlerService) with politeness delays, domain whitelisting, depth limits, and HTML parsing via Jsoup. Accessible under the **Book Discovery & Crawler** view in the web UI.

### Member 3: Distributed Storage & Consistent Hashing
- **MongoDB Sharding Infrastructure**:
  - docker-compose-sharding.yml deploying a full sharded cluster: Config Server (27019), Shard 1 (27018), Shard 2 (27028), and Mongos Query Router (27017).
  - init-sharding.js & setup-sharding.ps1 automating replica set initiation, shard registration, database sharding, and hashed shard keys ({  bookId: hashed } and { memberId: hashed }).
- **Consistent Hash Router**:
  - ConsistentHashRouter.java with 150 virtual nodes per physical storage node using 32-bit MurmurHash3.
  - Dynamic logical node addition and removal with real-time redistribution metrics.
  - Scale rebalancing simulator evaluating keys migrated against theoretical 1/(N+1) for additions and 1/N for removals.
  - Interactive UI dashboard under the **Distributed Storage & Sharding** view.
  - REST endpoints under /api/admin/distribution/**.

---

## 12. Member 4: Rate Limiting, URL Shortener & Evaluation (COMPLETE)

Member 4 has completed the implementation and integration of the distributed rate limiter, Base62 URL shortener, and comprehensive system evaluation test plans:

### 12.1 Distributed Token Bucket Rate Limiter
- **Algorithm:** Atomic Token Bucket implemented via Redis Lua script (`src/main/resources/scripts/rate_limiter.lua`).
- **Capacity ($C$):** 100 tokens (configurable via `app.rate-limit.capacity`).
- **Refill Rate ($r$):** 10 tokens / sec (configurable via `app.rate-limit.refill-rate-per-sec`).
- **Client Identity:** Tracks per-user authenticated IDs (`rate:user:{username}`) or client IPs (`rate:ip:{ip}`).
- **HTTP Headers:** Standard RFC headers:
  - `X-RateLimit-Limit: 100`
  - `X-RateLimit-Remaining: <remaining>`
  - `Retry-After: <seconds>` (on HTTP 429)
- **High-Availability Resilience:** Local in-memory token bucket fallback automatically activates if Redis experiences network latency or disconnection.
- **REST Diagnostics:**
  - `GET /api/admin/rate-limit/stats` — Real-time metrics, active keys, and rejection rates.
  - `POST /api/admin/rate-limit/reset` — Atomic key flush and counter reset.
  - `POST /api/admin/rate-limit/simulate` — Dynamic burst traffic testing directly from UI or CLI.

### 12.2 Base62 Book URL Shortener
- **Algorithm:** 64-bit integer to Base62 alphanumeric conversion (`0-9a-zA-Z`), powered by an atomic Redis counter (`INCR url_shortener:counter`) with cryptographic SHA-256 fallback.
- **Dual-Tier Persistence:**
  - **Tier 1 (Redis Cache):** `shortlink:{code} -> targetUrl` with 24-hour TTL for sub-millisecond 302 redirects.
  - **Tier 2 (MongoDB):** Durable document entity `ShortLink` in `short_links` collection tracking `clickCount`, `createdAt`, and `lastAccessedAt`.
- **REST Endpoints:**
  - `POST /api/short-links` — Creates shareable short code for book or catalogue path.
  - `GET /s/{code}` — Resolves code and returns HTTP 302 Found redirect with asynchronous click analytics recording.
  - `GET /api/short-links/{code}/analytics` — Detailed click stats and Redis cache status.
  - `GET /api/short-links` — List all active short links.

### 12.3 System Evaluation & Performance Stress Testing (Workstream 3)
Automated test plans are provided for both **Apache JMeter** and **Postman Runner**:
- **Postman Collection:** `postman/Library_Management_System_Member4_Evaluation.postman_collection.json`
- **JMeter Test Plan:** `jmeter/Member4_System_Evaluation_StressTest.jmx`

| Test Scenario | Concurrency (Threads) | Target Endpoints | Observed / Expected Outcome |
| :--- | :--- | :--- | :--- |
| **Scenario 1: Low Load (Baseline)** | 10 concurrent threads, 100 requests | `GET /api/search?q=clean`, `GET /s/{code}` | p95 latency < 15ms, 100% cache hits after warm-up, 0 errors. |
| **Scenario 2: Medium Load (Sustained)** | 50 concurrent threads, 1,000 requests | `GET /api/search/suggest?prefix=cle`, `GET /api/books`, `POST /api/short-links` | Trie autocomplete latency < 5ms, Redis hit ratio > 80%, zero 500 errors. |
| **Scenario 3: High Load (Rate Limit Stress)** | 150 concurrent threads, 3,000 burst requests | `GET /api/search?q=clean`, `POST /api/loans`, `POST /api/admin/rate-limit/simulate` | First 100 requests succeed (HTTP 200); subsequent requests return HTTP 429 Too Many Requests; DB connections remain healthy with 0 crashes. |

