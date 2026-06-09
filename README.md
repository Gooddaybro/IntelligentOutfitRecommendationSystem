# Intelligent Outfit Recommendation System

This repository is organized as a full-stack project while keeping the original project name unchanged.

```text
Intelligent Outfit Recommendation System/
├── backend/          # Java Spring Boot backend
├── frontend/         # React + TypeScript + Vite frontend
├── docs/             # Development documents and contracts
├── docker-compose.yml
└── README.md
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
npm run build
```

By default, Vite proxies `/api` to `http://localhost:8080`. Use `VITE_API_BASE_URL` when the backend is deployed elsewhere.
