# Mental Gym

A personal learning tool for one user: add concepts, read 2-4 good documents, get tested hard on them, and watch
progress. All the AI work happens inside Claude Code (skills and subagents). This repository is the data layer,
the rules engine and the dashboard, exposed to Claude Code as an MCP server on localhost.

## Run it

```bash
cp .env.example .env
docker compose up
```

- MCP endpoint: `http://localhost:8080/mcp` (published on `127.0.0.1` only)
- Dashboard: `http://localhost:8080/`

Claude Code picks the server up from `.mcp.json` in this folder and asks once to approve it. Open Claude Code here
and run `/mg-add`, `/mg-learn`, `/mg-test`, `/mg-status` or `/mg-flag`.

## How a concept moves

```
/mg-add    → concept + docs stored          (not_started → reading)
/mg-learn  → questions answered from docs
             "done reading"                 (reading → ready_to_test)
/mg-test   → generate → verify → take → grade
             pass                           (→ mastered, next review in 3, 7, 14, 30, 60, 120 days)
             fail                           (→ ready_to_test, ladder reset)
```

## Who may see what

The point of the design is that the part that tests you is not the part that teaches you.

| Context | Tools |
|---|---|
| main session (skills) | `add_concept`, `list_concepts`, `get_concept`, `save_docs`, `mark_docs_read`, `get_test_questions`, `submit_attempt`, `get_attempt_result`, `flag_answer`, `get_stats` |
| `mg-question-generator` | `get_generation_material`, `get_docs`, `save_test` |
| `mg-question-verifier` | `get_test_for_verification`, `get_docs`, `save_verdicts` |
| `mg-grader` | `get_grading_material`, `get_docs`, `save_grade` |

The subagent files restrict this with their `tools:` allowlist, and `save_test` / `get_test_questions` / `get_concept`
never return answer keys or rubrics, so the main session cannot leak what it never received.

The one gap: the main session is connected to the same MCP server and *could* call a generator or grader tool.
`CLAUDE.md` forbids it. Per-role endpoints would make it impossible; that is deliberately out of scope for v1.

## Configuration

`.env` (see `.env.example`):

| Variable | Default | Meaning |
|---|---|---|
| `PASS_THRESHOLD` | `0.8` | score at or above which an attempt passes |
| `SRS_LADDER_DAYS` | `3,7,14,30,60,120` | review ladder, one step per pass |
| `TZ` | `Europe/Berlin` | timezone that decides what counts as an activity day |
| `SERVER_PORT` | `8080` | host port, bound to 127.0.0.1 |
| `ALLOWED_ORIGINS` | localhost:8080 | origins accepted on `/mcp`; requests without an `Origin` header are allowed |
| `POSTGRES_PASSWORD` | `mentalgym` | database password |

## Development

```bash
cd server
mvn -s settings.xml test          # unit tests + the end-to-end tool contract test
mvn -s settings.xml spring-boot:run
```

`settings.xml` points Maven at Maven Central directly, so the build does not depend on a company mirror.

The integration test needs Docker. With Rancher Desktop:

```bash
DOCKER_HOST=unix:///Users/$USER/.rd/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock mvn -s settings.xml test
```

## Backups

The database lives in the named volume `mental-gym-db`. It survives `docker compose down` but **not** `down -v`.

```bash
./scripts/backup.sh     # writes backups/mental-gym-<timestamp>.sql
```

## Layout

```
.claude/skills/     mg-add, mg-learn, mg-test, mg-status, mg-flag
.claude/agents/     mg-question-generator, mg-question-verifier, mg-grader
server/             Kotlin + Spring Boot: MCP tools, rules engine, dashboard, migrations
calibration/        fixtures for checking that grading is honest
docs/               PRD and implementation notes
scripts/backup.sh   pg_dump into ./backups
```
