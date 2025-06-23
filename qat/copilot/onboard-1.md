# Onboarding Guide for the QAT Project AI Assistant

Welcome to the QAT project. You are my successor. The `DESIGN.md` and `README.md` documents contain the formal specifications. This document contains the informal, hard-won institutional knowledge from our development session. Read this to understand not just *what* the system does, but *why* it is designed the way it is and where it needs to go next.

### The Project's True Goal: Building an AI Auditor

Our stated goal is to generate fine-tuning data. But the *real*, more profound goal we discovered is to **build an AI system that can audit another AI system.** The most valuable part of this project is not the Q&A pairs themselves, but the `critique` object attached to them.

We learned that even the best LLMs will subtly hallucinate or make logical leaps to be more "helpful." Our `Critic` agent, when properly prompted, is ruthless at catching this. The `{"grounded": false}` output is not a bug—it is the system's most important feature. It is a verifiable, machine-generated flag that tells us, "This piece of data is untrustworthy." Our ultimate goal is to generate data that is 100% `{"grounded": true}`.

### The Immediate Next Step: The Finalizer Agent & The Self-Correction Loop

The system currently stops after the `Critique`. The next and final step of this development phase is to close the loop and enable self-correction.

**The New Workflow:**
`Question -> Retrieve -> Answer(v1) -> Critique(v1) -> Finalize(v2) -> Critique(v2)`

1.  **Finalizer Agent:** Create a new agent role, `Finalizer`.
2.  **New Prompt:** The `Finalizer` will receive the question, context, the v1 answer, *and the v1 critique*. Its prompt should be something like:
    > "You are a final editor. Rewrite the 'INITIAL DRAFT' to fully address the 'CRITICISM'. Ensure the 'FINAL ANSWER' is 100% grounded in the provided 'RETRIEVED CONTEXT' and removes any information the critique identified as un-grounded."
3.  **Final Verification:** After the `Finalizer` produces its answer, run the `Critic` one last time. This is our final quality check. Only transcripts where the *final* critique is `{"grounded": true}` should be considered "gold standard" data.

### Hard-Won Experience (What We Learned That Worked)

1.  **RAG is Non-Negotiable:** The single-article approach was a dead end. It led to context exhaustion and repetitive, useless "I don't know" answers. The RAG architecture, which searches a corpus, is the only viable path.
2.  **The Model Hierarchy is Crucial:** The most difficult tasks are answering and critiquing. **Always assign your best, most capable LLM to the `EXPERT_MODEL` and `CRITIC_MODEL` roles.** Using a weaker model for these roles will re-introduce the hallucination and obedience problems we worked so hard to solve.
3.  **The Strict Critic is Your Best Friend:** A lenient critic is useless. Our strict, pedantic `Critic` agent is the core of our quality control. Trust its judgment. When it flags an answer as `false`, it's almost always right.
4.  **Isolating Side Effects Saved Us:** Separating `llm-interface` and using a `log-fn` instead of direct `println` calls was essential for taming concurrency and making the system testable and maintainable.

### My Mistakes: Dead Ends to Avoid

I made several errors during our session. Do not repeat them.

1.  **Initial Misunderstanding of Context:** I initially focused too much on a single article. **Dead End:** Do not try to solve the "sparse context" problem by tweaking prompts. It's an input problem, not a prompt problem. The solution was architectural (RAG), not clever wording.
2.  **Ignoring Concurrency Side Effects:** My first implementation of parallel processing with `future`s resulted in chaotic, interleaved logging. I had forgotten that `println` is not thread-safe in terms of ordering. **Dead End:** Do not try to "fix" this with delays or other hacks. The correct solution was to stop threads from printing directly and instead collect their logs in a thread-safe container (the `atom`) to be printed sequentially by the main thread.
3.  **Jumping to a Brute-Force Solution:** When the program hung, my first suggestion was `(System/exit 0)`. This works, but it's a sledgehammer. The user correctly identified that `(shutdown-agents)` is the idiomatic, graceful way to solve this by managing the agent thread pool directly. **Lesson:** Always prefer the graceful, idiomatic solution over the brute-force one.
4.  **Not Clarifying Before Acting:** At one point, you reported a missing require and I responded by changing the requires *and* the code that used them. This was an overreach. **Lesson:** Listen to the user's report precisely. If a `require` is missing, add it. If one is superfluous, remove it. Don't refactor code unless that is the explicit request.

Your job is to build on this foundation. Implement the Finalizer agent, perfect the self-correction loop, and continue producing the highest quality, verifiable training data possible. Good luck.
