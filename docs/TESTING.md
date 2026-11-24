# Testing the Java edition

## Fast suite

Run Test-Project.ps1 from the root, or backend/mvnw -B -f backend/pom.xml test. No live PostgreSQL or Ollama is needed for the fast suite.

The fast suite covers password hashing and verification, session tokens, input validation, task templates, intent routing, source-grounding rules, role boundaries, API response contracts, NDJSON events, simulated Ollama embeddings and interrupted streams.

## Unit tests (JUnit 5 and Mockito)

The following service tests run without Spring Boot startup, PostgreSQL, or a running Ollama installation:

| Test class | Cases | Behaviors |
| --- | ---: | --- |
| [AuthServiceTest](../backend/src/test/java/com/novatech/firstday/AuthServiceTest.java) | 14 | Credentials, expired/revoked sessions, administrator permissions, logout, login, password hash exclusion |
| [HistoryServiceTest](../backend/src/test/java/com/novatech/firstday/HistoryServiceTest.java) | 12 | Conversation ownership, project scope, revoked membership, chronological context, blocked foreign deletion |
| [PersonalServiceTest](../backend/src/test/java/com/novatech/firstday/PersonalServiceTest.java) | 8 | Tool-specific requests, remaining/next tasks, completed plans, project progress, recorded blockers |
| [ChatServiceTest](../backend/src/test/java/com/novatech/firstday/ChatServiceTest.java) | 7 | Private routing, context follow-ups, topic changes, failed generation, disconnected clients, persistence |
| [InternshipAnswerTest](../backend/src/test/java/com/novatech/firstday/InternshipAnswerTest.java) | 13 | Internship synonyms, relevant training sources, missing evidence, topic and project separation |
| [ChatScopeTest](../backend/src/test/java/com/novatech/firstday/ChatScopeTest.java) | 17 | Internship boundaries, spoofed scope, quoted and chained follow-ups, topic changes |
| [InternshipScopeTest](../backend/src/test/java/com/novatech/firstday/InternshipScopeTest.java) | 15 | Allowed documents, safe generation, bounded recent context, prohibited sources |
| [CoreTest](../backend/src/test/java/com/novatech/firstday/CoreTest.java) | 45 | Passwords, validation, intent rules, templates, progress, evidence handling |

These are 131 unit test cases. Another 22 fast contract tests exercise MockMvc API behavior and a simulated Ollama HTTP server, bringing the fast suite to 153 cases. The live integration suite adds 60 cases, for 213 in a full run.

To run only the four service unit test classes, use Maven with the test selector:

~~~powershell
. ./Java-Environment.ps1
./backend/mvnw.cmd -f backend/pom.xml "-Dtest=AuthServiceTest,HistoryServiceTest,PersonalServiceTest,ChatServiceTest" test
~~~

The tests use synthetic identities and mocked repositories; they do not change existing application data.

## Integration and answer regression

Run Test-Project.ps1 -Integration, or backend/mvnw -B -f backend/pom.xml "-Dfirstday.integration=true" test.

Prerequisites: initialized PostgreSQL with pgvector; knowledge_base directory; local Ollama with nomic-embed-text and the configured CHAT_MODEL.

Integration tests create a randomly named java_test_* schema in the configured database. They insert synthetic accounts, projects, documents and conversations there, test actual SQL and model responses, then remove only that schema. Before inserting fixtures, the suite verifies its current database schema. After cleanup it verifies that the schema is gone and that its synthetic users, memberships, and messages did not leak into the application's public schema. It does not compare global row counts, which legitimately change while someone uses the application.

The suite includes nine source-selection scenarios and forty answer scenarios, plus database-backed Internship/project boundaries, quoted follow-ups, saved history, authentication, permissions, assignment revocation/restoration, private requests, document access, and streaming persistence checks. Model-based cases assert documented facts and prohibited claims, rather than exact generated wording.

Fast CI intentionally skips live integration tests. A green hosted CI run does not claim that PostgreSQL or an Ollama installation was checked. Local integration results and installed models must be evaluated separately.

## Reports

Maven writes JUnit XML and text reports to backend/target/surefire-reports. They are generated build artifacts and are ignored by Git. The GitHub Actions workflow uploads them as the java-test-results artifact and also type-checks and builds the frontend. See the published repository Actions tab for hosted runs.

See VALIDATION.md for the recorded local run and its limitations.
