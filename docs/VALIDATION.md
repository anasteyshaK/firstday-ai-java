# Local validation

Date: 24 November 2026.

The Java backend was verified through Maven Wrapper with JDK 21.0.6. All **213 tests passed**: zero failures, zero errors, zero skipped tests.

| Suite | Passed |
| --- | ---: |
| Core behavior, validation, and password interoperability | 45 |
| Authentication service unit tests | 14 |
| Conversation history unit tests | 12 |
| Personal answers unit tests | 8 |
| Chat orchestration unit tests | 7 |
| Internship question and source regression unit tests | 13 |
| Chat mode and follow-up unit tests | 17 |
| Internship source and generation guard unit tests | 15 |
| API contracts and authorization boundaries | 18 |
| Ollama HTTP streaming and embedding contracts | 4 |
| Live PostgreSQL/Ollama integration and answer regression | 60 |

There are 131 unit tests, 22 fast contract tests, and 60 integration cases. The unit tests need no running database or Ollama. The integration suite includes nine source-selection and forty answer-regression scenarios. It also verifies private ownership, membership revocation/restoration, access requests, document isolation, completed-stream persistence, and a real Ollama embedding round-trip through pgvector. Its temporary schema was verified before fixture creation and removed afterwards. Cleanup checks confirmed that synthetic accounts, memberships, and messages did not leak into the public application schema. Legitimate writes from someone using the application no longer cause false failures through global row-count comparisons.

The independent Java database contains 77 knowledge documents and 154 chunks, all with local embeddings, plus two synthetic projects. No original user accounts or conversations were migrated, and no test accounts remain in the public schema.

The React frontend passed its TypeScript check and production build. Its development server returned HTTP 200; the proxied health endpoint identifies the Java API; the readiness endpoint confirmed the PostgreSQL connection; a protected API request without a session returned HTTP 401. The login page was checked in the browser. Authenticated user workflows were exercised through backend integration tests rather than a complete browser click-through.

## Reproducible evidence

- [JUnit results](validation/junit.xml)
- [Machine-readable summary](validation/summary.json)
- [Testing commands and prerequisites](TESTING.md)

These are local results for this edition and configuration, not results from a hosted GitHub Actions run. Model-based checks can vary with a different installed model. Optional Langfuse cloud telemetry from the Python edition is not implemented. The original Python source tree was not modified.

Exported JUnit evidence contains testcase names and timings; local filesystem paths, environment variables, and console logs are omitted.

The question “как проходит интершип?” is covered explicitly. Internship overview aliases select the documented 30-day training plan. Missing evidence no longer falls back to unrelated incident runbooks; training stages and procedures are extracted from the approved document for Russian overview questions. Project and technical subtopics retain their separate scope.

## Internship scope and conversation memory

The backend takes the mode from the authenticated user profile. In COMPANY/Internship mode, project questions receive a boundary response without project resources; a forged project_id is rejected. Personal project listings are empty and project plan, document, and conversation reads are denied in this mode. Assigned memberships and their progress are retained for a later authorized mode change.

Internship retrieval uses company/training/support categories and excludes named project content. Generated Internship text is buffered until the completed answer passes the scope check, so blocked partial text is not streamed. Project mode retains normal authorized project responses.

Follow-ups tolerate opening quotes and conversational prefixes and preserve the original topic through multiple turns. The generator receives recent user and assistant messages from the same owned, scoped conversation. The bounded context favors the newest messages and treats history as reference context rather than factual evidence or instructions.

PostgreSQL-backed API tests verify a training question followed by a quoted ownership question, saved/restored messages, denied access by another user, blocked project scope spoofing, and preserved old project messages after switching to Internship. Frontend TypeScript and build checks passed. Active conversation selection is restored within the same browser tab using a key containing user, mode, and project; disabling browser storage does not disable server history. A signed-in browser click-through was not performed in the in-app browser because its session was at the login screen.

## Hosted GitHub Actions validation

On 24 November 2026, [this hosted run](https://github.com/anasteyshaK/firstday-ai-java/actions/runs/45485243174) passed both jobs: Java 21 tests and executable JAR packaging, and the TypeScript/Vite frontend build. The fast job runs the 153 service-independent checks; live PostgreSQL/Ollama integration scenarios remain opt-in. JUnit reports are uploaded as the java-test-results artifact, and a test summary is displayed on the workflow run. The hosted runner uses its installed Maven; the Maven Wrapper remains included for local development.
