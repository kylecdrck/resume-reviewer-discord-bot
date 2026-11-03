# Resume Reviewer

A Discord bot that reviews resume PDFs for **ATS (Applicant Tracking System) compliance** using an LLM.

A user drops a resume PDF into a forum thread and runs `/resume-review review`. The bot extracts the text, asks an OpenAI-compatible LLM to score the resume against a fixed ATS rubric, and posts a structured report (overall score, per-category ratings, critical blockers, strengths, recommendations) back into the thread. Optionally, it also scores the resume against up to five job roles.

Results are cached by the SHA-256 hash of the original file, so re-submitting the same resume returns the stored report without another LLM call. The stored copy of the resume text is redacted of PII first.

---

## Features

- **ATS evaluation** — LLM-scored across four rubrics: Structure & Parsing (/25), Contact Information (/10), Keywords & Alignment (/30), Impact & Metrics (/35). The overall score is the sum (/100).
- **Role suitability (optional)** — a separate LLM call scores the resume 0-100 against each requested role with a one-line reasoning.
- **Confirmation gate** — an ephemeral Continue / Cancel prompt before any LLM call is made (45s timeout).
- **Hash-based cache** — identical files are never evaluated twice; the stored report is returned instead.
- **PII redaction** — name, email, and PH phone numbers are replaced with placeholders before the text is persisted.
- **Channel allow-list** — admins can restrict the bot to specific channels/forums via `/resume-review config`; commands run elsewhere are rejected.
- **Auto-review on post** — a new post in an allowed forum whose starter message carries a `FOR_REVIEW_*.pdf` attachment is reviewed automatically, no slash command needed. An optional `REVIEW_ROLES: a,b,c` line in the post body adds the role suitability analysis.
- **Graceful LLM failure** — if the LLM is unreachable or returns an unparseable response, the user gets a clear "cannot be completed at this time" message.

## Commands

| Command | Who | Where | Description |
|---|---|---|---|
| `/resume-review review <file> [role_suitability]` | Thread author | Allowed forum | Reviews a resume PDF (max 5MB). `role_suitability` is an optional comma-separated list of up to 5 roles, e.g. `software engineer,QA,tester`. Replies "This command cannot be used here" when the parent forum is not on the allow-list. |
| `/resume-review config` | Server admin | Server | Opens an ephemeral form to select the channels/forums the bot may be used in. An empty selection resets to "all channels". |

## How a review runs

1. The attachment is validated (PDF only, ≤ 5MB) and downloaded into memory.
2. The bot replies ephemerally with **Continue** / **Cancel**. No click within `REVIEW_CONFIRMATION_TIMEOUT_SECONDS` (default 45) cancels the review.
3. On **Continue**:
   1. Text is extracted with PDFBox and sanitized (control codes, zero-width chars, and private-use glyphs are stripped).
   2. The SHA-256 hash of the **original** file is computed. If a report already exists for that hash, it is returned and the flow stops.
   3. Otherwise the LLM is called with the ATS rubric prompt and the response is parsed.
   4. The report is posted in the thread and persisted with the **redacted** resume text.
   5. If roles were requested and the review was fresh, a second LLM call posts the role suitability scores. Cached resumes skip this step.

Scanned PDFs without a text layer are not OCR'd and are rejected.

## Auto-review on post

Creating a post in an allowed forum can replace the slash command entirely:

```
Hello guys, this is my resume, please help evaluate!

REVIEW_ROLES: Engineer

attachment: FOR_REVIEW-my-resume.pdf
```

- Only the **starter message** of a new post is inspected; replies never re-trigger.
- The post must be created by the thread owner in a forum on the allow-list.
- Exactly one attachment must be a PDF whose name starts with `FOR_REVIEW_` (case-sensitive) and is ≤ 5MB.
- `REVIEW_ROLES:` is optional, must start a line, is case-sensitive, and takes up to 5 comma-separated roles. Only the first matching line is used.
- The confirmation gate is skipped; the report is posted in the thread.

Auto-review requires the **Message Content** privileged intent to be enabled for the bot in the Discord developer portal.

## Tech stack

- Java 21, Spring Boot 4.1
- JDA 5.6 (Discord)
- Apache PDFBox 3.0 (text extraction)
- Spring Data JPA + Liquibase (schema is owned by the changelogs; `ddl-auto: validate`)
- H2 (local/test) and PostgreSQL (production)
- Any OpenAI-compatible `/chat/completions` endpoint

## Getting started

### Prerequisites

- Java 21
- Maven (or the bundled `mvnw.cmd` / `mvnw`)
- A Discord bot token from the [Discord Developer Portal](https://discord.com/developers/applications)
- An API key for an OpenAI-compatible LLM endpoint

### Run locally

```powershell
$env:DISCORD_TOKEN = "Mz..."
$env:LLM_API_KEY = "sk-..."
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile uses an in-memory H2 database that is rebuilt on every start, so nothing persists between runs. The H2 console is at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:resume-reviewer`, user `sa`, empty password).

To test against PostgreSQL instead, use the `local-tst` profile — see [LOCAL-DEV.md](LOCAL-DEV.md) for both profiles, the full environment variable reference, and verification steps.

### Build and test

```powershell
.\mvnw.cmd clean verify
```

### Deploy

`.github/workflows/deploy-to-prd.yml` deploys to Railway (`railway up`) using the `RAILWAY_TOKEN` secret and the `RAILWAY_SERVICE` variable in the `prd` environment.

## Configuration

All settings are environment variables with defaults in `src/main/resources/application.yaml`.

| Variable | Default | Description |
|---|---|---|
| `DISCORD_ENABLED` | `true` | Start the Discord bot |
| `DISCORD_TOKEN` | *(blank)* | Bot token (required when enabled) |
| `LLM_BASE_URL` | `https://api.openai.com/v1` | OpenAI-compatible base URL |
| `LLM_API_KEY` | *(blank)* | Bearer token for the LLM endpoint |
| `LLM_MODEL` | `auto` | Model identifier |
| `LLM_TIMEOUT_SECONDS` | `120` | Connect/read timeout for LLM requests |
| `LLM_REASONING_EFFORT` | `none` | Sent as `reasoning_effort`; blank to omit |
| `LLM_MAX_TOKENS` | `2000` | Max tokens in the LLM response |
| `LLM_LOG_REQUEST` / `LLM_LOG_RESPONSE` | `false` | Log full LLM request/response bodies (both `true` in the `local` profile) |
| `REVIEW_CONFIRMATION_TIMEOUT_SECONDS` | `45` | How long the Continue/Cancel prompt stays open |
| `BOT_CONFIG_FORM_TIMEOUT_SECONDS` | `300` | How long the `/resume-review config` form stays open |
| `BOT_CONFIG_CACHE_TTL_SECONDS` | `60` | How long the channel allow-list cache is valid |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | — | PostgreSQL connection (only for the `local-tst` profile) |

## Project layout

```
src/main/java/dev/pandesal/resume_reviewer/
├── config/      Spring config, JDA setup, typed properties, bot config service
├── discord/     Slash commands, listener, confirmation/config interaction handlers, message formatting
├── domain/      JPA entities (Review, BotConfig)
├── llm/         OpenAI-compatible chat client
├── pdf/         PDF text extraction, sanitizing, hashing
├── redaction/   PII redaction
├── repository/  Spring Data repositories
└── review/      Review orchestration, ATS and role-suitability evaluators, prompt loading
src/main/resources/
├── db/changelog/  Liquibase changelogs
└── prompts/       LLM prompt templates
```

## Documentation

- [LOCAL-DEV.md](LOCAL-DEV.md) — local profiles, environment variables, verification
- [RESUME-REVIEW-NEW-FLOW.md](RESUME-REVIEW-NEW-FLOW.md) — evaluation flow specification
- [src/main/resources/ATS-compliance-guide.md](src/main/resources/ATS-compliance-guide.md) — ATS scoring rubric