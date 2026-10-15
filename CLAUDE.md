# Mental Gym

A personal learning tool. You add concepts, read documents, get tested on them and track progress.
Every piece of state lives behind the `mental-gym` MCP server (`http://localhost:8080/mcp`).

## Hard rules

- Never invent state and never write SQL. Concepts, docs, tests, attempts and grades only ever change through MCP tools.
- The main session must never call the generator, verifier or grader tools: `get_generation_material`, `save_test`,
  `get_test_for_verification`, `save_verdicts`, `get_grading_material`, `save_grade`, `get_docs`. Those belong to the subagents
  `mg-question-generator`, `mg-question-verifier` and `mg-grader`, which have them in their tool allowlists.
- Never reveal an answer key, a rubric or a grading criterion before the attempt is graded. During a test, do not say
  whether an answer is right, do not hint and do not correct.
- After grading, always show the missed criteria, even when the attempt passed.
- Tool parameters are camelCase (`conceptId`, `includeDocsText`, `testId`, `answerId`).

## Skills

- `/mg-add` — add a concept, find 2-4 good docs, store their text.
- `/mg-learn` — teacher mode, answers questions grounded in the stored docs.
- `/mg-test` — generate, verify, run and grade a test.
- `/mg-status` — streak, what is due, concepts by status.
- `/mg-flag` — flag a grade that looks wrong.

## The stack must be running

The tools only work while `docker compose up` is running in this folder. If a tool call fails to connect, tell the user
to start the stack instead of working around it. The dashboard is at http://localhost:8080/.
