(ns qat.workflow
  "Orchestrates the agent workflows for generating conversational data."
  (:require [qat.agents :as agents]
            [qat.config :as config]
            [qat.retriever :as retriever]
            [qat.json-formatter :as formatter]
            [clojure.string :as str]))

(defn- run-rag-q-and-a-session
  "The core R-A-R-C (Retrieve-Answer-Reflect-Critique) workflow for a single aspect."
  [corpus sample-article-text aspect num-questions log-fn]
  (if-let [questions (agents/generate-questions config/GUEST_MODEL sample-article-text aspect num-questions log-fn)]
    (do
      (log-fn (str "\n<- Agent generated " (count questions) " questions for aspect '" aspect "'."))
      (doall
       (for [question questions]
         (let [retrieved-context (retriever/search-corpus corpus question)
               answer-v1 (agents/generate-answer config/EXPERT_MODEL retrieved-context question log-fn)
               answer-v2 (agents/run-reflector config/REFLECTOR_MODEL retrieved-context question answer-v1 log-fn)
               critique (agents/run-critic config/CRITIC_MODEL retrieved-context question answer-v2 log-fn)]
           {:question question :answer answer-v2 :critique critique}))))
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
