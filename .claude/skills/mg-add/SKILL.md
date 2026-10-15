---
name: mg-add
description: Add a new concept to learn in Mental Gym and find the 2-4 documents to read for it. Use when the user wants to learn or add a topic, concept or subject, says "add X to mental gym", or asks for reading material on a concept.
---

# Add a concept

1. Read the concept name and optional topic from the user's message. Ask once only if the concept is unclear.
   Without a topic, the concept lands in `General`.
2. Call `add_concept`. Keep the returned `conceptId`.
3. Search the web for **2-4 non-redundant documents** that teach this concept well. Prefer official documentation and
   the right version. Skip blog spam and tutorials that repeat each other. No roles, no categories, just good sources.
4. Fetch each page's text. If a fetch fails or the text is obviously thin (blocked, JavaScript-only, cookie wall),
   say so and ask the user to paste the text instead.
5. Show the user the list: title, URL, one line on what it covers. Approval is not required, but make swapping easy —
   if the user wants a different link, fetch it and replace.
6. Call `save_docs` with the fetched text (`replace: true` when swapping the whole set). Report any size warning the
   tool returns.
7. Tell the user to read the docs, then run `/mg-learn` for questions or `/mg-test` when ready.

Never summarize the docs into the database instead of the real text: tests are generated from exactly what is stored.
