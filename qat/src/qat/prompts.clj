(ns qat.prompts
  "Contains functions for generating standardized prompts for different agents.")

(defn question-generator-prompt
  "Generates the prompt for the Question Generator Agent based on a sample article."
  [sample-article-text aspect num-questions]
  (format
   "You are an expert curriculum developer. Based on the overall themes in the provided sample article, generate %d insightful, open-ended questions about the topic of '%s'. The questions should be broad enough to require searching across multiple documents for a full answer.

Return the questions as a numbered list. Do not include any other text.

--- TOPIC THEME ---
%s

--- SAMPLE ARTICLE FOR THEME INSPIRATION ---
%s
--- END SAMPLE ARTICLE ---

Generate %d questions now:"
   num-questions aspect aspect sample-article-text num-questions))

(defn answer-generator-prompt
  "Generates the prompt for the Answer Generator Agent using retrieved context."
  [retrieved-context question]
  (format
   "You are a factual Q&A engine. Your task is to answer the 'QUESTION' based *strictly and solely* on the information contained within the 'RETRIEVED CONTEXT'.
Do not use any external knowledge. If the answer is not in the context, state that clearly and concisely.

--- RETRIEVED CONTEXT ---
%s
--- END RETRIEVED CONTEXT ---

--- QUESTION ---
%s
--- END QUESTION ---

Answer the question now:"
   retrieved-context question))

(defn critic-prompt
  "Generates the prompt for the Critic Agent using retrieved context."
  [retrieved-context question answer]
  (format
   "You are a meticulous fact-checker. Your task is to determine if the 'ANSWER' is *fully supported* by the 'RETRIEVED CONTEXT' provided. The answer must not contain any information, even if plausible, that is not explicitly present in the context.

Respond with ONLY a JSON object with two keys:
1. \"grounded\": A boolean value (true or false).
2. \"reasoning\": A brief, one-sentence explanation for your decision.

--- RETRIEVED CONTEXT ---
%s
--- END RETRIEVED CONTEXT ---

--- QUESTION ---
%s
--- END QUESTION ---

--- ANSWER ---
%s
--- END ANSWER ---

Now, provide your JSON response:"
   retrieved-context question answer))

(defn finalizer-prompt
  "Generates the prompt for the Finalizer Agent to correct an answer based on a critique."
  [retrieved-context question initial-answer initial-critique]
  (format
   "You are a final editor. Your task is to rewrite the 'INITIAL DRAFT' to fully address the 'CRITICISM'.
Ensure the 'FINAL ANSWER' is 100%% grounded in the provided 'RETRIEVED CONTEXT'.
Remove any information the critique identified as un-grounded. If the initial draft was correct but the critique found it lacking, enrich it with more detail *from the context*.
If the critique confirms the answer is not in the context, your final answer should also state that the answer cannot be found in the context.
Return only the text of the final, corrected answer.

--- RETRIEVED CONTEXT ---
%s
--- END RETRIEVED CONTEXT ---

--- QUESTION ---
%s
--- END QUESTION ---

--- INITIAL DRAFT ---
%s
--- END INITIAL DRAFT ---

--- CRITICISM ---
%s
--- END CRITICISM ---

Now, provide the FINAL ANSWER:"
   retrieved-context question initial-answer (pr-str initial-critique)))
