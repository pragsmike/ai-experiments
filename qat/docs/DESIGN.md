# QAT System Design Document

## 1. Objective
To design and implement a scalable, multi-agent system in Clojure for the automated generation of high-quality, structured, and auditable conversational datasets. The primary output is a JSONL file containing fact-grounded QA sessions suitable for fine-tuning Large Language Models (LLMs) on domain-specific dialogue.

## 2. Core Principles
The system is built on several core software design principles:
- **Separation of Concerns:** Logic is divided into distinct namespaces based on responsibility (e.g., `llm-interface`, `prompts`, `retriever`, `agents`, `workflow`). This makes the system modular, testable, and easier to maintain.
- **Immutability:** Data structures are treated as immutable, with transformations creating new data rather than modifying it in place, which simplifies concurrency and reasoning about state.
- **Concurrency:** The system leverages Clojure's `future`s to process distinct conversational aspects in parallel, maximizing throughput on multi-core processors.
- **Controlled Side Effects:** All external interactions (HTTP calls, file I/O, console logging) are isolated and handled explicitly, primarily in the `llm-interface` and `core` namespaces.

## 3. System Architecture
The system employs a Retrieval-Augmented Generation (RAG) pipeline orchestrated by a set of specialized AI agents that follow a self-correction loop.

### 3.1. Components
- **Corpus:** A directory of `.txt` documents that serves as the knowledge base for the system.
- **Agent Roles:** Each agent is a Clojure function that combines a prompt-generation function with a call to the LLM interface.
  - **Question Generator:** Creates broad, thematic questions based on a sample document to guide the conversation.
  - **Answer Agent:** Generates an initial answer (v1) based *only* on the context provided by the Retriever. It is prompted for strict adherence to the source text.
  - **Critic Agent:** The system's primary quality gate. It evaluates an answer against the retrieved context to check for factual grounding and hallucination. It is used twice in the workflow.
  - **Finalizer Agent:** Acts as an editor. It receives the initial answer *and* a critique, and its sole purpose is to rewrite the answer to address the criticism, producing the final version (v2).
- **Support Namespaces:**
  - `qat.config`: Centralizes all static configuration, such as the LLM model names for each agent role.
  - `qat.util`: Contains pure, generic helper functions used across the system (e.g., response parsers).
  - `qat.agents`: Defines the core capabilities of each AI agent, encapsulating their specific logic.
  - `qat.workflow`: Orchestrates the agents into high-level pipelines, such as the main self-correction loop and the parallel processing of aspects.
  - `qat.llm-interface`: Handles all HTTP communication with the LLM proxy (e.g., LiteLLM), including authentication and error handling.
  - `qat.prompts`: A centralized repository for all prompt templates. This allows for easy tuning of agent behavior without changing application logic.
  - `qat.retriever`: Manages loading the document corpus from disk and performing the search/retrieval operation.
  - `qat.json-formatter`: Assembles the final data from a session into the specified JSONL format.

### 3.2. Workflow: The A-C-F-C Self-Correction Loop (Answer, Critique, Finalize, Critique)
The core data generation process for a single question follows a rigorous, four-step self-correction loop:
1.  **Answer (v1):** The `Answer Agent` receives the question and the retrieved context, and produces a draft answer.
2.  **Critique (v1):** The `Critic Agent` immediately evaluates the draft answer, producing a structured critique (e.g., `{"grounded": false, "reasoning": "..."}`).
3.  **Finalize (v2):** The `Finalizer Agent` receives the question, context, the v1 answer, *and the v1 critique*. Its prompt instructs it to rewrite the answer to explicitly fix the issues identified by the critique.
4.  **Critique (v2):** The `Critic Agent` is run a final time on the finalized answer. This second critique serves as the definitive quality check for the Q&A pair.

## 4. Concurrency Model
- The system processes multiple conversational "aspects" in parallel. The main `process-corpus` function in `qat.workflow` maps over the list of aspects and wraps each session in a `(future ...)`.
- To prevent chaotic console output from parallel threads, logging is managed by passing a `log-fn` to the agents. Each `future` has a private `atom` that collects log messages. The main thread dereferences the futures sequentially and prints the collected logs for each session in a clean, orderly block.
- The application uses `(shutdown-agents)` to gracefully terminate the agent thread pool, ensuring the JVM exits cleanly without hanging.

## 5. Data Schema (JSONL Output)
Each line in the output file is a JSON object representing a full conversational session for one aspect. The `quality_metrics` object contains the complete audit trail for each Q&A pair, making the data's generation process fully transparent and verifiable.

```json
{
  "conversation_id": "samr_rag_v2_session_2",
  "article_metadata": {
    "title": "SAMR Model Corpus",
    "source": "corpus/"
  },
  "session_metadata": {
    "focus_aspect": "Analysis of Transformation"
  },
  "messages": [
    {
      "role": "user",
      "content": "<Question 1>"
    },
    {
      "role": "assistant",
      "content": "<Final Answer 1>"
    }
  ],
  "quality_metrics": {
    "pairs": [
      {
        "question": "<Question 1>",
        "initial_answer": "<Initial draft answer from the Answer Agent.>",
        "initial_critique": {
          "grounded": false,
          "reasoning": "The initial answer made a claim not supported by the context."
        },
        "final_answer": "<Final, corrected answer from the Finalizer Agent.>",
        "final_critique": {
          "grounded": true,
          "reasoning": "The final answer is now fully supported by the retrieved context."
        }
      }
    ]
  }
}
```
