# QAT System Design Document

## 1. Objective
To design and implement a scalable, multi-agent system in Clojure for the automated generation of high-quality, structured conversational datasets. The primary output is a JSONL file containing QA sessions suitable for fine-tuning Large Language Models (LLMs) on domain-specific, fact-grounded dialogue.

## 2. Core Principles
The system is built on several core software design principles:
- **Separation of Concerns:** Logic is divided into distinct namespaces based on responsibility (e.g., `llm-interface`, `prompts`, `retriever`, `core` orchestration).
- **Immutability:** Data structures are treated as immutable, with transformations creating new data rather than modifying it in place.
- **Concurrency:** The system leverages Clojure's `future`s to process distinct conversational aspects in parallel, maximizing throughput.
- **Controlled Side Effects:** All external interactions (HTTP calls, file I/O, console logging) are isolated and handled explicitly, primarily in the `llm-interface` and `core`'s `-main` function.

## 3. System Architecture
The system employs a Retrieval-Augmented Generation (RAG) pipeline orchestrated by a set of specialized AI agents.

### 3.1. Components
- **Corpus:** A directory of `.txt` documents that serves as the knowledge base for the system.
- **Agent Roles:** Each agent is a Clojure function that combines a prompt-generation function with a call to the LLM interface.
  - **Question Generator:** Creates broad, thematic questions based on a sample document to guide the conversation.
  - **Retriever:** A non-LLM component that performs a keyword search against the entire corpus to find relevant context chunks for a given question.
  - **Answer Agent:** Generates an initial answer based *only* on the context provided by the Retriever.
  - **Reflector Agent:** Takes the initial answer and rewrites it for better structure, clarity, and directness, acting as an automated editor.
  - **Critic Agent:** The final quality gate. It evaluates the *refined* answer against the retrieved context to check for factual grounding and hallucination.
- **Support Namespaces:**
  - `qat.llm-interface`: Handles all HTTP communication with the LLM proxy (e.g., LiteLLM), including authentication and error handling.
  - `qat.prompts`: A centralized repository for all prompt templates. This allows for easy tuning of agent behavior without changing application logic.
  - `qat.retriever`: Manages loading the document corpus from disk and performing the search/retrieval operation.
  - `qat.json-formatter`: Assembles the final data from a session into the specified JSONL format.

### 3.2. Workflow: The R-A-R-C Loop (Retrieve, Answer, Reflect, Critique)
The core data generation process for a single question follows these steps:
1.  **Question:** The `Question Generator` produces a question.
2.  **Retrieve:** The `Retriever` searches the corpus using the question as a query and assembles a context block.
3.  **Answer (v1):** The `Answer Agent` receives the question and the retrieved context, and produces a draft answer.
4.  **Reflect (v2):** The `Reflector Agent` receives the question, context, and draft answer, and produces a refined, final answer.
5.  **Critique:** The `Critic Agent` receives the question, context, and the *final* answer, and produces a quality score (`{"grounded": boolean, "reasoning": "..."}`).

This entire sequence is then mapped over a list of questions generated for a specific "aspect" (e.g., "Critical Evaluation").

### 4. Concurrency Model
- The system processes multiple conversational "aspects" in parallel. The main `process-corpus` function maps over the list of aspects and wraps each session in a `(future ...)`.
- To prevent chaotic console output from parallel threads, logging is managed by passing a `log-fn` to the agents. Each `future` has a private `atom` that collects log messages. The main thread dereferences the futures sequentially and prints the collected logs for each session in a clean, orderly block.
- The application uses `(shutdown-agents)` to gracefully terminate the agent thread pool, ensuring the JVM exits cleanly without hanging.

### 5. Data Schema (JSONL Output)
Each line in the output file is a JSON object representing a full conversational session for one aspect.

```json
{
  "conversation_id": "samr_rag_v2_session_3",
  "article_metadata": {
    "title": "SAMR Model Corpus",
    "source": "corpus/"
  },
  "session_metadata": {
    "focus_aspect": "Critical Evaluation"
  },
  "messages": [
    {"role": "user", "content": "<Question 1>"},
    {"role": "assistant", "content": "<Final Answer 1>"},
    {"role": "user", "content": "<Question 2>"},
    {"role": "assistant", "content": "<Final Answer 2>"}
  ],
  "quality_metrics": {
    "pairs": [
      {
        "question": "<Question 1>",
        "answer": "<Final Answer 1>",
        "critique": {
          "grounded": true,
          "reasoning": "The answer is fully supported by the retrieved context."
        }
      }
    ]
  }
}
