# Contributing

Thank you for taking a look at FirstDay AI Java.

## Before opening a change

- Describe the behavior you want to improve and include a reproducible example.
- Keep Internship and Project Onboarding boundaries intact.
- Use synthetic accounts and documentation in test scenarios.
- Do not include credentials, local database exports, user conversations, or .env files.

## Development checks

Run Test-Project.ps1 for fast Java checks and the frontend build. Changes affecting retrieval, SQL, conversation context, or authorization should also run Test-Project.ps1 -Integration against the isolated test schema.

See [the testing guide](docs/TESTING.md) for prerequisites and individual test selectors.

## Pull requests

Explain the problem, resulting behavior, and checks performed. For answer regressions, include the question, expected sources or facts, and prohibited claims. For interface changes, include screenshots with synthetic data.

Generated build artifacts belong in workflow artifacts, not source control.
