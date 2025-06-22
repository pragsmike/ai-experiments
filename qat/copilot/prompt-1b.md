**Engineering Specification
Multi-Agent Interview-Transcript Generator (Clojure)**

---

### 1  Objective

Design and implement the first iteration of a Clojure-based, multi-agent pipeline that converts an input article into high-quality interview transcripts (JSONL) via iterative question-answer exchanges. The system will initially include two agents—**Question Generator** and **Answer Agent**—with clear extension points for future roles (Critic, Moderator, Context Manager).

---

### 2  Key Requirements

| ID  | Requirement                                                            | Notes                                              |
| --- | ---------------------------------------------------------------------- | -------------------------------------------------- |
| R-1 | Read a single article (plaintext or Markdown).                         | Input source can be file path, URL, or string.     |
| R-2 | Generate *N* context-relevant questions.                               | Parameter `N` configurable (default = 5).          |
| R-3 | Produce corresponding answers grounded in the article.                 | Must reference article facts; avoid hallucination. |
| R-4 | Loop over multiple *aspects* (e.g., factual, analytical, critical).    | Aspect list configurable.                          |
| R-5 | Output each Q\&A session as a JSONL line conforming to the spec in §5. | One `conversation_id` per aspect iteration.        |
| R-6 | Provide deterministic, testable functions with pure inputs/outputs.    | Side effects isolated in I/O layer.                |

---

### 3  Architecture Overview

```text
┌────────────┐   article   ┌────────────────────┐   questions   ┌───────────────┐
│ Article In │ ──────────► │ Question Generator │ ────────────► │ Answer Agent │
└────────────┘             └────────────────────┘               └───────────────┘
      ▲                              │                                 │
      └────────── JSONL transcript ◄─┴────────── assembler / formatter ┘
```

* **Coordination**: use **core.async** channels for back-pressure and composability.
* **Parallelism**: wrap long-running steps in **futures**; enforce timeouts.
* **State**: share immutable context via atoms; use STM refs only if future agents require joint updates.

---

### 4  Implementation Checklist

1. **Project Scaffold**

   ```bash
   clj -A:new app interview-gen
   ```

2. **Protocols & Records**

   ```clojure
   (defprotocol QGen (generate-qs [_ article aspect n]))
   (defrecord OpenAIQGen [...] QGen
     (generate-qs [this art asp n] ...))
   ```

3. **Channels**

   ```clojure
   (def question-ch (chan 10))
   (def answer-ch   (chan 10))
   ```

4. **Agent Loop (MVP)**

   ```clojure
   (go-loop [[aspect & more] aspects]
     (when aspect
       (let [qs  (<! (generate-qs qgen article aspect N))
             ans (<! (answer-fn aagent article qs))]
         (>! answer-ch {:aspect aspect :qas ans})
         (recur more))))
   ```

5. **Transcript Formatter** – ensure output matches §5.

6. **Config File** – EDN with model keys, prompt templates, and aspect list.

7. **Unit Tests** – cover parsing, generation counts, JSONL schema validation.

---

### 5  Output Schema (JSONL)

Each line = one aspect-focused session.

```json
{
  "conversation_id": "art42_iter1",
  "article_metadata": { "title": "...", "domain": "...", "source": "...", "length": 2500 },
  "session_metadata": { "focus_aspect": "factual_content", "iteration_number": 1 },
  "messages": [
    { "role": "user", "content": "Question text ..." },
    { "role": "assistant", "content": "Answer text ..." }
    /* repeat n times */
  ]
}
```

---

### 6  Extension Points (Roadmap)

| Phase | New Role    | Responsibility                                    | Key Additions                     |
| ----- | ----------- | ------------------------------------------------- | --------------------------------- |
| 2     | Critic      | Score Q\&A pairs, flag low quality.               | `critique-ch`, heuristic metrics. |
| 3     | Moderator   | Orchestrate multi-aspect flow, enforce coherence. | state machine, topic stack.       |
| 4     | Context Mgr | Entity and memory tracking across sessions.       | vector store or in-memory DB.     |

---

### 7  Acceptance Criteria

* **AC-1**: Running `clj -M:run article.md` produces a `.jsonl` file with ≥ 1 aspect section and passes JSON schema validation.
* **AC-2**: For a test article, at least 80 % of answers echo factual content verbatim or with correct paraphrase.
* **AC-3**: End-to-end runtime < 60 s for a 2 500-word article on a standard workstation.

---

### 8  Reference Material

The assistant may consult *Approaches for "Ask-and-Answer" Model Workflows* (uploaded) for:

* Prompt-engineering patterns
* Tool comparisons (LangFlow vs n8n)
* Best practices in multi-agent QA pipelines

---

### 9  Deliverables

1. Clojure source code + `deps.edn`
2. Sample JSONL output for `sample-article.md`
3. README with setup and execution instructions
4. Test suite (`clojure.test` or Kaocha)

---

*Follow this specification verbatim. Use idiomatic, functional Clojure, keep modules small, and document public vars with docstrings.*
