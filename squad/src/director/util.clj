(ns director.util
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.edn :as edn]))

(defn sanitize-filename [name]
  (-> (str name) ; Ensure it's a string
      str/lower-case
      (str/replace #"[^a-z0-9\-_.]+" "_")
      (str/replace #"_+" "_")
      (cond->> (and (pos? (count name)) (Character/isDigit (.charAt name 0))) (str "g_"))
      (str/replace #"^[_.]+|[_.]+$" "")))

;; For parsing JSON from trusted sources (e.g., files we wrote)
(defn parse-json-from-string-trusted [json-str error-context]
  (try
    (json/read-str json-str :key-fn keyword)
    (catch Exception e
      (println (str "Error parsing TRUSTED JSON string for " error-context ": " (.getMessage e) "\nString was: " json-str))
      {:error (str "Trusted JSON parsing error: " (.getMessage e)) :raw json-str})))

(defn- find-balanced-braces [s start-offset open-brace close-brace]
  (loop [idx (inc start-offset)
         depth 1
         content-chars [(nth s start-offset)]]
    (if (or (>= idx (count s)) (zero? depth))
      (if (zero? depth)
        (str/join content-chars)
        nil) ; Unbalanced
      (let [char (nth s idx)]
        (recur (inc idx)
               (cond
                 (= char open-brace) (inc depth)
                 (= char close-brace) (dec depth)
                 :else depth)
               (conj content-chars char))))))

(defn extract-data-block-from-llm [s]
  (if (nil? s)
    nil
    (let [s-after-backtick-removal (let [match (re-find #"(?is)^\s*```(?:json|edn)?\s*(\{.*?\})\s*```\s*$" s)]
                                     (if match
                                       (second match)
                                       s))
          start-brace (.indexOf s-after-backtick-removal "{")]

      (if (neg? start-brace)
        (do
          (when-not (str/blank? s)
            (println "DEBUG (Extractor): No opening brace '{' found in LLM response (first 100 chars): " (subs s 0 (min (count s) 100)) "..."))
          nil)
        (find-balanced-braces s-after-backtick-removal start-brace \{ \})))))


(defn parse-data-from-llm-response [str-raw error-context]
  (when-not (str/blank? str-raw)
    (let [extracted-str (extract-data-block-from-llm str-raw)]
      (if (str/blank? extracted-str)
        (do
          (println (str "Error: Could not extract a data block (JSON/EDN) from LLM for " error-context "."))
          (println (str "Original LLM string was (first 200 chars): " (subs str-raw 0 (min (count str-raw) 200)) "..."))
          {:error "Could not extract data block from LLM" :raw str-raw})
        (or
         ;; Try parsing as JSON first
         (try
           (let [parsed (json/read-str extracted-str :key-fn keyword)]
             (println (str "Successfully parsed as JSON for " error-context))
             parsed)
           (catch Exception json-e
             (println (str "INFO: Failed to parse extracted block as JSON for " error-context ". Error: " (.getMessage json-e) ". Will try EDN."))
             nil))

         ;; Try parsing as EDN if JSON failed
         (try
           ;; SIMPLIFIED EDN PARSING CALL - no explicit :readers map
           (let [parsed (edn/read-string extracted-str)] ; <--- CORRECTED LINE
             (println (str "Successfully parsed as EDN for " error-context))
             parsed)
           (catch Exception edn-e
             (println (str "Error: Failed to parse extracted block as JSON or EDN for " error-context "."))
             (println (str "JSON error was handled. EDN parsing error: " (.getMessage edn-e)))
             (println (str "Extracted string was: " extracted-str))
             (println (str "Original LLM string was (first 200 chars): " (subs str-raw 0 (min (count str-raw) 200)) "..."))
             {:error (str "Data parsing error (tried JSON then EDN): EDN error: " (.getMessage edn-e))
              :extracted extracted-str :raw str-raw})))))))
