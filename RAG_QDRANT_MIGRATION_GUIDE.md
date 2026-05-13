# RAG 检索改造协作说明：从 MySQL-only 到 MySQL + Qdrant

本文用于团队协作同步。当前项目的 AI 阅读助手 RAG 已从最初的 **MySQL-only 章节级向量检索**，改成 **MySQL 存业务/检索元数据 + Qdrant 存 chunk 向量** 的混合架构。

适用目录：

```text
E:\novle\novel-backend-dev\novel-backend-dev
```

## 1. 改造前是什么样

最开始的 RAG 是 MySQL-only：

- 小说章节正文在 MySQL 的 `book_chapter`。
- AI 向量存在 MySQL 的 `chapter_embedding.embedding`，格式是 JSON 字符串。
- 向量粒度是“每章一条 embedding”。
- 检索时 Java 从 MySQL 读出整本书的向量 JSON，在内存里算余弦相似度。
- 全文检索直接查 `book_chapter.title/content`。
- 混合检索虽然存在，但融合的是“章节级结果”，不是“片段级结果”。

这个方案能跑，但问题是：

- 长章节只用一个向量，语义太粗。
- 命中的可能是章节，但不一定是答案所在片段。
- 向量都塞 MySQL JSON，不适合后续扩展。
- 作者修改章节后，旧 RAG 数据容易残留或重建不完整。

## 2. 现在改成了什么

现在的设计是：

```text
MySQL
  - 书籍、章节、用户等业务数据
  - rag_chunk：chunk 文本、章节归属、sort_order、防越章过滤元数据
  - chapter_ai_state：章节 AI 处理状态和内容 hash
  - chapter_summary / character_info / character_event / chapter_index 等 AI 业务表

Qdrant
  - 每个 rag_chunk 对应一条向量 point
  - point id = rag_chunk.id
  - payload 包含 bookId / chapterId / sortOrder / chunkIndex
```

RAG 查询流程现在是：

```text
用户问题 + 当前章节
-> 当前章节 sortOrder
-> MySQL FULLTEXT 检索 rag_chunk
-> Qdrant 向量检索 chunk vectors
-> 两路结果 RRF 融合
-> Noop reranker 预留接口
-> 只把 sortOrder <= 当前章节的 chunks 放进 prompt
```

核心防越章规则：

```text
bookId = 当前书
sortOrder <= 当前阅读章节 sortOrder
```

这个规则在 Qdrant payload 过滤、MySQL SQL 过滤、进入 prompt 前都有防线。

## 3. 新增/修改的重要文件

### 文档

```text
QDRANT_SETUP.md
RAG_QDRANT_MIGRATION_GUIDE.md
```

### 配置

```text
src/main/resources/application.yaml
```

新增配置：

```yaml
novel:
  ai:
    vector-store: qdrant
    qdrant:
      base-url: http://localhost:6333
      collection-name: novel_rag_chunks
      vector-size: 1024
      distance: Cosine
    chunk:
      target-chars: 600
      overlap-chars: 100
      max-candidates: 40
      final-top-k: 6
    summary:
      rebuild-window: 3
    rerank-enabled: false
```

### MySQL 表结构

```text
src/main/resources/sql/ai_init.sql
src/main/resources/sql/chapter_ai_state_ddl.sql
```

新增/需要确认存在的表：

- `rag_chunk`
- `chapter_ai_state`

### Qdrant 向量库接入

```text
src/main/java/com/djs/novel/ai/vector/RagVectorStore.java
src/main/java/com/djs/novel/ai/vector/RagVectorMatch.java
src/main/java/com/djs/novel/ai/vector/QdrantRagVectorStore.java
```

### RAG chunk 检索

```text
src/main/java/com/djs/novel/ai/chunk/ChapterChunker.java
src/main/java/com/djs/novel/ai/entity/RagChunk.java
src/main/java/com/djs/novel/ai/mapper/RagChunkMapper.java
src/main/resources/xml/RagChunkMapper.xml
src/main/java/com/djs/novel/ai/search/ChunkFulltextSearchEngine.java
src/main/java/com/djs/novel/ai/search/ChunkVectorSearchEngine.java
src/main/java/com/djs/novel/ai/search/ChunkHybridSearchEngine.java
```

### 作者修改章节后的 AI 更新

```text
src/main/java/com/djs/novel/ai/listener/ChapterAiEventListener.java
src/main/java/com/djs/novel/ai/edit/ChapterEditImpactAnalyzer.java
src/main/java/com/djs/novel/ai/entity/ChapterAiState.java
src/main/java/com/djs/novel/ai/mapper/ChapterAiStateMapper.java
```

## 4. 本地/服务器需要安装启动什么

### 4.1 必需服务

本项目后端现在至少需要：

```text
MySQL
Redis
Qdrant
```

MySQL 和 Redis 是原项目已有依赖。Qdrant 是这次 RAG 向量库改造新增的。

### 4.2 如果是 Windows 本地开发

Windows 推荐用 Docker Desktop 跑 Qdrant。

安装地址：

```text
https://docs.docker.com/desktop/setup/install/windows-install/
```

安装后打开 Docker Desktop，等它启动完成。

验证：

```powershell
docker --version
docker ps
```

如果 Windows 本地 Docker 长期不可用，可以不在本地跑 Qdrant。此时：

- 后端可以编译和跑单元测试。
- MySQL FULLTEXT 召回仍然可用。
- Qdrant 向量召回无法端到端验证。
- 需要在 Linux 服务器或其他同事机器上完成 Qdrant smoke test。

### 4.3 如果是 Linux 服务器

服务器大概率是 Linux，推荐直接用 Docker Engine 或 Docker Compose 跑 Qdrant。

Ubuntu/Debian 安装 Docker Engine 可参考 Docker 官方文档。简化命令如下：

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo systemctl enable docker
sudo systemctl start docker
docker --version
```

如果服务器不是 Ubuntu/Debian，请按对应发行版安装 Docker，或者让运维直接提供一个可访问的 Qdrant 地址。

## 5. 下载并启动 Qdrant

官方 Qdrant 本地 quickstart 推荐使用 Docker 运行 Qdrant。我们在 Windows 下使用 Docker volume 保存数据。

### 5.1 拉取镜像

```powershell
docker pull qdrant/qdrant
```

### 5.2 Windows PowerShell 启动 Qdrant

```powershell
docker run -d --name novel-qdrant `
  -p 6333:6333 `
  -p 6334:6334 `
  -v qdrant_storage:/qdrant/storage `
  qdrant/qdrant
```

### 5.3 Linux 服务器启动 Qdrant

Linux shell 使用反斜杠换行：

```bash
docker run -d --name novel-qdrant \
  -p 6333:6333 \
  -p 6334:6334 \
  -v qdrant_storage:/qdrant/storage \
  qdrant/qdrant
```

如果服务器开了防火墙，并且后端不在同一台机器上，需要开放 `6333`：

```bash
sudo ufw allow 6333/tcp
```

生产或多人共用环境建议不要把 Qdrant 裸露到公网。更稳妥的方式是：

- 后端和 Qdrant 部署在同一台服务器，用 `http://localhost:6333`。
- 或者内网访问，只允许后端服务器访问 Qdrant 端口。
- 或者使用反向代理和鉴权。

端口说明：

```text
6333: HTTP REST API，本项目使用这个端口
6334: gRPC API，当前代码暂时不用
```

访问：

```text
REST API: http://localhost:6333
Dashboard: http://localhost:6333/dashboard
```

### 5.4 验证 Qdrant 是否启动

```powershell
Invoke-RestMethod http://localhost:6333/healthz
```

期望结果：

```text
healthz check passed
```

查看 collections：

```powershell
Invoke-RestMethod http://localhost:6333/collections
```

第一次可能没有 `novel_rag_chunks`，这是正常的。后端第一次写入 chunk 向量时会自动创建 collection。

### 5.5 停止/重启 Qdrant

停止：

```powershell
docker stop novel-qdrant
```

再次启动：

```powershell
docker start novel-qdrant
```

删除容器：

```powershell
docker rm novel-qdrant
```

删除本地向量数据：

```powershell
docker volume rm qdrant_storage
```

注意：删除 volume 会清空所有本地 Qdrant 向量索引。

## 6. MySQL 建库和新表

### 6.1 创建数据库

如果本地还没有 `novel` 数据库：

```sql
CREATE DATABASE IF NOT EXISTS novel
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE novel;
```

PowerShell 连接 MySQL 示例：

```powershell
mysql -u root -p
```

或者直接执行：

```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS novel DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;"
```

### 6.2 执行 AI 表结构脚本

在后端根目录执行：

```powershell
mysql -u root -p novel < src/main/resources/sql/ai_init.sql
```

如果只想单独补 `chapter_ai_state`：

```powershell
mysql -u root -p novel < src/main/resources/sql/chapter_ai_state_ddl.sql
```

### 6.3 本次新增的核心表：rag_chunk

如果队友不想整份跑 `ai_init.sql`，可以只执行下面这段：

```sql
USE novel;

CREATE TABLE IF NOT EXISTS rag_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    start_offset INT NOT NULL,
    end_offset INT NOT NULL,
    embedding LONGTEXT NULL,
    embedding_model VARCHAR(80) NULL,
    token_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_chapter_chunk (chapter_id, chunk_index),
    INDEX idx_book_visible (book_id, sort_order),
    INDEX idx_chapter (chapter_id),
    FULLTEXT KEY idx_chunk_content (content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

说明：

- `rag_chunk.content` 存 chunk 文本，用于 MySQL FULLTEXT 检索。
- `rag_chunk.book_id/chapter_id/sort_order` 用于防止跨书、跨章节检索。
- `rag_chunk.embedding` 现在保留为兼容字段，但新流程的向量主要写入 Qdrant。
- `FULLTEXT idx_chunk_content(content)` 用于全文召回。

### 6.4 本次新增/需要确认的状态表：chapter_ai_state

```sql
USE novel;

CREATE TABLE IF NOT EXISTS chapter_ai_state (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    raw_hash VARCHAR(64) NOT NULL,
    normalized_hash VARCHAR(64) NOT NULL,
    semantic_hash VARCHAR(64) NOT NULL,
    last_action VARCHAR(40) NOT NULL,
    last_reason VARCHAR(255) NULL,
    processed_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_chapter_ai_state (chapter_id),
    INDEX idx_book_chapter_ai_state (book_id, chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

说明：

- 用来记录章节内容 hash。
- 作者修改章节后，系统可判断是跳过、只刷新 RAG 文本、还是完整重建 AI 数据。
- 避免无意义地重跑摘要、角色抽取和向量生成。

## 7. application.yaml 必须配置

本地开发至少确认这些配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/novel?characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_psw

  data:
    redis:
      host: localhost
      port: 6379
      password: your_psw

deepseek:
  api:
    key: your_apikey
    base-url: https://api.deepseek.com
    chat-model: deepseek-v4-pro

dashscope:
  api:
    key: your_apikey
  embedding:
    model: text-embedding-v4
    dimensions: 1024

novel:
  ai:
    vector-store: qdrant
    qdrant:
      base-url: http://localhost:6333
      collection-name: novel_rag_chunks
      vector-size: 1024
      distance: Cosine
    chunk:
      target-chars: 600
      overlap-chars: 100
      max-candidates: 40
      final-top-k: 6
    rerank-enabled: false
```

如果 Qdrant 跑在远程 Linux 服务器，开发机或后端服务器要改成对应地址：

```yaml
novel:
  ai:
    qdrant:
      base-url: http://服务器IP:6333
```

如果后端和 Qdrant 在同一台 Linux 服务器上，保持：

```yaml
novel:
  ai:
    qdrant:
      base-url: http://localhost:6333
```

注意：

- `qdrant.vector-size` 必须和 `dashscope.embedding.dimensions` 一致，目前都是 `1024`。
- `rerank-enabled: false` 表示现在只预留 reranker 接口，暂时不接真实 reranker。
- 如果 Qdrant 没启动，向量检索会失败并返回空结果，hybrid 检索仍可用 MySQL FULLTEXT 结果，但效果会下降。

## 8. Qdrant collection 需要手动创建吗

一般不需要。

代码里的 `QdrantRagVectorStore` 会在第一次写入向量时自动检查/创建 collection：

```text
collection-name: novel_rag_chunks
vector-size: 1024
distance: Cosine
```

如果想手动创建，也可以执行：

```powershell
$body = @{
  vectors = @{
    size = 1024
    distance = "Cosine"
  }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Method Put `
  -Uri "http://localhost:6333/collections/novel_rag_chunks" `
  -ContentType "application/json" `
  -Body $body
```

查看 collection：

```powershell
Invoke-RestMethod http://localhost:6333/collections/novel_rag_chunks
```

## 9. 已有小说数据如何重建 RAG

启动 MySQL、Redis、Qdrant 后，先启动后端：

```powershell
.\mvnw.cmd spring-boot:run
```

然后对已有书籍触发重建：

```text
POST /api/ai/admin/reprocess/{bookId}
```

示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8081/api/ai/admin/reprocess/1" `
  -Headers @{ Authorization = "Bearer 你的登录token" }
```

重建过程会：

1. 读取每章内容。
2. 按章节内严格分 chunk。
3. 写入 MySQL `rag_chunk` 元数据和 chunk 文本。
4. 调 DashScope 生成 chunk embedding。
5. 写入 Qdrant `novel_rag_chunks` collection。
6. 清理该书 AI cache。

## 10. 作者修改章节后的行为

现在章节新增/更新/删除会触发 AI 事件。

新增章节：

```text
ChapterPublishedEvent
-> 生成摘要
-> 抽取角色
-> 构建 chapter_index
-> 生成旧 chapter_embedding
-> 重建 rag_chunk + Qdrant vectors
-> 保存 chapter_ai_state
```

更新章节：

```text
ChapterUpdatedEvent
-> 重新从数据库加载完整章节
-> ChapterEditImpactAnalyzer 判断修改影响
-> 没变化：跳过
-> 轻微文本变化：尽量只刷新 rag_chunk 文本
-> 语义变化：重建摘要/角色/chapter_index/rag_chunk/Qdrant vectors
```

删除章节：

```text
ChapterDeletedEvent
-> 删除 rag_chunk
-> 删除 Qdrant 中 chapterId 对应 vectors
-> 删除 summary / character_event / chapter_index / chapter_embedding / chapter_ai_state
-> 清理该书 AI cache
```

## 11. 队友本地启动顺序

推荐顺序：

```powershell
# 1. 启动 Docker Desktop
docker ps

# 2. 启动 Qdrant
docker start novel-qdrant

# 如果容器还没创建，先执行：
docker run -d --name novel-qdrant `
  -p 6333:6333 `
  -p 6334:6334 `
  -v qdrant_storage:/qdrant/storage `
  qdrant/qdrant

# 3. 启动 MySQL 和 Redis
# 按各自本地安装方式启动

# 4. 执行 AI 表结构
mysql -u root -p novel < src/main/resources/sql/ai_init.sql

# 5. 启动后端
.\mvnw.cmd spring-boot:run
```

验证：

```powershell
Invoke-RestMethod http://localhost:6333/healthz
.\mvnw.cmd test
```

## 12. 如果本地无法测试 Qdrant，怎么判断风险

当前我能确认的部分：

```text
后端编译通过
单元测试通过
chunk 分块逻辑通过测试
Noop reranker 通过测试
RRF 融合通过测试
Qdrant HTTP 客户端代码能通过编译
```

当前不能仅靠本地编译确认的部分：

```text
Qdrant 容器是否能在你的机器启动
后端能否连到 Qdrant
Qdrant collection 是否能成功自动创建
chunk 向量是否能成功写入 Qdrant
Qdrant payload 过滤是否按 bookId/sortOrder 正常返回
已有书籍 reprocess 后 AI 问答是否命中正确 chunk
```

因此不能说“100% 没问题”。准确说法是：

```text
代码层面已通过编译和单元测试；Qdrant 端到端需要在 Linux 服务器或能运行 Docker 的环境里做一次 smoke test。
```

### 12.1 服务器 smoke test 步骤

在 Linux 服务器上：

```bash
# 1. 确认 Qdrant 正常
curl http://localhost:6333/healthz

# 2. 确认 collections API 正常
curl http://localhost:6333/collections

# 3. 启动后端
./mvnw spring-boot:run
```

然后调用重建：

```bash
curl -X POST \
  "http://localhost:8081/api/ai/admin/reprocess/1" \
  -H "Authorization: Bearer 你的登录token"
```

检查 Qdrant 是否出现 collection：

```bash
curl http://localhost:6333/collections/novel_rag_chunks
```

如果 collection 存在，再检查点数量：

```bash
curl http://localhost:6333/collections/novel_rag_chunks
```

如果后端日志没有 Qdrant 连接错误，并且阅读页 AI 问答能返回来源章节，说明基础链路通了。

### 12.2 无法本地 Docker 时的协作建议

你可以把本地开发分成两类：

```text
本地 Windows：
- 写代码
- 跑 mvn test
- 跑 MySQL/Redis 基础功能
- 不强求 Qdrant 端到端

Linux 服务器/同事机器：
- 跑 Qdrant
- 执行 ai_init.sql
- 调 reprocess
- 做 AI 问答 smoke test
```

## 13. 常见问题

### Q: 现在还需要 MySQL 的 `chapter_embedding` 吗？

短期保留。

旧的章节级 embedding 代码还在，避免一次性拆太多 AI 模块。但新的 chunk 级 RAG 向量检索已经走 Qdrant。

### Q: `rag_chunk.embedding` 为什么还在？

这是兼容字段。

当前代码写入 `rag_chunk` 时 `embedding` 可以为空，真正向量写入 Qdrant。保留这个字段是为了降低迁移风险，后面确认 Qdrant 方案稳定后可以再清理。

### Q: Qdrant 没启动会怎样？

写入向量会失败，日志会报 Qdrant 连接问题。

检索时 `ChunkVectorSearchEngine` 会捕获异常并返回空列表，`ChunkHybridSearchEngine` 仍会使用全文检索结果。但这不是完整效果，协作开发时应启动 Qdrant。

### Q: 为什么要同时用 MySQL 和 Qdrant？

MySQL 更适合存业务表、章节内容、chunk 文本、全文索引和关系数据。

Qdrant 更适合存向量、按向量相似度检索，并支持 payload 过滤。这里用 payload 的 `bookId` 和 `sortOrder` 防止跨书、越章检索。

### Q: 是否需要把 Qdrant 数据提交到仓库？

不需要，也不能提交。

Qdrant 数据在 Docker volume `qdrant_storage` 里，是本地运行数据。团队成员各自本地重建即可。

### Q: 如何清空并重建向量？

清空 Qdrant collection：

```powershell
Invoke-RestMethod `
  -Method Delete `
  -Uri "http://localhost:6333/collections/novel_rag_chunks"
```

然后重跑：

```text
POST /api/ai/admin/reprocess/{bookId}
```

## 14. 给队友的最短版 checklist

```text
1. 拉最新代码
2. 安装/启动 Docker Desktop
3. docker run 启动 novel-qdrant
4. MySQL 执行 src/main/resources/sql/ai_init.sql
5. 检查 application.yaml 的 MySQL/Redis/DeepSeek/DashScope/Qdrant 配置
6. 启动后端
7. 对已有书调用 /api/ai/admin/reprocess/{bookId}
8. 测试阅读页 AI 问答
```

## 15. 参考资料

- Qdrant Local Quickstart: https://qdrant.tech/documentation/quickstart/
- Qdrant Filtering: https://qdrant.tech/documentation/search/filtering/
- Docker Desktop Windows Install: https://docs.docker.com/desktop/setup/install/windows-install/
