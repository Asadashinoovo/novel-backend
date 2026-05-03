# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Start the application (requires MySQL + Redis configured in application.yaml)
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=NovelApplicationTests

# Package as JAR
./mvnw clean package -DskipTests
```

The app runs on port **8081**. Active profile defaults to `dev`.

## Architecture

This is an online novel reading and writing platform backend built on **Spring Boot 3.5.14 + Java 17 + MyBatis-Plus 3.5.15**.

### Layer convention

```
Controller  →  Service (interface)  →  ServiceImpl  →  Mapper (MyBatis-Plus BaseMapper + custom XML)
```

Custom SQL lives in `src/main/resources/xml/*.xml`. MyBatis-Plus `BaseMapper` covers basic CRUD; complex queries go through hand-written XML `<select>`/`<update>`/`<insert>` blocks.

### Package map (`com.djs.novel`)

| Package | Purpose |
|---|---|
| `controller/` | REST controllers, all returning `Result` DTOs |
| `service/` | Service interfaces (`I*Service`) |
| `service/impl/` | Service implementations; business logic and authorization checks live here |
| `mapper/` | MyBatis-Plus mapper interfaces (`@Mapper`, extend `BaseMapper`) |
| `entity/` | Database entities (map to tables via `@TableName`) |
| `dto/` | Request/response DTOs — `Result` is the unified API response wrapper |
| `vo/` | View objects returned to clients (e.g., `BookVO` joins book + author name + types) |
| `config/` | Spring configuration (`WebConfig` registers auth interceptor) |
| `filter/` | `AuthenticationFilter` — a `HandlerInterceptor` for Bearer token validation |
| `util/` | `UserHolder` — ThreadLocal-based current-user context |

### Database tables

- **`user`** — user accounts (password stored as BCrypt hash)
- **`book_info`** — book metadata (title, cover, description, author_id)
- **`book_chapter`** — chapter content with `sort_order` for ordering
- **`book_type`** — genre/type catalog
- **`book_info_book_type`** — M:N join between books and types

### Authentication flow

1. `POST /api/auth/login` with username+password → BCrypt verification → generates UUID token → stores in Redis (`token:<token>` → user JSON, `user:id:<userId>` → token, TTL 60 min)
2. All `/api/**` requests (except `/api/auth/login`, `/api/home/**`, `/api/chapter/get/**`) go through `AuthenticationFilter`
3. Filter extracts `Bearer <token>` → looks up user from Redis → loads into `UserHolder` (ThreadLocal)
4. Services call `UserHolder.getUser()` to get the current authenticated user for ownership checks

### API routing summary

| Path pattern | Auth required | Purpose |
|---|---|---|
| `POST /api/auth/login` | No | Login |
| `POST /api/auth/logout` | Yes | Logout (clears Redis tokens) |
| `GET /api/home/book` | No | Paginated book listing with keyword search |
| `GET /api/home/book/{id}` | No | Single book detail |
| `GET /api/home/book/{id}/chapters` | No | Chapter list for a book |
| `GET /api/chapter/get/{bookId}/{id}` | No | Chapter content (also increments read count) |
| `GET /api/user/me` | Yes | Current user info |
| `GET /api/bookinfo/**` | Yes | Book CRUD (my books, add, update, delete, types) |
| `POST /api/bookinfo/**` | Yes | Book CRUD |
| `POST /api/chapter/add` | Yes | Add chapter (auto-computes sort_order as MAX+10) |
| `POST /api/chapter/update` | Yes | Update chapter |
| `POST /api/chapter/delete/{bookId}/{id}` | Yes | Delete chapter |

### Authorization pattern

Ownership checks in service implementations follow this pattern:
```java
if (!isOwner(userId, bookIds)) {
    return Result.fail("无权修改此书籍");
}
```
Where `isOwner` queries `book_info` and compares `authorId` against `UserHolder.getUser().getId()`.

### Unified response

All endpoints return `Result` with fields: `success` (Boolean), `errorMsg`, `data`, `total`. Static factories: `Result.ok()`, `Result.ok(data)`, `Result.ok(data, total)`, `Result.fail(errorMsg)`.

## Configuration

- `src/main/resources/application.yaml` — dev database and Redis credentials, MyBatis mapper locations, SQL logging
- Create `src/main/resources/application-prod.yaml` for production (gitignored)
- Database name: `novel`; init with `novel.sql` at repo root (if present)
