### How Reliability Emerges from Unreliable Components

The system's core design principle is that **a single LLM, no matter how capable, cannot be fully trusted.** LLMs are prone to subtle invention and logical leaps in their attempt to be helpful. Instead of trying to perfect a single agent, this system creates reliability through an **audited, adversarial self-correction process.**

It orchestrates a conversation between specialized agents with opposing goals: an optimistic `Answer Agent` that drafts a response, and a pessimistic, pedantic `Critic Agent` that ruthlessly checks it for factual grounding. A `Finalizer` agent is then forced to reconcile these two viewpoints. By running the `Critic` one last time as a final quality gate, we don't trust any single agent's output; we trust the verifiable result of the entire workflow. The system produces a "gold standard" answer not when an agent claims it is good, but when it has survived this rigorous, multi-stage scrutiny.

---

### The Self-Correction Workflow

The generation of a single Question-Answer pair follows a four-step loop:

1.  **Initial Draft (Answer v1):** An `Answer Agent` produces a first draft of the answer based on the retrieved context. This draft is optimized for relevance and completeness.
2.  **Initial Review (Critique v1):** A `Critic Agent` immediately evaluates the draft. It produces a structured JSON object flagging whether the answer is fully grounded in the context and explains why.
3.  **Corrective Rewrite (Answer v2):** A `Finalizer Agent` receives the initial draft *and* the critique. Its sole job is to rewrite the answer to explicitly address the criticism, removing any un-grounded claims or enriching it with more evidence from the context.
4.  **Final Verification (Critique v2):** The `Critic Agent` is run a final time on the corrected answer. Only pairs that pass this final check with `{"grounded": true}` are considered trustworthy, "gold standard" data.

---

### Agent Roles and Prompt Summaries

*   **Question Generator:**
    *   **Role:** To initiate the conversation by creating a set of broad, insightful questions about the source material.
    *   **Prompt Summary:** "You are an expert curriculum developer. Based on the themes in the provided text, generate N open-ended questions that require deep understanding."

*   **Answer Agent (The Expert):**
    *   **Role:** To provide the initial, fact-based answer (v1). It is prompted to be strict and avoid making assumptions.
    *   **Prompt Summary:** "You are a factual Q&A engine. Answer the question based *strictly and solely* on the provided context. If the answer is not present, state that clearly."

*   **Critic Agent (The Fact-Checker):**
    *   **Role:** To act as the ruthless, pedantic quality gate. It performs the same function for both the initial and final review.
    *   **Prompt Summary:** "You are a meticulous fact-checker. Is the answer *fully supported* by the context? Respond ONLY with a JSON object: `{\"grounded\": boolean, \"reasoning\": \"...\"}`."

*   **Finalizer Agent (The Editor):**
    *   **Role:** To act as the final editor, producing the polished answer (v2) by resolving the conflict between the initial draft and the critique.
    *   **Prompt Summary:** "You are a final editor. Rewrite the 'INITIAL DRAFT' to fully address the 'CRITICISM'. Ensure the 'FINAL ANSWER' is 100% grounded in the provided context and removes any un-grounded information."

---

### A Typical Self-Correction Scenario

This diagram illustrates how the system corrects a common failure mode where an agent invents plausible but un-grounded information.

```
[Question]
"What are the official stages of the SAMR model?"
     |
     v
[Answer Agent] --> (Answer v1): "The official stages are Substitution, Augmentation,
                 Modification, and Redefinition, which directly impact
                 student financial aid eligibility."
     |
     v
[Critic Agent] --> (Critique v1): {
                   "grounded": false,
                   "reasoning": "The answer correctly lists the stages but adds
                                 information about 'financial aid eligibility' which
                                 is not present in the retrieved context."
                 }
     |
     v
[Finalizer Agent] --> (Answer v2): "The official stages of the SAMR model, as described
(Receives Answer v1   in the context, are Substitution, Augmentation,
 AND Critique v1)      Modification, and Redefinition."
     |
     v
[Critic Agent] --> (Critique v2): {
                   "grounded": true,
                   "reasoning": "The final answer is now fully supported by the
                                 retrieved context and contains no extra information."
                 }
     |
     v
[Outcome]
A high-quality, verified Q&A pair is written to the output file.
```
