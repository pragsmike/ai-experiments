(ns qat.json-formatter
  "Handles formatting the conversational data into the final JSONL format."
  (:require [clojure.data.json :as json]))

(defn- format-messages [qa-pairs]
  ;; The conversation should only contain the final, polished answer.
  (mapcat (fn [qa]
            [{:role "user" :content (:question qa)}
             {:role "assistant" :content (:final_answer qa)}])
          qa-pairs))

(defn- format-quality-metrics [qa-pairs]
  ;; The quality metrics contain the full audit trail.
  {:pairs (map #(select-keys % [:question :initial_answer :initial_critique :final_answer :final_critique]) qa-pairs)})

(defn format-session-as-jsonl
  "Takes a completed session data and formats it into a single JSONL string."
  [{:keys [session-id article-metadata session-metadata qa-pairs]}]
  (let [output-map {:conversation_id session-id
                    :article_metadata article-metadata
                    :session_metadata session-metadata
                    :messages (format-messages qa-pairs)
                    :quality_metrics (format-quality-metrics qa-pairs)}]
    (json/write-str output-map)))
