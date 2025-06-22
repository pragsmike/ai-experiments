(ns qat.prompts
  "Contains functions for generating standardized prompts for different agents.")

;; question-generator-prompt and answer-generator-prompt remain the same...

(defn question-generator-prompt
  "Generates the prompt for the Question Generator Agent."
  [article-text aspect num-questions] ; Added 'aspect'
  (format
   "You are an expert analyst. Your task is to generate %d insightful, open-ended questions based *only* on the provided article.
The questions should focus specifically on the theme of '%s'. Do not ask simple fact-retrieval questions.

Return the questions as a numbered list, with each question on a new line. Do not include any other text, preamble, or explanation.

--- THEME ---
%s

--- ARTICLE ---
%s
--- END ARTICLE ---

Generate %d questions now:"
   num-questions aspect aspect article-text num-questions))

(defn answer-generator-prompt
  "Generates the prompt for the Answer Generator Agent."
  [article-text question]
  (format
   "You are a factual Q&A engine. Your task is to answer the following question based *strictly and solely* on the information contained within the provided article.
Do not use any external knowledge. If the answer is not in the article, state that clearly and concisely.
Provide a comprehensive, yet concise answer.

--- ARTICLE ---
%s
--- END ARTICLE ---

--- QUESTION ---
%s
--- END QUESTION ---

Answer the question now:"
   article-text question))


;; --- NEW: Prompt for the Critic Agent (Step 4) ---
(defn critic-prompt
  "Generates the prompt for the Critic Agent to check for factual grounding."
  [article-text question answer]
  (format
   "You are a meticulous fact-checker. Your task is to determine if the provided 'ANSWER' is *fully supported* by the 'ARTICLE' text in response to the 'QUESTION'.
The answer must not contain any information, even if plausible, that is not explicitly present in the article.

Respond with ONLY a JSON object with two keys:
1. \"grounded\": A boolean value (true or false).
2. \"reasoning\": A brief, one-sentence explanation for your decision.

--- ARTICLE ---
%s
--- END ARTICLE ---

--- QUESTION ---
%s
--- END QUESTION ---

--- ANSWER ---
%s
--- END ANSWER ---

Now, provide your JSON response:"
   article-text question answer))
