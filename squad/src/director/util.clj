(ns director.util
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.edn :as edn]))

(defn sanitize-filename [name]
  (-> (str name)
      str/lower-case
      (str/replace #"[^a-z0-9\-_.]+" "_")
      (str/replace #"_+" "_")
      (cond->> (and (pos? (count name)) (Character/isDigit (.charAt name 0))) (str "g_"))
      (str/replace #"^[_.]+|[_.]+$" "")))

(defn parse-json-from-string-trusted [json-str error-context]
  (try
    (json/read-str json-str :key-fn keyword)
    (catch Exception e
      (println (str "Error parsing TRUSTED JSON string for " error-context ": " (.getMessage e) "\nString was: " json-str))
      {:error (str "Trusted JSON parsing error: " (.getMessage e)) :raw json-str})))

(defn- find-balanced-braces [s start-offset open-brace close-brace]
  (loop [idx (inc start-offset) ; Start searching one char after the initial open-brace
         depth 1
         ;; Start accumulating from the open-brace itself
         json-chars [(nth s start-offset)]]
    (if (or (>= idx (count s)) ; Reached end of string
            (and (zero? depth) (< 0 (count json-chars)))) ; Balanced and collected something
      (if (and (zero? depth) (< 0 (count json-chars)))
        (str/join json-chars)
        nil) ; Unbalanced or empty (should not happen if start-offset is valid '{')
      (let [char (nth s idx)]
        (recur (inc idx)
               (cond
                 (= char open-brace) (inc depth)
                 (= char close-brace) (dec depth)
                 :else depth)
               (conj json-chars char))))))

;; SIMPLIFIED: Skip everything up to the first '{', then find its balanced '}'
(defn extract-data-block-from-llm [s]
  (if (nil? s)
    nil
    (let [start-brace (.indexOf s "{")] ; Find the VERY FIRST '{'
      (if (neg? start-brace)
        (do
          (when-not (str/blank? s)
            (println "DEBUG (Extractor Simplified): No opening brace '{' found anywhere in LLM response (first 100 chars): " (subs s 0 (min (count s) 100)) "..."))
          nil)
        ;; Found an opening brace, now find its matching closing brace from this point
        (let [block (find-balanced-braces s start-brace \{ \})]
          ;; Optional: If the extracted block is wrapped in triple backticks, strip them.
          ;; This is only if backticks are *around the found block*, not handled before.
          (if block
            (let [match (re-find #"(?is)^\s*```(?:json|edn)?\s*(.*?)\s*```\s*$" block)] ; Check if the block itself is wrapped
              (if match (second match) block))
            block))))))


(defn parse-data-from-llm-response [str-raw error-context]
  (when-not (str/blank? str-raw)
    (let [extracted-str (extract-data-block-from-llm str-raw)]
      (if (str/blank? extracted-str)
        (do
          (println (str "Error: Could not extract a data block (JSON/EDN) from LLM for " error-context "."))
          (println (str "Original LLM string was (first 200 chars): " (subs str-raw 0 (min (count str-raw) 200)) "..."))
          {:error "Could not extract data block from LLM" :raw str-raw})
        (or
         (try
           (let [parsed (json/read-str extracted-str :key-fn keyword)]
             (println (str "Successfully parsed as JSON for " error-context))
             parsed)
           (catch Exception json-e
             (println (str "INFO: Failed to parse extracted block as JSON for " error-context ". Error: " (.getMessage json-e) ". Will try EDN."))
             nil))
         (try
           (let [parsed (edn/read-string extracted-str)]
             (println (str "Successfully parsed as EDN for " error-context))
             parsed)
           (catch Exception edn-e
             (println (str "Error: Failed to parse extracted block as JSON or EDN for " error-context "."))
             (println (str "JSON error was handled. EDN parsing error: " (.getMessage edn-e)))
             (println (str "Extracted string was: " extracted-str))
             (println (str "Original LLM string was (first 200 chars): " (subs str-raw 0 (min (count str-raw) 200)) "..."))
             {:error (str "Data parsing error (tried JSON then EDN): EDN error: " (.getMessage edn-e))
              :extracted extracted-str :raw str-raw})))))))
