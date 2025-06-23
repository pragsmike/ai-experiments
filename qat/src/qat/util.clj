(ns qat.util
  "General-purpose utility functions."
  (:require [clojure.string :as str]))

(defn parse-questions
  "Parses a raw string of numbered questions into a clean sequence of strings."
  [raw-response]
  (when (and raw-response (not (str/starts-with? raw-response "{")))
    (->> (str/split-lines raw-response)
         (map str/trim)
         (remove str/blank?)
         ;; Remove leading numbers like "1.", "1)", etc.
         (map #(str/replace % #"^\d+[\.\)]\s*" "")))))
