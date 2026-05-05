# AI 模块架构文档

## 这个模块做什么

小说阅读平台的 AI 助手。读者在任何章节阅读时，可以向 AI 提问剧情相关问题，AI 会基于已读章节内容给出回答。

三个核心能力：
- **AI 问答**：读者提问 → 检索相关章节 → 拼成 prompt → AI 生成答案
- **前情提要**：每章自动生成剧情摘要，帮助读者快速回顾
- **角色追踪**：自动抽取每章出现的角色和事件，支持角色时间线查询

---

## 业务流程

### 流程一：读者提问（核心链路）

```
读者在第二章问"秦羽的父亲是谁"
        │
        ▼
┌─ 1. 查 Redis 缓存 ──────────────────┐
│  问题归一化 → MD5 → 查 Redis         │
│  ┌ 命中 → 直接返回（毫秒级）         │
│  └ 未命中 ↓                         │
└─────────────────────────────────────┘
        │
        ▼
┌─ 2. 混合检索（关键词 + 向量双路）───┐
│                                      │
│  关键词路：MySQL FULLTEXT            │
│    MATCH(title,content) AGAINST      │
│    ('秦羽的父亲是谁')                │
│    → 匹配含"秦羽""父亲"的章节        │
│                                      │
│  向量路：DashScope text-embedding-v4 │
│    "秦羽的父亲是谁" → 1024维向量     │
│    → 与所有章节向量算余弦相似度       │
│    → 语义理解"父亲=秦战天"           │
│                                      │
│  RRF 融合排序 → TOP 3 章节           │
└─────────────────────────────────────┘
        │
        ▼
┌─ 3. 组装上下文 ─────────────────────┐
│  优先包含当前章节（第2章）           │
│  补上检索到的第1章、第3章            │
│  去重、按章节顺序排列、越章保护       │
│  每章截取前800字                     │
└─────────────────────────────────────┘
        │
        ▼
┌─ 4. 构建 Prompt ────────────────────┐
│  System: 你是网文阅读助手...         │
│  User:                               │
│    相关章节内容：                     │
│    [第1章 "星辰之始"]: ...           │
│    [第2章 "星辰大帝的传承"]: ...     │
│    用户问题：秦羽的父亲是谁           │
└─────────────────────────────────────┘
        │
        ▼
┌─ 5. DeepSeek 生成 ──────────────────┐
│  → "根据第1章内容，秦羽的父亲是      │
│      秦战天。"                       │
└─────────────────────────────────────┘
        │
        ▼
┌─ 6. 写缓存 + 返回 ──────────────────┐
│  答案存 Redis（24小时过期）          │
│  返回 ChatResponse {                 │
│    answer: "秦战天",                 │
│    sources: [第1章, 第2章, 第3章]    │
│  }                                   │
└─────────────────────────────────────┘
```

### 流程二：章节发布后的异步处理

```
新章节入库 → 触发 ChapterPublishedEvent
        │
        ▼  @Async 异步线程池（核心2，最大4）
┌─────────────────────────────────────────┐
│                                         │
│  ① 生成前情提要                         │
│     SummaryServiceImpl                  │
│     → 前几章：全文拼接生成              │
│     → 后面章节：上一章摘要 + 最近3章正文 │
│     → 写入 chapter_summary 表           │
│                                         │
│  ② 抽取角色和事件                       │
│     CharacterServiceImpl               │
│     → AI 提取角色名 + 行为              │
│     → 写入 character_info 表            │
│     → 写入 character_event 表           │
│                                         │
│  ③ 构建知识图谱索引                     │
│     ChapterIndexBuilder                │
│     → 从角色事件提取实体                │
│     → 写入 chapter_index 表             │
│                                         │
│  ④ 生成向量                             │
│     EmbeddingServiceImpl               │
│     → DashScope text-embedding-v4      │
│     → 1024维向量 → JSON                │
│     → 写入 chapter_embedding 表         │
│                                         │
└─────────────────────────────────────────┘
```

---

## 代码分层

```
ai/
├── orchestrator/        ← 编排层（串联业务流程）
│   └── AiOrchestratorImpl      核心引擎，链式调用各层
│
├── search/              ← 检索层（三引擎可切换）
│   ├── FulltextSearchEngine    关键词引擎：MySQL FULLTEXT
│   ├── VectorSearchEngine      语义引擎：向量 + 余弦相似度
│   └── HybridSearchEngine      混合引擎：双路 RRF 融合（默认）
│
├── client/              ← 外部 API 客户端
│   ├── DeepSeekClient          LLM 对话生成
│   └── DashScopeEmbeddingClient 文本向量化
│
├── cache/               ← 缓存层
│   └── RedisCacheLayer         问题 → MD5 → Redis
│
├── service/impl/        ← 业务服务
│   ├── SummaryServiceImpl      前情提要生成
│   ├── CharacterServiceImpl    角色抽取 + 搜索
│   └── EmbeddingServiceImpl    向量生成 + 相似度搜索
│
├── knowledge/           ← 知识图谱
│   └── ChapterIndexBuilder     章节实体索引构建
│
├── mq/                  ← 消息队列（预留）
│   └── NoopMqProvider          当前空操作
│
└── config/              ← 配置切换
    ├── SearchModeConfig        检索模式切换
    └── MqModeConfig            MQ 模式切换
```

---

## 检索模式对比

| 模式 | 配置值 | 原理 | 适用场景 |
|------|--------|------|---------|
| 关键词 | `fulltext` | MySQL FULLTEXT + ngram 分词 | 精确匹配，如"第一章" |
| 向量语义 | `vector` | text-embedding-v4 + 余弦相似度 | 模糊语义，如"主角他爸" |
| **混合** | **`hybrid`** | 双路召回 + RRF 融合 | **推荐，当前默认** |

切换方式：改一行配置重启即可。
```yaml
novel.ai.search-mode: hybrid   # fulltext | vector | hybrid
```

---

## 实现状态

### 真实在跑

| 组件 | 状态 | 依赖 |
|------|------|------|
| AiOrchestratorImpl | 全流程编排 | ISearchEngine + ICacheLayer + DeepSeekClient |
| FulltextSearchEngine | MySQL FULLTEXT 关键词检索 | book_chapter 索引 |
| VectorSearchEngine | DashScope v3 向量语义检索 | DashScopeEmbeddingClient |
| HybridSearchEngine | 双路 RRF 融合 | 上面两个引擎 |
| RedisCacheLayer | 问答缓存 | Redis |
| DashScopeEmbeddingClient | 文本→1024维向量 | DashScope API |
| FulltextInitService | 启动自动建索引 | MySQL |
| ChapterIndexBuilder | 实体索引构建 | character_event 表 |
| SummaryServiceImpl | 渐进式前情提要 | DeepSeekClient |
| CharacterServiceImpl | 角色抽取+追踪 | DeepSeekClient |
| EmbeddingServiceImpl | 向量生成+余弦搜索 | DashScopeEmbeddingClient |

### 未来接口（代码已留好，换一行配置就能激活）

| 接口 | 当前 | 将来 |
|------|------|------|
| `ISearchEngine → ELASTICSEARCH` | 降级为 hybrid | Elasticsearch 全文索引 |
| `IMqProvider → ROCKETMQ` | NoopMqProvider 空操作 | RocketMQ 削峰异步 |
| `IMqProvider → RABBITMQ` | NoopMqProvider 空操作 | RabbitMQ 削峰异步 |

---

## 配置参考

```yaml
deepseek:
  api:
    key: <DeepSeek API Key>
    base-url: https://api.deepseek.com
    chat-model: deepseek-v4-pro
    timeout: 120

dashscope:
  api:
    key: <阿里云百炼 API Key>
  embedding:
    model: text-embedding-v4
    dimensions: 1024

novel:
  ai:
    search-mode: hybrid         # fulltext | vector | hybrid
    mq-mode: noop               # noop | rocketmq | rabbitmq
    cache-enabled: true         # 关闭则不走缓存
    cache-ttl-hours: 24         # 缓存过期时间
    async-threshold-ms: 10000   # 超过此时间走 MQ（暂未启用）
```

---

## 涉及的数据表

| 表 | 用途 | 写入时机 |
|---|---|---|
| `book_chapter` | 章节原文 | 章节入库时 |
| `chapter_summary` | 前情提要 | 章节发布后异步生成 |
| `character_info` | 角色信息 | 章节发布后异步抽取 |
| `character_event` | 角色事件 | 章节发布后异步抽取 |
| `chapter_embedding` | 章节向量（JSON） | 章节发布后异步生成 |
| `chapter_index` | 实体索引 | 角色抽取完成后构建 |
| `ai_cache` | 问答缓存（MySQL 备用） | 用户提问后 |
| Redis `ai:cache:*` | 问答缓存（主） | 用户提问后 |

---

## 本地运行

依赖：MySQL + Redis

启动步骤：
1. 执行 `src/main/resources/sql/chapter_index_ddl.sql` 建表（首次）
2. `mvn spring-boot:run`
3. FULLTEXT 索引启动时自动创建
4. 已有章节需调用 `POST /api/ai/admin/reprocess/{bookId}` 生成向量和摘要
