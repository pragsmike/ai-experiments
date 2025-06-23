(ns qat.agents
  "Defines the individual AI agent functions."
  (:require [qat.llm-interface :as llm]
            [qat.prompts :as prompts]
            [qat.util :as util]
            [clojure.data.json :as json]))

(defn generate-questions [model-name sample-article-text aspect num-questions log-fn]
  (log-fn (str "-> Invoking Question Generator Agent for aspect: " aspect))
  (let [prompt (prompts/question-generator-prompt sample-article-text aspect num-questions)
        raw-response (llm/call-model model-name prompt)]
    (util/parse-questions raw-response)))

(defn generate-answer [model-name retrieved-context question log-fn]
  (log-fn (str " -> Invoking Answer Agent for question: \"" (subs question 0 (min 50 (count question))) "...\""))
  (let [prompt (prompts/answer-generator-prompt retrieved-context question)]
    (llm/call-model model-name prompt)))

(defn run-finalizer [model-name retrieved-context question initial-answer initial-critique log-fn]
  (log-fn "  -> Invoking Finalizer Agent to correct the answer...")
  (let [prompt (prompts/finalizer-prompt retrieved-context question initial-answer initial-critique)]
    (llm/call-model model-name prompt)))

(defn run-critic [model-name retrieved-context question final-answer log-fn & [log-message]]
  (log-fn (or log-message "   -> Invoking Critic Agent..."))
  (let [prompt (prompts/critic-prompt retrieved-context question final-answer)
        raw-response (llm/call-model model-name prompt)]
    (try (json/read-str raw-response :key-fn keyword)
         (catch Exception _ {:grounded :error :reasoning "Failed to parse critic JSON"}))))
