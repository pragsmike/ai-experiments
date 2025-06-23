Based on the provided documents, particularly the `onboard-1.md` guide, here is an elucidation of the project's goal and an outline of the incremental implementation steps to achieve it.

### The Project's Goal

The stated goal is to generate structured, conversational fine-tuning data from a corpus of documents. However, the true, more profound objective is to **build an AI system that can rigorously audit another AI system.**

The core of this project is not merely the generation of questions and answers, but the creation of a verifiable, machine-generated quality assessment for each answer. The most critical component is the `Critic` agent, and its most important output is the `critique` object, specifically the `{"grounded": boolean}` flag. This flag serves as a trustworthy, automated signal indicating whether a generated piece of information is factually supported by the source material.

The ultimate aim is to perfect a self-correcting workflow that can reliably produce a "gold standard" dataset where 100% of the question-answer pairs are certified by the system itself as `{"grounded": true}`.

### Iterative Implementation Steps

The current system stops after a `Reflect -> Critique` sequence. The next and most crucial phase is to implement the self-correction loop as specified in the onboarding guide.

**Step 1: Introduce the Initial Critique**

*   **Objective:** Modify the existing workflow to generate an initial critique immediately after the first answer is drafted. This critique will become the input for the correction step.
*   **Tasks:**
    1.  In `qat.core/run-rag-q-and-a-session`, re-wire the agent sequence. After the `Answer Agent` produces `answer-v1`, immediately call the `Critic Agent` on it.
    2.  This first critique (`critique-v1`) now provides a structured assessment of what is wrong with the initial draft.

**Step 2: Implement the `Finalizer` Agent**

*   **Objective:** Replace the current `Reflector` agent with a `Finalizer` agent that performs targeted corrections based on the initial critique.
*   **Tasks:**
    1.  Create a new prompt function in `qat.prompts`, named `finalizer-prompt`. This prompt must take the `retrieved-context`, `question`, the `initial-answer` (v1), and the `critique` (v1) as input. Its instruction will be to rewrite the initial answer specifically to address the issues raised in the critique.
    2.  In `qat.core`, create a new `run-finalizer` function. This function will use the `finalizer-prompt` and call the LLM.
    3.  Replace the call to `run-reflector` in the main workflow with a call to this new `run-finalizer` function, ensuring it receives the output from Step 1. The output of this step is the `final-answer` (v2).

**Step 3: Implement the Final Verification**

*   **Objective:** Add a final quality gate to verify that the `Finalizer` agent successfully corrected the answer.
*   **Tasks:**
    1.  After the `Finalizer` produces the `final-answer` (v2), call the `Critic Agent` a *second time*.
    2.  This `critique-v2` serves as the final, definitive quality score for the question-answer pair.
    3.  Only pairs where `critique-v2` is `{"grounded": true}` should be considered "gold standard" outputs.

**Step 4: Update Data Structures and Logging**

*   **Objective:** Persist the results of the entire self-correction loop to make the output data richer and the process more transparent.
*   **Tasks:**
    1.  Modify the data structure returned by `run-rag-q-and-a-session`. Each pair should now contain the `initial_answer`, `initial_critique`, `final_answer`, and `final_critique` to provide a complete audit trail.
    2.  Update `qat.json-formatter` to correctly serialize this new, richer data structure into the `quality_metrics` object in the final JSONL output.
    3.  Enhance the console logging (`log-fn`) to clearly show the steps of the new loop (e.g., "Initial Answer -> Critique (v1) -> Finalizing -> Final Answer -> Critique (v2)").
