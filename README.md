# Novel - 在线小说阅读与创作平台


## 项目结构

```
novel-backend/          # 后端 (Spring Boot 3.5.14 + Java 17)
```

## 技术栈

### 后端
- **框架**: Spring Boot 3.5.14
- **Java 版本**: 17
- **ORM**: MyBatis-Plus 3.5.15
- **数据库**: MySQL
- **缓存**: Redis
- **安全**: Spring Security

## 功能模块


### 后端 API
- **AuthController** - 用户认证 (登录/注册)
- **UserController** - 用户管理
- **BookInfoController** - 书籍信息
- **ChapterController** - 章节管理

## 快速开始


### 后端

```bash
cd novel-backend
# 配置 application.yaml 中的数据库和 Redis 连接
./mvnw spring-boot:run
```

## 数据库配置

后端配置文件: `novel-backend/src/main/resources/application.yaml`

需要配置:
- MySQL 数据库连接
- Redis 连接

## SQL文件
创建novel库，然后运行sql
novel.sql

---

## 架构总览

```
用户 ──→ 前端 (Vue3 + Vite) ──→ Spring Boot API
                                       │
            ┌──────────────────────────┼──────────────────────────┐
            │                          │                          │
    ┌───────▼───────┐          ┌───────▼───────┐          ┌───────▼───────┐
    │   小说平台      │          │   AI 助手      │          │    外部 API    │
    │  (基础 CRUD)   │          │  (RAG 引擎)    │          │               │
    └───────────────┘          └───────────────┘          └───────────────┘
```

### 小说平台（基础功能）
- 用户认证 (Spring Security + Redis Token)
- 书籍/章节管理 (MyBatis-Plus + MySQL)
- 章节阅读 + 书架

### AI 助手（RAG 检索增强生成）

```
用户提问 ──→ ┌─ Redis 缓存 (命中→直接返回) ──────────────────────┐
             │  miss                                                 │
             ├─ 混合检索 (关键词 + 向量双路 → RRF 融合)               │
             ├─ 越章保护 (maxSortOrder 过滤未订阅章节)                │
             ├─ 上下文构建 (当前章 + 检索结果 → 拼接 prompt)           │
             ├─ DeepSeek 生成答案                                     │
             └─ 写缓存 + 返回 (含来源章节引用)                        │
```

**三大核心功能：**

| 功能 | 说明 | 触发时机 |
|------|------|---------|
| 智能问答 | 读者在任意章节向 AI 提问剧情，AI 基于已读章节回答 | 用户主动提问 |
| 前情提要 | 每章自动生成剧情摘要，渐进式策略控制上下文开销 | 章节发布后异步生成 |
| 角色追踪 | 自动抽取角色及其事件，支持角色时间线查询 | 章节发布后异步生成 |

**检索模式：**

| 模式 | 原理 | 适用场景 |
|------|------|---------|
| 关键词 (`fulltext`) | MySQL FULLTEXT + ngram 分词 | 精确匹配 |
| 向量语义 (`vector`) | DashScope text-embedding-v4 + 余弦相似度 | 模糊语义 |
| 混合 (`hybrid`) | 双路 RRF 融合，默认推荐 | 通用场景 |

**章节生命周期自动管理：**
- 发布 → 异步生成摘要、抽取角色、构建知识索引、向量化
- 更新 → 异步重新生成全部 AI 数据
- 删除 → 异步清理四表（summary / event / index / embedding）

**架构设计：**
- 检索/缓存/MQ 三层接口抽象，当前用 MySQL FULLTEXT + Redis + Noop，切换 ES / RocketMQ 改一行配置即可
- 问题 → MD5 → Redis 缓存，24h TTL，高频问题秒级响应

### AI 模块技术栈

| 组件 | 技术 |
|------|------|
| LLM 对话 | DeepSeek (deepseek-v4-pro) |
| 文本向量化 | 阿里云 DashScope text-embedding-v4 (1024维) |
| 检索融合 | RRF (Reciprocal Rank Fusion, k=60) |
| 异步处理 | Spring @Async + ApplicationEvent |
| 缓存 | Redis (24h TTL, 可配置) |

### AI 模块配置

```yaml
novel:
  ai:
    search-mode: hybrid       # fulltext | vector | hybrid
    cache-enabled: true
    cache-ttl-hours: 24

deepseek:
  api:
    key: <your-key>
    chat-model: deepseek-v4-pro

dashscope:
  api:
    key: <your-key>
  embedding:
    model: text-embedding-v4
    dimensions: 1024
```

详细架构文档见: [src/main/java/com/djs/novel/ai/ARCHITECTURE.md](src/main/java/com/djs/novel/ai/ARCHITECTURE.md)

