(ns qat.workflow
  "Orchestrates the agent workflows for generating conversational data."
  (:require [qat.agents :as agents]
            [qat.config :as config]
            [qat.retriever :as retriever]
            [qat.json-formatter :as formatter]
            [clojure.string :as str]))

(defn- run-rag-q-and-a-session
  "The core self-correction workflow for a single aspect.
  Answer(v1) -> Critique(v1) -> Reflect(v2) -> Critique(v2)"
  [corpus sample-article-text aspect num-questions log-fn]
  (if-let [questions (agents/generate-questions config/GUEST_MODEL sample-article-text aspect num-questions log-fn)]
    (do
      (log-fn (str "\n<- Agent generated " (count questions) " questions for aspect '" aspect "'."))
      (doall
       (for [question questions]
         (do
           (log-fn (str "\n--- Processing Question: \"" question "\" ---"))
           (let [retrieved-context (retriever/search-corpus corpus question)
               ;; v1: Initial Draft
                 answer-v1 (agents/generate-answer config/EXPERT_MODEL retrieved-context question log-fn)
                 critique-v1 (agents/run-critic config/CRITIC_MODEL retrieved-context question answer-v1 log-fn #(str "   -> Invoking Critic (v1) on INITIAL answer..."))

               ;; v2: Final, Reflected Answer
               ;; NOTE: Reflector does not yet use critique-v1. This is the next step.
                 answer-v2 (agents/run-reflector config/REFLECTOR_MODEL retrieved-context question answer-v1 log-fn)
                 critique-v2 (agents/run-critic config/CRITIC_MODEL retrieved-context question answer-v2 log-fn #(str "   -> Invoking Critic (v2) on FINAL answer..."))]
             {:question question
              :initial_answer answer-v1
              :initial_critique critique-v1
              :final_answer answer-v2
              :final_critique critique-v2})))))
    (do (log-fn (str "<- Agent failed to generate questions for aspect '" aspect "'.")) [])))

(defn process-corpus
  "Processes the entire corpus by running parallel sessions for each aspect."
  [corpus sample-article-text output-file]
  (let [aspects ["Factual Summary" "Analysis of Transformation" "Critical Evaluation"]
        article-metadata {:title "SAMR Model Corpus" :source "corpus/"}
        session-futures (doall
                           (for [[index aspect] (map-indexed vector aspects)]
                             (future
                               (let [log-atom (atom [(str "\n\n=== Starting Session " (inc index) "/" (count aspects) " | Aspect: " aspect " ===")])
                                     log-fn (fn [msg] (swap! log-atom conj msg))
                                     qa-pairs (run-rag-q-and-a-session corpus sample-article-text aspect 2 log-fn)
                                     session-data {:session-id (str "samr_rag_v2_session_" (inc index))
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
          (spit output-file (str (:jsonl-string result) "\n") :append true)))))
