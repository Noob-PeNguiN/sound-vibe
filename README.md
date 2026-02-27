# SoundVibe

SoundVibe 是一个**数字音乐资产交易平台**，支持音乐作品上传、AI 智能分析与标注、向量化语义搜索、AI 音乐生成，以及完整的购物车与订单交易流程。

项目采用 **Monorepo** 结构，包含三个子系统：

| 子系统 | 技术栈 | 说明 |
|--------|--------|------|
| `sound-vibe-backend` | Java 17 / Spring Cloud | 微服务后端 |
| `sound-vibe-frontend` | Vue 3 / TypeScript | 前端 SPA |
| `sound-vibe-analysis` | Python / FastAPI / PyTorch | AI 音频分析服务 |

## 功能概览

- **用户系统** — 注册、登录、JWT 鉴权
- **资产管理** — 音频文件上传至 MinIO 对象存储，自动触发 AI 分析
- **AI 音频分析** — 自动提取 BPM、调性、时长；CLAP 模型生成 512 维音频嵌入向量；Zero-Shot 自动标注（114 个候选标签）
- **AI 音乐生成** — 基于 MusicGen 的文本到音频生成
- **作品发布** — 发布 Beat/Track，支持多文件 Pack
- **语义搜索** — 基于 Elasticsearch + CLAP 向量的文本语义检索
- **购物车 & 订单** — 完整的交易流程，支持超时自动取消（RabbitMQ 延迟队列 + Redis 分布式锁）

## 技术架构

```
                         ┌──────────────┐
                         │   Frontend   │ :5173
                         │  Vue 3 + TS  │
                         └──────┬───────┘
                                │
                         ┌──────▼───────┐
                         │   Gateway    │ :8080
                         │ Spring Cloud │
                         └──────┬───────┘
                                │
          ┌─────────┬───────────┼───────────┬──────────┐
          │         │           │           │          │
    ┌─────▼──┐ ┌───▼────┐ ┌───▼────┐ ┌───▼────┐ ┌───▼────┐
    │  Auth  │ │ Asset  │ │Catalog │ │ Search │ │ Order  │
    │ :8081  │ │ :8082  │ │ :8083  │ │ :8084  │ │ :8085  │
    └────────┘ └───┬────┘ └────────┘ └───┬────┘ └────────┘
                   │                     │
              ┌────▼─────────────────────▼────┐
              │     Analysis (Python)  :8090  │
              │   CLAP · MusicGen · FastAPI   │
              └───────────────────────────────┘

基础设施: MySQL · Redis · RabbitMQ · MinIO · Elasticsearch · Nacos
```

## 环境要求

| 依赖 | 版本要求 |
|------|---------|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| Python | 3.10+ |
| Docker & Docker Compose | 最新稳定版 |

## 快速开始

### 1. 启动基础设施

所有中间件通过 Docker Compose 一键启动：

```bash
cd sound-vibe-backend/docker
docker-compose up -d
```

这将启动以下服务：

| 服务 | 端口 | 用途 |
|------|------|------|
| Nacos | 8848 | 服务注册与配置中心 |
| MySQL | 3306 | 主数据库 |
| Redis | 6379 | 缓存 & 分布式锁 |
| RabbitMQ | 5672 / 15672 | 消息队列 / 管理界面 |
| MinIO | 9000 / 9001 | 对象存储 / 管理界面 |
| Elasticsearch | 9200 | 搜索引擎 |
| Kibana | 5601 | ES 可视化 |

### 2. 初始化数据库

连接 MySQL（`root` / `root`），创建数据库并执行建表脚本：

```sql
CREATE DATABASE IF NOT EXISTS sound_vibe_db DEFAULT CHARACTER SET utf8mb4;
```

各模块的建表 SQL 位于对应模块的 `src/main/resources/db/` 目录下。

### 3. 启动后端微服务

```bash
cd sound-vibe-backend

# 编译整个项目
mvn clean install -DskipTests

# 按顺序启动各服务（每个在单独终端中运行）
cd vibe-auth    && mvn spring-boot:run
cd vibe-asset   && mvn spring-boot:run
cd vibe-catalog && mvn spring-boot:run
cd vibe-search  && mvn spring-boot:run
cd vibe-order   && mvn spring-boot:run
cd vibe-gateway && mvn spring-boot:run
```

也可以直接在 IDE（IntelliJ IDEA / VS Code）中逐个启动各模块的 `Application` 主类。

微服务端口一览：

| 服务 | 端口 | Nacos 服务名 |
|------|------|-------------|
| vibe-gateway | 8080 | vibe-gateway |
| vibe-auth | 8081 | vibe-auth |
| vibe-asset | 8082 | vibe-asset |
| vibe-catalog | 8083 | vibe-catalog |
| vibe-search | 8084 | vibe-search |
| vibe-order | 8085 | vibe-order |

### 4. 启动 AI 分析服务

```bash
cd sound-vibe-analysis

# 创建并配置环境变量
cp .env.example .env   # 如果没有 .env.example，参见下方配置

# 方式一：本地运行
pip install -r requirements.txt
python -m src.main

# 方式二：Docker 运行
docker build -t vibe-analysis .
docker run -p 8090:8090 --env-file .env vibe-analysis
```

Analysis 服务的 `.env` 配置：

```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=sound_vibe_db
DB_USER=root
DB_PASSWORD=root

RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest

MINIO_ENDPOINT=localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=soundvibe-assets
MINIO_SECURE=false

SERVICE_HOST=0.0.0.0
SERVICE_PORT=8090
```

> 首次启动会自动下载 CLAP 和 MusicGen 模型（约 2-3 GB），请耐心等待。

### 5. 启动前端

```bash
cd sound-vibe-frontend/sound-vibe-frontend

npm install
npm run dev
```

访问 **http://localhost:5173** 即可使用。

开发模式下，Vite 会自动将 `/api/*` 请求代理到对应的后端微服务。

## 项目结构

```
SoundVibe-Project/
├── sound-vibe-backend/          # Java 微服务后端
│   ├── vibe-common/             #   公共模块（DTO、工具类、全局异常）
│   ├── vibe-gateway/            #   API 网关（路由、JWT 鉴权、CORS）
│   ├── vibe-auth/               #   认证服务（注册、登录）
│   ├── vibe-asset/              #   资产服务（文件上传、MinIO 管理）
│   ├── vibe-catalog/            #   目录服务（作品发布、查询）
│   ├── vibe-search/             #   搜索服务（Elasticsearch 全文 + 向量检索）
│   ├── vibe-order/              #   订单服务（购物车、下单、支付）
│   ├── docker/                  #   Docker Compose 基础设施
│   └── pom.xml                  #   Maven 父 POM
│
├── sound-vibe-frontend/         # Vue 3 前端
│   └── sound-vibe-frontend/
│       ├── src/
│       │   ├── api/             #     API 请求封装（Axios）
│       │   ├── views/           #     页面组件
│       │   ├── components/      #     可复用组件
│       │   ├── stores/          #     Pinia 状态管理
│       │   ├── types/           #     TypeScript 类型定义
│       │   └── router/          #     路由配置
│       ├── package.json
│       └── vite.config.ts
│
├── sound-vibe-analysis/         # Python AI 分析服务
│   ├── src/
│   │   ├── main.py              #     入口（FastAPI + RabbitMQ Worker）
│   │   ├── worker.py            #     消息队列消费者
│   │   ├── processor.py         #     音频特征提取（BPM、调性）
│   │   ├── model_manager.py     #     AI 模型管理（CLAP、MusicGen）
│   │   ├── tagger.py            #     Zero-Shot 自动标注
│   │   ├── music_generator.py   #     AI 音乐生成
│   │   └── ...
│   ├── requirements.txt
│   └── Dockerfile
│
├── .gitignore
└── README.md
```

## 核心技术选型

### 后端

| 类别 | 技术 |
|------|------|
| 基础框架 | Spring Boot 3.2 + Spring Cloud 2023 |
| 服务治理 | Spring Cloud Alibaba Nacos（注册 & 配置） |
| 网关 | Spring Cloud Gateway |
| 服务调用 | OpenFeign |
| ORM | MyBatis-Plus 3.5 |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 7 + Redisson（分布式锁） |
| 消息队列 | RabbitMQ 3.12 |
| 对象存储 | MinIO |
| 搜索引擎 | Elasticsearch 8.11 |

### 前端

| 类别 | 技术 |
|------|------|
| 框架 | Vue 3 (Composition API + `<script setup>`) |
| 语言 | TypeScript |
| 构建 | Vite 5 |
| 状态管理 | Pinia |
| 路由 | Vue Router |
| 样式 | Tailwind CSS |
| HTTP | Axios |
| 音频 | Tone.js + @tonejs/midi |

### AI 分析

| 类别 | 技术 |
|------|------|
| 框架 | FastAPI + Uvicorn |
| 音频处理 | librosa |
| 嵌入模型 | CLAP (laion/clap-htsat-unfused) |
| 音乐生成 | MusicGen (facebook/musicgen-small) |
| 深度学习 | PyTorch |
| 消息队列 | pika (RabbitMQ) |

## Gateway 路由

所有外部请求通过 Gateway（`:8080`）统一路由：

| 路径前缀 | 目标服务 |
|----------|---------|
| `/auth/**` | vibe-auth |
| `/assets/**` | vibe-asset |
| `/catalog/**` | vibe-catalog |
| `/search/**` | vibe-search |
| `/order/**` | vibe-order |

## 默认账号

| 服务 | 地址 | 用户名 | 密码 |
|------|------|--------|------|
| MySQL | localhost:3306 | root | root |
| RabbitMQ 管理界面 | http://localhost:15672 | guest | guest |
| MinIO 管理界面 | http://localhost:9001 | minioadmin | minioadmin |
| Nacos 控制台 | http://localhost:8848/nacos | nacos | nacos |
