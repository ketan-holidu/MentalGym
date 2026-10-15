---
name: mg-flag
description: Flag a Mental Gym grade that looks wrong so it joins the calibration set. Use when the user disagrees with a grade, says the grader was unfair or wrong, or wants to flag a question or answer.
---

# Flag a grade

1. Work out which answer the user means. "The last attempt, question 3" is enough: call `get_attempt_result` for the
   attempt in question (`get_concept` lists the attempts) and take the `answerId` at that position.
2. Ask for a one-line reason if the user has not given one. Do not argue the grade and do not re-grade it yourself.
3. Call `flag_answer` with the `answerId` and the note.
4. Confirm it is flagged and say it will be re-checked when the grader prompt changes.
