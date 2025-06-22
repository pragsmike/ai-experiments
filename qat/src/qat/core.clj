(ns qat.core
  (:require [qat.llm-interface :as llm]
            [qat.prompts :as prompts]
            [qat.json-formatter :as formatter]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json])
  (:gen-class))

;; --- Agent Definitions (No changes) ---
(defn- parse-questions [raw-response]
  (when-not (str/starts-with? raw-response "{")
    (->> (str/split-lines raw-response)
         (map str/trim)
         (remove str/blank?)
         (map #(str/replace % #"^\d+[\.\)]\s*" "")))))

(defn generate-questions [model-name article-text aspect num-questions]
  (println (str "-> Invoking Question Generator Agent for aspect: " aspect))
  (let [prompt (prompts/question-generator-prompt article-text aspect num-questions)
        raw-response (llm/call-model model-name prompt)]
    (parse-questions raw-response)))

(defn generate-answer [model-name article-text question]
  (println (str " -> Invoking Answer Agent for question: \"" (subs question 0 (min 50 (count question))) "...\""))
  (let [prompt (prompts/answer-generator-prompt article-text question)]
    (llm/call-model model-name prompt)))

(defn run-critic [model-name article-text question answer]
  (println "  -> Invoking Critic Agent...")
  (let [prompt (prompts/critic-prompt article-text question answer)
        raw-response (llm/call-model model-name prompt)]
    (try
      (json/read-str raw-response :key-fn keyword)
      (catch Exception e
        {:grounded :error :reasoning (str "Failed to parse critic JSON: " (.getMessage e))}))))

;; --- Core Workflow (with resilience) ---
(defn run-q-and-a-session
  [q-model a-model c-model article-text aspect num-questions]
  (if-let [questions (generate-questions q-model article-text aspect num-questions)]
    (do
      (println (str "\n<- Agent generated " (count questions) " questions for aspect '" aspect "'."))
      (doall
       (for [question questions]
         (if-let [answer (and (not (str/blank? question)) (generate-answer a-model article-text question))]
           (let [critique (run-critic c-model article-text question answer)]
             {:question question :answer answer :critique critique})
           {:question question :answer "ERROR: Failed to generate answer." :critique {:grounded false :reasoning "Upstream failure."}}))))
    (do
      (println (str "<- Agent failed to generate questions for aspect '" aspect "'."))
      []))) ;; Return empty list on failure

;; --- Main execution block for testing ---
(def GUEST_MODEL "openai/gpt-3.5-turbo")
(def EXPERT_MODEL "openai/gpt-4.1-nano")
(def CRITIC_MODEL "openai/gpt-4.1-nano")

(defn process-article
  "Main processing logic for a single article."
  [article-text output-file]
  (let [aspects ["Factual Summary" "Analysis of Transformation" "Critical Evaluation"]
        article-metadata {:title "SAMR Model Overview" :source "sample-text"}]

    ;; --- NEW: Parallel processing with futures ---
    (let [session-futures (doall
                           (for [[index aspect] (map-indexed vector aspects)]
                             (future
                               (println (str "\n\n=== Starting Session " (inc index) "/" (count aspects) " | Aspect: " aspect " ==="))
                               (let [qa-pairs (run-q-and-a-session GUEST_MODEL EXPERT_MODEL CRITIC_MODEL article-text aspect 2)
                                     session-data {:session-id (str "samr_v1_session_" (inc index))
                                                   :article-metadata article-metadata
                                                   :session-metadata {:focus_aspect aspect}
                                                   :qa-pairs qa-pairs}]
                                 (formatter/format-session-as-jsonl session-data)))))]

      ;; --- Collect results and write to file ---
      (doseq [f session-futures]
        (let [jsonl-string @f]
          (println "\n--- Writing session to file ---")
          (println jsonl-string)
          (spit output-file (str jsonl-string "\n") :append true))))))

(defn -main
  "Main entry point. Reads article from file specified in command-line."
  [& args]
  (llm/pre-flight-checks)
  (if-let [input-file (first args)]
    (if (.exists (clojure.java.io/file input-file))
      (let [article-text (slurp input-file)
            output-file (str/replace input-file #"\.[^.]+$" ".jsonl")]
        (println (str "--- Processing " input-file " -> " output-file " ---"))
        (spit output-file "" :append false) ; Clear output file
        (process-article article-text output-file)
        (println (str "\n\n--- Execution Complete. Output written to " output-file " ---")))
      (println (str "ERROR: Input file not found: " input-file)))
    (println "Usage: clj -M:run <path/to/article.txt>")))
