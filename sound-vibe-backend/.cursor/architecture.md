# SoundVibe Architecture Overview

## 1. Module Structure (Maven)
The project is a multi-module Maven project:
- `sound-vibe-backend` (Root)
  - `vibe-common`: Shared DTOs, Utils, Global Exception, Result Wrapper. (No Main Class)
  - `vibe-gateway` (Port 8080): Spring Cloud Gateway + Sentinel.
  - `vibe-auth` (Port 8081): User Identity, JWT generation.
  - `vibe-asset` (Port 8082): Audio file upload (MinIO), Asset Management.
  - `vibe-search` (Port 8083): Elasticsearch interactions, Vector Search.
  - `vibe-market` (Port 8084): Licensing, Orders, Shopping Cart.
  - `vibe-algo` (Port 5000): Python/FastAPI Sidecar (Not a Maven module, separate folder).

## 2. Infrastructure (Docker - Apple Silicon Optimized)
- **Nacos:** `localhost:8848` (Ver: 2.3.2-slim, ARM64 supported).
- **MySQL:** `localhost:3306` (Ver: 8.0, Database: `sound_vibe_db`).
- **Redis:** `localhost:6379` (Ver: 7.0).
- **RabbitMQ:** `localhost:5672` (Ver: 3.12).
- **MinIO:** `localhost:9000` (API), `localhost:9001` (Console).
- **Elasticsearch:** `localhost:9200` (Ver: 8.11.1).

## 3. Critical Business Flows

### A. Audio Upload & Analysis Pipeline
1.  **Upload:** User POSTs file to `vibe-asset`.
2.  **Storage:** `vibe-asset` saves bytes to MinIO.
3.  **Event:** `vibe-asset` publishes `AssetUploadEvent` to RabbitMQ.
4.  **Analysis (Async):**
    - `vibe-asset` (Consumer) listens to event.
    - Calls `vibe-algo` (Python) via HTTP/Feign to extract BPM, Key, and Vector.
    - Updates MySQL `audio_meta`.
5.  **Indexing:**
    - Once analysis is done, `vibe-asset` publishes `AssetIndexedEvent`.
    - `vibe-search` listens and syncs data to Elasticsearch.

### B. Search Flow
1.  User requests `vibe-search`.
2.  If text query: `vibe-search` calls `vibe-algo` to embed text -> vector.
3.  `vibe-search` queries Elasticsearch (kNN + Filtering).

## 4. Security
- Gateway verifies JWT in `Authorization` header.
- Downstream services trust the Gateway (or check `X-User-Id` header).