# llmchat

A chat app for OpenAI-compatible LLMs. Spring Boot API (`apps/api`) and React web client (`apps/web`).

## Prerequisites

- Java 17
- Node.js 20+
- Docker (for PostgreSQL and Redis)
- An API key for an OpenAI-compatible LLM endpoint

## Setup

1. Install dependencies:

   ```bash
   npm install
   ```

2. Start PostgreSQL and Redis:

   ```bash
   docker compose up -d
   ```

   Postgres listens on `5432` (database, user and password all `llmchat`), Redis on `6379`.

3. Configure the API. Copy the example file and fill in the values:

   ```bash
   cp apps/api/.env.example apps/api/.env
   ```

   ```properties
   POSTGRE_URL=jdbc:postgresql://localhost:5432/llmchat
   POSTGRE_USERNAME=llmchat
   POSTGRE_PASSWORD=llmchat

   # Base64-encoded, at least 32 bytes: openssl rand -base64 32
   JWT_SECRET=<generated secret>

   REDIS_HOST=localhost
   REDIS_PORT=6379

   LLM_BASE_URL=https://api.openai.com/v1
   LLM_API_KEY=<your key>
   LLM_MODEL=<model name>
   ```

4. Configure the web client:

   ```bash
   cp apps/web/.env.example apps/web/.env.local
   ```

   `VITE_API_URL` defaults to `http://localhost:8081`. The Vite dev server proxies `/api/*` to it, so no CORS setup is needed.

The database schema is created by Flyway when the API starts.

## Run

```bash
npm run dev        # API on http://localhost:8081 and web on http://localhost:5173
npm run dev:api    # API only
npm run dev:web    # web only
npm run test:api   # API tests (needs Postgres and Redis running)
npm run build:web  # production build of the web client
```

Open http://localhost:5173 and register an account.

To reset the database and Redis data:

```bash
docker compose down -v
docker compose up -d
```
