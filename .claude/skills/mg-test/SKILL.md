---
name: mg-test
description: Run a Mental Gym test for a concept - generates questions, verifies them, asks them closed book, grades the answers and shows the result. Use when the user wants to be tested, quizzed or examined on a concept, says "test me", or wants to do a review that is due.
---

# Run a test

You are the orchestrator. The generator, verifier and grader run as subagents so that this conversation never sees
answer keys or rubrics before grading.

1. **Resolve the concept.** Use `list_concepts` when the user named it loosely, or `get_stats` when they asked for
   whatever is due. Call `get_concept`. If the docs are not all read, ask the user to confirm they have read them and
   then call `mark_docs_read`. If the concept has no docs, stop and point at `/mg-add`.

2. **Generate.** Spawn the `mg-question-generator` subagent with the concept id and nothing else. It returns a
   `testId` and the prompts only.

3. **Verify.** Spawn the `mg-question-verifier` subagent with that `testId`. It returns a verdict per position.
   For any rejected position, spawn `mg-question-generator` again with the concept id, the `testId` and the rejected
   positions — it reads the reasons itself. At most **2 retry rounds**. If fewer than 4 questions end up valid,
   abort and tell the user the test could not be built.

4. **Ask.** Call `get_test_questions`. Present the questions **one at a time**, closed book: no hints, no follow-up
   teaching, no reaction to whether an answer looks right or wrong. Keep each answer exactly as the user wrote it.
   "I don't know" is a valid answer — move on.

5. **Submit.** Call `submit_attempt` with every answer verbatim.

6. **Grade.** Spawn one `mg-grader` subagent **per question, in parallel**, each with only the `attemptId` and one
   `questionId`. Never pass the answer text or anything you think about it.

7. **Report.** Call `get_attempt_result` and show: the score and pass/fail against the threshold, per question the
   feedback and the score, the **missed criteria even when the attempt passed**, which docs to reread, and the new
   review date when the concept was mastered. Mention `/mg-flag` if a grade looks wrong.
