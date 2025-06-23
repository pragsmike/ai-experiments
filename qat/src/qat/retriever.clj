(ns qat.retriever
  "Handles loading and searching a corpus of documents."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(defn- chunk-text
  "Splits a document into paragraphs (chunks)."
  [text]
  (clojure.string/split text #"\n\s*\n"))

(defn load-corpus
  "Loads all .txt files from a directory into a list of chunks with metadata."
  [dir-path]
  (let [dir (clojure.java.io/file dir-path)]
    (if (.isDirectory dir)
      (->> (file-seq dir)
           (filter #(.isFile %))
           (filter #(clojure.string/ends-with? (.getName %) ".txt"))
           (mapcat (fn [file]
                     (let [content (slurp file)
                           chunks (chunk-text content)]
                       (map-indexed (fn [i chunk]
                                      {:source (.getName file)
                                       :chunk-id i
                                       :text chunk})
                                    chunks))))
           (vec))
      (println (str "ERROR: Corpus directory not found: " dir-path)))))

(defn search-corpus
  "Performs a simple keyword search over the corpus chunks."
  [corpus query]
  (let [query-words (set (-> query
                             clojure.string/lower-case
                             (clojure.string/split #"\s+")))]
    (->> corpus
         (filter (fn [chunk]
                   (let [chunk-words (set (-> (:text chunk)
                                              clojure.string/lower-case
                                              (clojure.string/split #"\s+")))]
                     (some chunk-words query-words))))
         (map :text)
         (clojure.string/join "\n---\n"))))
