# Calibration round 1 — Transactional propagation and rollback rules

2026-10-15 · `gen-v1` / `grade-v1` · pass threshold 0.80 · test `22c73893-b2aa-4cb9-b2df-dde5d82089fc`

Questions 1, 3 and 4 of a six-question test. Each fixture set was submitted as its own attempt and graded twice by
independent grader subagents that were given only an `attemptId` and a `questionId` — never the depth level.

## Scores

| Question | Type | shallow | medium | deep |
|---|---|---|---|---|
| 1 | predict_debug | 0.182 → 0.091 | 0.636 → 0.773 | 1.000 → 1.000 |
| 3 | compare_synthesize | 0.000 → 0.000 | 0.773 → 0.773 | 1.000 → 1.000 |
| 4 | scenario | 0.350 → 0.250 | 0.700 → 0.700 | 1.000 → 1.000 |

(run 1 → run 2)

## Against the acceptance criteria

- **shallow < medium < deep** — holds for all three questions in both runs.
- **shallow below the threshold** — yes, 0.00 to 0.35, nowhere near 0.80.
- **deep at or above the threshold** — yes, 1.00 throughout.
- **consistency across runs** — 6 of 9 gradings identical; the largest swing was 0.136 (question 1, medium).
  No fixture crossed the pass threshold between runs.
- **misconception detection** — `misconception_flagged` fired on all three shallow answers in both runs, and on
  none of the medium or deep ones.

Grading is honest enough to use.

## What the round exposed

1. **No discrimination at the top.** All three deep answers scored a flat 1.00 in both runs. Either the rubrics top
   out for any thorough doc-grounded answer, or the grader is generous once the main points are present. Worth
   watching: if real answers keep landing on 1.00, the rubrics need a criterion that separates "complete" from
   "expert".
2. **The threshold does real work.** The medium band sat at 0.64 to 0.77 — answers that get the central mechanism
   right but skip a sub-question fail at 0.80. That is the intended behaviour, and it means 0.80 is strict rather
   than ceremonial.
3. **Variance lives in one kind of criterion.** Every disagreement between runs was on a criterion that asks
   whether the learner *named a rule* versus merely *described its effect* (question 1's "caller must not be
   misled" rationale, question 4's global-fix mechanism). Rubric criteria should say explicitly whether naming the
   mechanism is required, or graders will keep splitting that call at 0.5.
4. **A real bug fell out of the round**, before any grading: the verification payload carried the full doc text and
   reached 57.8 KB, which the MCP client truncated to a ~2 KB preview. The verifier refused to save verdicts it
   could not justify rather than rubber-stamping them. Doc text now comes from the paginated `get_docs` tool, and
   is inlined in a role payload only when the whole doc set fits inside `mental-gym.inline-doc-chars`.

## Caveat on this round

All nine answers were written by Claude from the same three documents the questions came from, so a clean ordering
partly measures self-consistency rather than mastery detection. The signals that survive that caveat are the ones
where the grader could have failed and did not: shallow answers scoring near zero *with* the right misconceptions
flagged, and deep answers never being marked down for something the docs do not support. A round with answers
written by the owner would be the real test of the upper half of the scale.

## Procedure notes

- Questions 2, 5 and 6 were answered with a filler string and deliberately left ungraded, so the three attempts
  stay unfinalized and the concept's mastery state and review ladder were never touched by calibration data.
- Raw per-criterion data: `run-1.json`, `run-2.json`. Answers: `fixtures.md`. Questions: `questions.md`.
- Attempt ids — shallow `e4089608-ef44-467d-8035-2a81d9c7f9f4`, medium `e385068f-28c7-495a-947a-0b48eb214059`,
  deep `abe98092-91d7-484c-898e-fdd5364ff082`.
