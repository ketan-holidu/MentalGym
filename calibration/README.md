# Calibration set

Grading quality is the main risk in Mental Gym, so it gets checked by hand before the tool is trusted.

## How to run a calibration round

1. Pick two concepts you know well. Store their docs here as `<concept>/doc-1.md`, `doc-2.md`, …
   (the same text that went into `save_docs`, so questions and fixtures match).
2. Run `/mg-test` on the concept and keep the generated questions.
3. For each question write three answers into `<concept>/fixtures.md`:
   - **shallow** — keywords, no reasoning,
   - **medium** — the main idea, missing a criterion or two,
   - **deep** — what you would say in a review.
4. Submit each set as its own attempt and compare:
   - scores must come out shallow < medium < deep,
   - shallow must land below the pass threshold,
   - deep must land at or above it.
5. Run each fixture twice. Large swings between runs mean the rubric or the grader prompt needs work, not the answer.
6. Anything you flag with `/mg-flag` joins the regression set: re-run those answers whenever `grade-v1` changes,
   and bump the version string in `.claude/agents/mg-grader.md` when you do.

`calibration/` holds the inputs. The grades themselves live in the database and on the dashboard.

## Rounds run so far

- **2026-10-15 — Transactional propagation and rollback rules** (`gen-v1` / `grade-v1`):
  ordering held on all three questions across two grading runs, shallow 0.00-0.35, deep 1.00, largest swing 0.136.
  See `spring-transactional-propagation/results.md`.
