(ns qat.core
  (:require [qat.llm-interface :as llm]
            [qat.prompts :as prompts]
            [qat.json-formatter :as formatter]
            [clojure.string :as str]
            [clojure.pprint :as pprint]
            [clojure.data.json :as json])
  (:gen-class))


;; --- Agent Definitions ---

(defn- parse-questions [raw-response]
  (->> (str/split-lines raw-response)
       (map str/trim)
       (remove str/blank?)
       (map #(str/replace % #"^\d+[\.\)]\s*" ""))))

(defn generate-questions
  "Agent: Generates questions for a given article and aspect."
  [model-name article-text aspect num-questions]
  (println (str "-> Invoking Question Generator Agent for aspect: " aspect))
  (let [prompt (prompts/question-generator-prompt article-text aspect num-questions)
        raw-response (llm/call-model model-name prompt)]
    (parse-questions raw-response)))

(defn generate-answer
  "Agent: Answers a single question based on the article."
  [model-name article-text question]
  (println (str " -> Invoking Answer Agent for question: \"" (subs question 0 (min 50 (count question))) "...\""))
  (let [prompt (prompts/answer-generator-prompt article-text question)]
    (llm/call-model model-name prompt)))

;; --- NEW: Critic Agent (Step 4) ---
(defn run-critic
  "Agent: Critiques an answer for factual grounding."
  [model-name article-text question answer]
  (println "  -> Invoking Critic Agent...")
  (let [prompt (prompts/critic-prompt article-text question answer)
        raw-response (llm/call-model model-name prompt)]
    (try
      (json/read-str raw-response :key-fn keyword)
      (catch Exception e
        {:grounded :error :reasoning (str "Failed to parse critic JSON: " (.getMessage e))}))))

;; --- Core Workflow (Now includes the critic) ---

(defn run-q-and-a-session
  "Orchestrates a single session for one aspect: Q -> A -> C."
  [q-model a-model c-model article-text aspect num-questions]
  (let [questions (generate-questions q-model article-text aspect num-questions)]
    (println (str "\n<- Agent generated " (count questions) " questions for aspect '" aspect "'."))
    (doall
     (map (fn [question]
            (let [answer (generate-answer a-model article-text question)
                  critique (run-critic c-model article-text question answer)]
              {:question question
               :answer answer
               :critique critique}))
          questions))))

;; --- Main execution block for testing ---

(def GUEST_MODEL "groq/llama3-8b-8192")
(def EXPERT_MODEL "groq/llama3-70b-8192")
(def CRITIC_MODEL "groq/llama3-8b-8192") ; A fast model is fine for the critic's structured task

(def sample-article
  "The SAMR model, developed by Dr. Ruben Puentedura, provides a framework for integrating technology into teaching and learning. It comprises four levels: Substitution, Augmentation, Modification, and Redefinition. Substitution is when technology acts as a direct tool substitute with no functional change, like using a word processor instead of a pen. Augmentation is a direct substitute with functional improvement, such as using a spell-checker. Modification sees technology allow for significant task redesign, like collaborative online editing. Finally, Redefinition enables the creation of new tasks previously inconceivable, such as creating a multimedia documentary to share with a global audience. The goal is to move from enhancement (S, A) to transformation (M, R).")

(defn -main
  "Main entry point for running a demonstration of Steps 2, 3, and 4."
  [& args]
  (llm/pre-flight-checks)
  (println "--- Executing Steps 2, 3 & 4: Formatting, Multi-Aspect, and Critic ---")

  (let [output-file "output.jsonl"
        ;; --- NEW: Multi-Aspect Iteration (Step 3) ---
        aspects ["Factual Summary" "Analysis of Transformation"]
        article-metadata {:title "SAMR Model Overview" :source "sample-text"}]

    (spit output-file "" :append false) ; Clear the output file

    (doseq [[index aspect] (map-indexed vector aspects)]
      (println (str "\n\n=== Starting Session " (inc index) "/" (count aspects) " | Aspect: " aspect " ==="))
      (let [qa-pairs (run-q-and-a-session GUEST_MODEL EXPERT_MODEL CRITIC_MODEL sample-article aspect 2)
            session-data {:session-id (str "samr_v1_session_" (inc index))
                          :article-metadata article-metadata
                          :session-metadata {:focus_aspect aspect}
                          :qa-pairs qa-pairs}
            ;; --- NEW: JSONL Formatting (Step 2) ---
            jsonl-string (formatter/format-session-as-jsonl session-data)]

        (println "\n--- Raw session data ---")
        (pprint/pprint qa-pairs)

        (println "\n--- Formatted JSONL line ---")
        (println jsonl-string)
        (spit output-file (str jsonl-string "\n") :append true))))

  (println (str "\n\n--- Execution Complete. Output written to output.jsonl ---")))
