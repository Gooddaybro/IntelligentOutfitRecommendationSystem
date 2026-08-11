# Intelligent Outfit Recommendation System

This repository is organized as a full-stack project while keeping the original project name unchanged.

```text
Intelligent Outfit Recommendation System/
├── backend/          # Java Spring Boot backend
├── frontend/         # React + TypeScript + Vite frontend
├── scripts/          # Local demo entrypoints
├── .env.demo.example # Non-secret Docker demo defaults
├── .env.daocloud.example # Docker demo defaults with DaoCloud image mirrors
├── docs/             # Development documents and contracts
├── docker-compose.yml
├── docker-compose.demo.yml
└── README.md
```

## One-Command Demo

Prerequisites:

- Docker Desktop or Docker Engine with Docker Compose v2.
- Clone `AI-Clothing-Shopping-Assistant-System` beside this repository, or set
  `PYTHON_AI_CONTEXT` to its absolute path before running the script.

Default startup, using official image registries:

```bash
cp .env.demo.example .env
sh scripts/start-demo.sh
```

China mainland startup, using public DaoCloud mirror variables:

```bash
cp .env.daocloud.example .env
sh scripts/start-demo.sh
```

国内网络优先用 `.env.daocloud.example`。它只把 Docker 镜像、Maven 仓库和
pip 源切到更容易访问的公开镜像源，不改变端口、demo 密码或业务开关。如果公开
镜像源临时不可用，直接在 `.env` 里替换对应的 `*_IMAGE`、`MAVEN_REPO_URL`
或 `PIP_INDEX_URL` 即可。

This starts MySQL, Redis, RabbitMQ, LangGraph PostgreSQL, Elasticsearch, the
Java backend, the Python AI service, the React frontend, and a one-shot product
search index rebuild.

After startup, check these local endpoints:

- Frontend: <http://localhost:3000>
- Java backend health: <http://localhost:8080/actuator/health>
- Python AI health: <http://localhost:8000/health>
- Elasticsearch health: <http://localhost:9200/_cluster/health>

Kibana is optional because it is only used for Elasticsearch inspection. Start
it when needed:

```bash
COMPOSE_PROFILES=observability sh scripts/start-demo.sh
```

If the Python repository is not beside this one, set `PYTHON_AI_CONTEXT` to its
path before running the script. The demo enables recommendation ES recall with
`APP_RECOMMENDATION_ES_RECALL_ENABLED=true`.

If registry access is unstable, override the image variables in `.env`.
Runtime service images use `MYSQL_IMAGE`, `REDIS_IMAGE`, `RABBITMQ_IMAGE`,
`LANGGRAPH_POSTGRES_IMAGE`, `ELASTICSEARCH_IMAGE`, `KIBANA_IMAGE`, and
`CURL_IMAGE`. Build base images use `JDK_BASE_IMAGE`, `JRE_BASE_IMAGE`,
`NODE_BASE_IMAGE`, `NGINX_BASE_IMAGE`, and `PYTHON_BASE_IMAGE`.
`MAVEN_REPO_URL`, `PIP_INDEX_URL`, and `PIP_TRUSTED_HOST` only affect dependency
downloads inside the backend and Python Docker builds.

The DaoCloud example only changes image and dependency download sources; service
ports, demo secrets, and application feature flags stay the same as
`.env.demo.example`. It uses DaoCloud's Docker Hub mirror for Docker Hub images
and DaoCloud's Elastic mirror for Elasticsearch/Kibana. Public mirrors can be
rate-limited or temporarily unavailable, so replace the image variables with
your own reachable registry if pulls still fail.

Stop the demo without deleting volumes:

```bash
sh scripts/stop-demo.sh
```

Stop the demo and delete demo volumes:

```bash
sh scripts/stop-demo.sh -v
```

## Backend

The backend remains the source of truth for users, sessions, products, SKUs, prices, inventory, orders, payments, and frontend APIs.

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Verification:

```powershell
cd backend
.\mvnw.cmd verify
```

## Frontend

The frontend provides two main shopping modes:

- AI recommendation mode: chat first, with recommendation cards and cart beside the conversation.
- Traditional browse mode: product browsing first, with AI assistance beside the catalog.

The AI can recommend products, but cart and order operations still require explicit user confirmation in the frontend and are executed through Java backend APIs.

```powershell
cd frontend
npm install
npm run dev
```

Verification:

```powershell
cd frontend
npm test -- --run
npm run test:e2e
npm run build
```

By default, Vite proxies `/api` to `http://localhost:8080`. Use `VITE_API_BASE_URL` when the backend is deployed elsewhere.

## Quality Closure Backlog

The next approved work should follow:

- `docs/superpowers/plans/2026-06-19-ai-commerce-quality-closure.md`
- `docs/superpowers/specs/2026-06-19-frontend-e2e-quality-gate-design.md`
