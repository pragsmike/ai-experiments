# QAT: Question-Answer Transcript Generator

QAT is a multi-agent system built in Clojure to automatically generate high-quality, factually-grounded conversational datasets. It uses a Retrieval-Augmented Generation (RAG) workflow to create rich dialogues about a corpus of documents, which can then be used to fine-tune Large Language Models (LLMs).

## Features
- **Multi-Agent Workflow:** Utilizes specialized agents for Questioning, Answering, Reflecting, and Critiquing.
- **Retrieval-Augmented Generation (RAG):** Answers are generated based on context retrieved from a local document corpus, minimizing hallucination.
- **Automated Quality Scoring:** A `Critic` agent evaluates every answer for factual grounding, embedding a quality score directly into the dataset.
- **Multi-Aspect Conversation:** Generates multiple distinct conversations about a topic, each focused on a different theme (e.g., Factual Summary, Critical Evaluation).
- **Parallel Processing:** Processes conversational aspects concurrently for high throughput.
- **Structured JSONL Output:** Produces clean, structured JSONL data ready for fine-tuning pipelines.

## Setup

### 1. Prerequisites
- **Clojure:** Ensure you have the Clojure CLI tools installed. Follow the official guide: [Clojure CLI Tools Installation](https://clojure.org/guides/install_clojure).
- **LLM Proxy:** This tool is designed to work with an OpenAI-compatible API server like [LiteLLM](https://github.com/BerriAI/litellm). Set up LiteLLM or another proxy to serve your desired models.

### 2. Installation
1.  **Clone the repository:**
    ```bash
    git clone <your-repo-url>
    cd qat
    ```

2.  **Create the Knowledge Corpus:**
    The system reads its knowledge from a directory of text files.
    ```bash
    mkdir corpus
    ```
    Populate the `corpus/` directory with one or more `.txt` files containing the information you want to generate conversations about.

3.  **Configure Environment Variables:**
    The application requires an API key to authenticate with your LLM proxy. Set this key as an environment variable.

    On macOS/Linux:
    ```bash
    export LITELLM_API_KEY="your-proxy-api-key"
    ```
    On Windows (PowerShell):
    ```bash
    $env:LITELLM_API_KEY="your-proxy-api-key"
    ```

### 3. Agent & Model Configuration
The LLM models used for each agent role are defined as `def`s at the top of the `qat.core` namespace in `src/qat/core.clj`. You can edit these to match the model names configured in your LLM proxy.

```clojure
(def GUEST_MODEL "openai/gpt-3.5-turbo")    ; For asking questions
(def EXPERT_MODEL "openai/gpt-4.1-nano")      ; For generating initial answers
(def REFLECTOR_MODEL "openai/gpt-4.1-nano") ; For refining answers
(def CRITIC_MODEL "openai/gpt-4.1-nano")      ; For quality scoring
```

### 4. Usage
To run the data generation process, execute the following command from the project's root directory, pointing it to your corpus directory:

```bash
clj -M:run corpus
```

The program will:
  * Load all .txt files from the corpus directory.
  * Process them in parallel according to the defined aspects.
  * Print orderly logs for each session to the console.
  * Write the final, structured output to corpus_output.jsonl.

