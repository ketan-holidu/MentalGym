---
name: mg-status
description: Show Mental Gym progress - streak, concepts due for review, counts by status and recent attempts. Use when the user asks how they are doing, what is due, what to study next, or for their learning status or streak.
---

# Show progress

1. Call `get_stats`, and `list_concepts` when the user asked about a specific topic or status.
2. Report, compactly:
   - the current streak (and the longest one),
   - what is due for review now, oldest first,
   - counts by status: not started, reading, ready to test, mastered,
   - the last few attempts with their scores.
3. Suggest one next step: the oldest due review, or the concept sitting in `ready_to_test` longest.
4. Mention the dashboard at http://localhost:8080/ for the full picture.
