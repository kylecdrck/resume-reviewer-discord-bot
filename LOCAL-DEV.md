# Local Development Guide

The base config (`application.yaml`) defaults to **PostgreSQL**, so running with
no profile connects to Postgres. Two local profiles are available:

| Profile | Database | Use case |
|---|---|---|
| `local` | In-memory H2 (fresh every run) | Quick dev/testing, no setup |
| `local-tst` | PostgreSQL | Testing against the "real" database |

## Prerequisites
- **Java 21** + **Maven** (or use `.\mvnw.cmd`)
- **PostgreSQL** running locally (for the default config and `local-tst`), or
  set `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` to point elsewhere

---

## Profile: `local` (H2)

No database setup needed — the schema is rebuilt from the Liquibase changelog on every run and nothing persists between runs.

### Environment variables
| Variable | Required | Default |
|---|---|---|
| `DISCORD_TOKEN` | **yes** (to run the bot) | *(blank)* — from Discord Developer Portal |
| `DISCORD_ENABLED` | no | `true` |
| `LLM_API_KEY` | **yes** (for LLM evaluation) | *(blank)* — without it, the review cannot be completed |
| `LLM_BASE_URL` | no | `https://api.openai.com/v1` |
| `LLM_MODEL` | no | `auto` |
| `LLM_TIMEOUT_SECONDS` | no | `120` — connect/read timeout for LLM requests |
| `LLM_REASONING_EFFORT` | no | `none` — sent as `reasoning_effort` to reduce LLM response time; set blank to omit |
| `LLM_LOG_REQUEST` | no | `false` — log the full LLM API request body; **enabled by default in the `local` profile** |
| `LLM_MAX_TOKENS` | no | `2000` — max tokens for the evaluation LLM response |
| `REVIEW_CONFIRMATION_TIMEOUT_SECONDS` | no | `45` |
| `BOT_CONFIG_FORM_TIMEOUT_SECONDS` | no | `300` — how long the `/resume-review config` form stays open before timing out |
| `BOT_CONFIG_CACHE_TTL_SECONDS` | no | `60` — how long the in-memory config cache is valid before being renewed from the DB |

### Run
```powershell
$env:DISCORD_TOKEN = "Mz..."
$env:LLM_API_KEY = "sk-..."
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```
Or build first:
```powershell
.\mvnw.cmd clean package
java -jar target\resume-reviewer-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```
Or from the IDE: run `ResumeReviewerApplication` with VM option `--spring.profiles.active=local`.

### H2 console
Available at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:resume-reviewer`, user `sa`, empty password).

---

## Profile: `local-tst` (PostgreSQL)

The default (no profile) config already targets PostgreSQL; this profile exists
for explicit local testing against it.

### 1. Create the database
```sql
CREATE DATABASE resume_reviewer;
```

### 2. Environment variables
Everything from the `local` table above, plus:

| Variable | Required | Default |
|---|---|---|
| `DB_URL` | no | `jdbc:postgresql://localhost:5432/resume_reviewer` |
| `DB_USERNAME` | **yes** | *(blank)* |
| `DB_PASSWORD` | **yes** | *(blank)* |

```powershell
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "yourpassword"
$env:DISCORD_TOKEN = "Mz..."
$env:LLM_API_KEY = "sk-..."
```

### 3. Run
```powershell
.\mvnw.cmd clean package
java -jar target\resume-reviewer-0.0.1-SNAPSHOT.jar --spring.profiles.active=local-tst
```

Liquibase creates the schema automatically on first run.

---

## Production

No profile is set in production. The app connects to PostgreSQL via
`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` and **Liquibase runs on every startup**
(`spring.liquibase.enabled` defaults to `true`), applying any pending changesets
before Hibernate validates the schema. Set `LIQUIBASE_ENABLED=false` to skip it.

---

## Verifying the bot
Invite the bot to your Discord server, then in a **forum thread you authored** (the command is rejected for anyone else):
- `/resume-review review <pdf> [role_suitability]` — ephemeral confirmation with **Continue** / **Cancel** buttons (45s timeout). **Continue** runs the LLM evaluation, caches the result (keyed by the original file hash, stored text redacted of PII), and posts the report in the thread. **Cancel** (or timeout) discards the submission. The optional `role_suitability` option takes up to 5 comma-separated roles (e.g. `software engineer,QA,tester`); for a **fresh** review a separate LLM call then posts a per-role suitability score (0-100) with short reasoning. Already-reviewed resumes skip the role analysis.
- `/resume-review config` — **admin-only** (requires the `ADMINISTRATOR` permission). Opens an ephemeral form with a multi-select channel menu (text/news/forum, up to 25 options) and **Submit** / **Cancel** buttons. **Submit** saves the selected channel IDs, restricting the bot's features to those channels; an empty selection resets to the default (bot works in all channels). **Cancel** (or timeout) discards the form without saving. Config changes are served from an in-memory cache and propagate within `BOT_CONFIG_CACHE_TTL_SECONDS` (immediately for the admin who saved).
- **Auto-review** — create a post in an allowed forum with a single `FOR_REVIEW_*.pdf` attachment (≤ 5MB) in the starter message. The review runs without the slash command or the confirmation gate. Add an optional `REVIEW_ROLES: Engineer,QA` line (own line, exact case, max 5 roles) to also run the role suitability analysis. Replies inside the thread never re-trigger.

## Notes
- The **Message Content** privileged intent must be enabled for the bot in the Discord developer portal, otherwise auto-review cannot read the post body.
- `ddl-auto: validate` — the schema is owned by Liquibase; don't hand-edit tables.
- If the schema changed since an earlier `local-tst` run (e.g., the `file_hash` column), drop and recreate the database.
- Slash commands are registered **globally** on startup — new/changed commands can take a minute to appear in Discord.
