# Architecture

The browser uses React and TypeScript. During development Vite proxies /api to Spring MVC on port 8002. The frontend request and streaming formats are preserved.

## Java backend

- AuthService and Passwords handle registration, opaque sessions, PBKDF2 hashes, session expiry, and role checks. Raw session tokens are returned only to the client; database sessions store their SHA-256 digest.
- WorkspaceService scopes task plans, requests, project memberships, and document access. Private data is answered from authorized SQL records.
- HistoryService checks conversation ownership and project membership before accessing messages. Completed exchanges are persisted transactionally.
- QueryPolicy and PersonalService recognize private task, progress, and request questions and follow-ups.
- RagService retrieves authorized knowledge, resolves explicit procedures and technology facts from evidence, and calls Ollama when a generated explanation is needed.
- OllamaClient streams real provider deltas and verifies the completion event. Incomplete provider streams are not saved as complete exchanges.
- Bootstrap provides explicit schema initialization, knowledge indexing, and interactive administrator promotion.

PostgreSQL persists users, sessions, tasks, memberships, requests, conversation history, documents, and 768-dimensional pgvector embeddings. JDBC operations use bound parameters.

Corporate retrieval combines lexical and vector similarity when embeddings are available; lexical search remains available when the embedding provider is unavailable. Project documents are searched only within an authorized project. There is no automatic fallback from a project to another project's documentation.

## Configuration

.env is local and ignored. Runtime environment variables override file settings. FIRSTDAY_ENV_FILE or the firstday.env JVM property selects an alternate configuration file. The bundled local setup uses an independent database and ports. No existing Python data is migrated automatically.

No hosted model, Python service, or Langfuse account is required. Optional cloud tracing from the Python implementation has not been ported.

## Development boundaries

The supplied server binds to localhost. Production hosting still requires deployment-specific TLS, reverse proxy configuration, operational monitoring, backups, and abuse controls. The repository is a local demonstration, not an assertion of production certification.

## Mode and conversational context

The authoritative onboarding_mode comes from the authenticated database profile. COMPANY means Internship: the personal project list is hidden; project reads and project chat requests are forbidden, even if a membership exists. Administrator assignment operations retain their separate role checks.

ChatService resolves quoted and chained follow-ups using the current conversation's recent questions. HistoryService supplies the latest user/assistant turns only after checking employee ownership and matching project scope. RagService receives bounded history as conversational context and uses the selected knowledge documents as evidence. Private progress and task answers continue to come from scoped SQL.

Internship searches restrict source categories and exclude named project documents. All Internship answer text is validated before being emitted; project responses retain streaming provider deltas. The active conversation ID is remembered per browser tab, user, mode, and project. Stored messages are retained in PostgreSQL; changing modes never deletes conversation data.
