(ns qat.core
  (:require [qat.llm-interface :as llm]
            [qat.retriever :as retriever]
            [qat.workflow :as workflow]
            [clojure.java.io :as io])
  (:gen-class))

(defn -main
  "Main entry point. Reads corpus from a directory specified in command-line."
  [& args]
  (llm/pre-flight-checks)
  (if-let [corpus-dir (first args)]
    (if (.isDirectory (io/file corpus-dir))
      (let [corpus (retriever/load-corpus corpus-dir)
            sample-article-text (when (seq corpus) (:text (first corpus)))
            output-file (str "corpus" "_output.jsonl")]
        (if sample-article-text
          (do
            (println (str "--- Processing corpus in " corpus-dir " -> " output-file " ---"))
            (println (str "Loaded " (count corpus) " chunks from corpus."))
            (spit output-file "" :append false) ; Clear output file before starting
            (workflow/process-corpus corpus sample-article-text output-file)
            (println (str "\n\n--- Execution Complete. Output written to " output-file " ---")))
          (println (str "ERROR: No .txt files found in corpus directory: " corpus-dir))))
      (println (str "ERROR: Corpus directory not found: " corpus-dir)))
    (println "Usage: clj -M:run <path/to/corpus_directory>"))

  ;; Gracefully shut down the agent thread pool
  (shutdown-agents))
