(ns qat.core
  (:require [qat.llm-interface :as llm]
            [qat.prompts :as prompts]
            [qat.json-formatter :as formatter]
            [qat.retriever :as retriever]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

;; --- Agent Definitions now accept a logging function ---
(defn- parse-questions [raw-response]
  (when (and raw-response (not (str/starts-with? raw-response "{")))
    (->> (str/split-lines raw-response) (map str/trim) (remove str/blank?) (map #(str/replace % #"^\d+[\.\)]\s*" "")))))

(defn generate-questions [model-name sample-article-text aspect num-questions log-fn]
  (log-fn (str "-> Invoking Question Generator Agent for aspect: " aspect))
  (let [prompt (prompts/question-generator-prompt sample-article-text aspect num-questions)
        raw-response (llm/call-model model-name prompt)]
    (parse-questions raw-response)))

(defn generate-answer [model-name retrieved-context question log-fn]
  (log-fn (str " -> Invoking Answer Agent for question: \"" (subs question 0 (min 50 (count question))) "...\""))
  (let [prompt (prompts/answer-generator-prompt retrieved-context question)]
    (llm/call-model model-name prompt)))

(defn run-critic [model-name retrieved-context question answer log-fn]
  (log-fn "  -> Invoking Critic Agent...")
  (let [prompt (prompts/critic-prompt retrieved-context question answer)
        raw-response (llm/call-model model-name prompt)]
    (try (json/read-str raw-response :key-fn keyword)
         (catch Exception _ {:grounded :error :reasoning "Failed to parse critic JSON"}))))

;; --- RAG Workflow now passes the logging function ---
(defn run-rag-q-and-a-session
  [q-model a-model c-model corpus sample-article-text aspect num-questions log-fn]
  (if-let [questions (generate-questions q-model sample-article-text aspect num-questions log-fn)]
    (do
      (log-fn (str "\n<- Agent generated " (count questions) " questions for aspect '" aspect "'."))
      (doall
       (for [question questions]
         (let [retrieved-context (retriever/search-corpus corpus question)
               answer (generate-answer a-model retrieved-context question log-fn)
               critique (run-critic c-model retrieved-context question answer log-fn)]
           {:question question :answer answer :critique critique}))))
    (do (log-fn (str "<- Agent failed to generate questions for aspect '" aspect "'.")) [])))

;; --- Main execution block ---
(def GUEST_MODEL "openai/gpt-3.5-turbo")
(def EXPERT_MODEL "openai/gpt-4.1-nano")
(def CRITIC_MODEL "openai/gpt-4.1-nano")

(defn process-corpus
  [corpus sample-article-text output-file]
  (let [aspects ["Factual Summary" "Analysis of Transformation" "Critical Evaluation"]
        article-metadata {:title "SAMR Model Corpus" :source "corpus/"}]
    (let [session-futures (doall
                           (for [[index aspect] (map-indexed vector aspects)]
                             (future
                               (let [log-atom (atom [(str "\n\n=== Starting Session " (inc index) "/" (count aspects) " | Aspect: " aspect " ===")])
                                     ;; The logging function we pass to the agents
                                     log-fn (fn [msg] (swap! log-atom conj msg))
                                     qa-pairs (run-rag-q-and-a-session GUEST_MODEL EXPERT_MODEL CRITIC_MODEL corpus sample-article-text aspect 2 log-fn)
                                     session-data {:session-id (str "samr_rag_v1_session_" (inc index))
                                                   :article-metadata article-metadata
                                                   :session-metadata {:focus_aspect aspect}
                                                   :qa-pairs qa-pairs}]
                                 {:logs @log-atom
                                  :jsonl-string (formatter/format-session-as-jsonl session-data)}))))]
      (doseq [f session-futures]
        (let [result @f]
          (println (str/join "\n" (:logs result)))
          (println "\n--- Writing session to file ---")
          (println (:jsonl-string result))
          (spit output-file (str (:jsonl-string result) "\n") :append true))))))

(defn -main
  "Main entry point. Reads corpus from a directory specified in command-line."
  [& args]
  (llm/pre-flight-checks)
  (if-let [corpus-dir (first args)]
    (if (.isDirectory (io/file corpus-dir))
      (let [corpus (retriever/load-corpus corpus-dir)
            sample-article-text (when (seq corpus) (:text (first corpus)))
            output-file (str "corpus_output.jsonl")]
        (if sample-article-text
          (do
            (println (str "--- Processing corpus in " corpus-dir " -> " output-file " ---"))
            (println (str "Loaded " (count corpus) " chunks from corpus."))
            (spit output-file "" :append false)
            (process-corpus corpus sample-article-text output-file)
            (println (str "\n\n--- Execution Complete. Output written to " output-file " ---")))
          (println (str "ERROR: No .txt files found in corpus directory: " corpus-dir))))
      (println (str "ERROR: Corpus directory not found: " corpus-dir)))
    (println "Usage: clj -M:run <path/to/corpus_directory>"))

  (shutdown-agents)
  ;; --- NEW: Explicitly shut down the JVM to prevent hanging ---
  )
