(ns qat.json-formatter
  "Handles formatting the conversational data into the final JSONL format."
  (:require [clojure.data.json :as json]))

(defn- format-messages [qa-pairs]
  (mapcat (fn [qa]
            [{:role "user" :content (:question qa)}
             {:role "assistant" :content (:answer qa)}])
          qa-pairs))

(defn format-session-as-jsonl
  "Takes a completed session data and formats it into a single JSONL string."
  [{:keys [session-id article-metadata session-metadata qa-pairs]}]
  (let [output-map {:conversation_id session-id
                    :article_metadata article-metadata
                    :session_metadata session-metadata
                    :messages (format-messages qa-pairs)
                    ;; We add quality metrics directly to the messages later
                    ;; This is a simplified structure for now.
                    :quality_metrics {:pairs (map #(select-keys % [:question :answer :critique]) qa-pairs)}}]
    (json/write-str output-map)))
