# Implementation notes

What the build settled, and where it departs from the PRD. Written while implementing; keep it current.

## Verified against current docs (section 14 of the PRD)

| Question | Answer |
|---|---|
| Restricting a subagent to MCP tools | `tools:` in the agent frontmatter takes exact names, `mcp__<server>__<tool>`, e.g. `mcp__mental-gym__save_grade`. `mcp__<server>__*` grants a whole server. |
| Subagent model choice | Yes: `model: opus \| sonnet \| haiku \| fable \| <full id> \| inherit`. All three agents use `opus`. |
| Can subagents spawn subagents | **Yes**, up to three layers deep by default — the PRD's assumption was wrong. `mg-test` still orchestrates from the main session, because only the main session can ask the user the questions. The agents' `tools:` lists exclude `Agent`, so they cannot spawn anything. |
| Skill discovery | `.claude/skills/<name>/SKILL.md`, frontmatter `name` + `description` (both used for auto-matching), invocable as `/<name>`. No restart needed; only a `.claude/skills` directory that did not exist at session start needs `/reload-skills` once. |
| `.mcp.json` HTTP syntax | `{"mcpServers": {"mental-gym": {"type": "http", "url": "..."}}}`. `claude mcp add --transport http mental-gym http://localhost:8080/mcp --scope project` writes the same thing. Project scope asks for approval once. |
| MCP SDK for Kotlin/Spring | Spring AI 2.0.1 `spring-ai-starter-mcp-server-webmvc` with `spring.ai.mcp.server.protocol=STREAMABLE`. Tools are plain methods annotated `@McpTool` / `@McpToolParam` (`org.springframework.ai.mcp.annotation`). Spring AI 2.0.1 requires Spring Boot 4.1.1. |

## Stack

Kotlin on Spring Boot 4.1.1, Spring AI 2.0.1, Flyway, `JdbcClient` with hand-written SQL (no JPA — the jsonb columns
and the tiny query surface do not need it), Postgres 17.2, Jackson 3 (what Boot 4 and the MCP SDK use).

Build with `mvn -s settings.xml`: the committed `server/settings.xml` points at Maven Central, so a company mirror in
`~/.m2/settings.xml` cannot break the build.

## Departures from the PRD

- **Tool parameters are camelCase** (`conceptId`, `includeDocsText`, `testId`), because Spring AI derives the schema
  from the Kotlin parameter names. Tool *names* are snake_case as specified.
- **`save_verdicts` takes an optional `category`** (`ambiguous`, `key_wrong`, `unanswerable`, `recall_only`) next to
  the reason. The server falls back to deriving it from the reason text. Reasons stay in the database; only the
  category goes back to the caller.
- **The `General` topic is created by a startup runner**, not by a migration: migrations stay DDL-only.
- **Docs and reading never demote a mastered concept.** `save_docs` and `mark_docs_read` leave `mastered` alone, so
  swapping a doc before a review does not wipe the review schedule.
- **`submit_attempt` is idempotent by content**: resubmitting the same answers for a test that still has an ungraded
  attempt returns that attempt instead of creating a second one.
- **Re-grading a finalized attempt** updates the score and pass flag but does not touch the concept state again
  (that would double-count a review step). The server logs a warning when it happens.
- **Evidence is enforced server-side**: `save_grade` rejects any criterion with credit above 0 and no quote from the
  answer. The grader prompt says the same thing; the server makes it true.
- **`docsToReread` lists every doc of the concept** when anything was missed. Without the `question_docs` link table
  (out of scope for v1) the server cannot say which doc a question came from.
- **Doc text is not embedded in the role payloads.** `get_generation_material`, `get_test_for_verification` and
  `get_grading_material` list the docs by `docIndex`, title, url and length; the text comes from a 17th tool,
  `get_docs(conceptId, docIndex?, offset?)`, one document per call, sliced at `mental-gym.doc-chunk-chars`
  (default 20000) with a `nextOffset` to continue. Found by dogfooding: with three real Spring reference pages
  (28 KB) the verification payload reached 57.8 KB and Claude Code truncated it to a ~2 KB preview, so the verifier
  could not read the questions it was meant to check. `get_generation_material` at 35.7 KB had squeaked through.
  Every payload is now bounded whatever the doc set weighs.
- **`get_test_for_verification` takes an optional `position`** so the verifier can walk a long test one question at
  a time.
- **The dashboard draws its own SVG trend** instead of pulling a charting library from a CDN, so it works offline.

## Rules that live in the server, not in prompts

- Question score = `sum(weight × credit) / sum(weight)`; test score = mean of the question scores (4 decimals).
- `passed = score >= threshold`, threshold stored on the attempt when it is submitted.
- Pass: `mastered`, `mastered_at` set once, `review_count + 1`, next ladder step, `next_review_at = now + step`.
- Fail: `ready_to_test`, ladder and `next_review_at` cleared.
- An attempt is only finalized once every answer has a grade; re-running grading for missing answers is safe.
- Validation: 4-8 questions per test, 3-5 rubric criteria with unique ids and positive weights, known question types
  and criterion kinds, credit within 0..1, no answers for unknown questions, no attempt on a draft test.
- Activity day = a day (in `TZ`) with a submitted attempt or a `mark_docs_read` call. Streak and longest streak are
  derived from those days, never stored.

## Security

`/mcp` is behind an origin filter: a request carrying an `Origin` header outside `ALLOWED_ORIGINS` gets 403. Requests
without an `Origin` header (what an MCP client sends) pass. The port is published as `127.0.0.1:8080:8080`, so nothing
outside this machine can reach it. No auth, single user, no API keys anywhere — the server never calls an LLM.

## Testing

- `RulesTest` covers scoring, the pass rule, the review ladder, the streak and validation.
- `ToolFlowIntegrationTest` runs the whole Phase 1 flow against a real Postgres (Testcontainers): add concept → docs →
  read → generate → one rejection → regenerate → verify → take → grade → pass → mastered with a review date → second
  attempt failing → ladder reset. It also asserts the information barrier, by serializing what `save_test`,
  `get_test_questions` and `get_concept` return and failing if an answer key or rubric appears in the JSON.

`ToolFlowIntegrationTest` waits for the container to be reachable *from the host* (a short `DriverManager` retry in
its `@DynamicPropertySource`) before Spring builds the context. The Postgres container's default wait strategy only
watches its own log, and on Rancher Desktop or Colima the published port can start forwarding a second or two after
that, which otherwise fails the whole context with "connection refused".

On Rancher Desktop, Testcontainers needs:

```bash
DOCKER_HOST=unix:///Users/$USER/.rd/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Still open

- Calibration round 1 passed on 2026-10-15 (`calibration/spring-transactional-propagation/results.md`), but every
  fixture answer was written by Claude. A round with the owner's own answers is still needed to test the upper half
  of the scale, and the rubrics show no discrimination above "complete".
- Per-role MCP endpoints (`/mcp/main`, `/mcp/generator`, `/mcp/grader`) remain the hardening option if the main
  session ever misbehaves.
- Deleting a concept is still a manual SQL job.
