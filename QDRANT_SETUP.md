# Qdrant Local Setup

This project can use Qdrant as the local vector database for RAG chunk embeddings.

## Why Qdrant

- Runs locally with Docker on Windows.
- Provides an HTTP API, so the backend does not need a heavy Java SDK.
- Stores vectors separately from MySQL while MySQL keeps business metadata.
- Fits the current RAG design: one vector per `rag_chunk`.

## Prerequisites

Install Docker Desktop for Windows:

```text
https://docs.docker.com/desktop/setup/install/windows-install/
```

After installation, open Docker Desktop and wait until it says Docker is running.

Verify Docker:

```powershell
docker --version
docker ps
```

## Start Qdrant

Run:

```powershell
docker run -d --name novel-qdrant `
  -p 6333:6333 `
  -p 6334:6334 `
  -v qdrant_storage:/qdrant/storage `
  qdrant/qdrant:latest
```

Qdrant HTTP API will be available at:

```text
http://localhost:6333
```

Dashboard:

```text
http://localhost:6333/dashboard
```

## Verify Qdrant

Run:

```powershell
Invoke-RestMethod http://localhost:6333/healthz
```

Expected:

```text
healthz check passed
```

You can also list collections:

```powershell
Invoke-RestMethod http://localhost:6333/collections
```

## Backend Configuration

The backend reads these settings from `src/main/resources/application.yaml`:

```yaml
novel:
  ai:
    vector-store: qdrant
    qdrant:
      base-url: http://localhost:6333
      collection-name: novel_rag_chunks
      vector-size: 1024
      distance: Cosine
```

The vector size must match DashScope `text-embedding-v4` dimensions:

```yaml
dashscope:
  embedding:
    dimensions: 1024
```

## Stop Qdrant

Stop the container:

```powershell
docker stop novel-qdrant
```

Start it again later:

```powershell
docker start novel-qdrant
```

Remove the container:

```powershell
docker rm novel-qdrant
```

Remove stored vector data:

```powershell
docker volume rm qdrant_storage
```

Only remove the volume when you intentionally want to delete all local vector indexes.

## Rebuild Existing Book Vectors

After Qdrant is running, rebuild a book's AI data through the backend endpoint:

```text
POST /api/ai/admin/reprocess/{bookId}
```

This triggers chapter AI processing and writes chunk vectors to Qdrant.

## Notes For Collaborators

- MySQL still stores books, chapters, users, summaries, character events, and `rag_chunk` metadata.
- Qdrant stores the actual chunk vectors and payloads used for vector retrieval.
- If Qdrant is not running, vector retrieval will fail gracefully and hybrid retrieval can still use fulltext chunk retrieval.
- Do not commit local Docker volumes or generated Qdrant storage files.
