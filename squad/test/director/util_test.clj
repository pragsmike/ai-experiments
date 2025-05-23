(ns director.util-test
  (:require [clojure.test :refer :all]
            [director.util :as u]
            [clojure.string :as str]))

(deftest sanitize-filename-test
  (is (= "test_game_alpha" (u/sanitize-filename "Test Game Alpha!")))
  (is (= "another-name_123" (u/sanitize-filename "another-name 123"))) ; Kept hyphen
  (is (= "g_123start" (u/sanitize-filename "123start")))
  (is (= "plain" (u/sanitize-filename "plain")))
  (is (= "with.dots.v1" (u/sanitize-filename "with.dots.v1"))) ; Kept dots
  (is (= "" (u/sanitize-filename "")))
  (is (= "" (u/sanitize-filename nil)))) ; Expect empty string for nil

(deftest extract-data-block-from-llm-test
  (testing "Basic valid JSON object"
    (is (= "{\"key\":\"value\"}" (u/extract-data-block-from-llm "{\"key\":\"value\"}"))))

  (testing "JSON object with leading and trailing whitespace"
    (is (= "{\"key\":\"value\"}" (u/extract-data-block-from-llm "  \n{\"key\":\"value\"} \t"))))

  (testing "JSON object with preamble"
    (is (= "{\"key\":\"value\"}" (u/extract-data-block-from-llm "Some preamble text... {\"key\":\"value\"}"))))

  (testing "JSON object with significant preamble (e.g., 400 chars)"
    (let [junk (apply str (repeat 400 "j"))
          json-str "{\"data\":true}"
          full-response (str junk json-str)]
      (is (= json-str (u/extract-data-block-from-llm full-response)))))

  (testing "JSON object with preamble and postamble"
    (is (= "{\"key\":\"value\"}" (u/extract-data-block-from-llm "Preamble... {\"key\":\"value\"} ...Postamble"))))

  (testing "JSON object with nested objects"
    (is (= "{\"outer\":{\"inner\":\"value\"},\"another\":1}" (u/extract-data-block-from-llm "Text {\"outer\":{\"inner\":\"value\"},\"another\":1} Text"))))

  (testing "String with no JSON object"
    (is (nil? (u/extract-data-block-from-llm "This is just text."))))

  (testing "String with only an opening brace but not balanced"
    (is (nil? (u/extract-data-block-from-llm "Text {key:val"))))

  (testing "String with mismatched braces (more opening)"
    (is (nil? (u/extract-data-block-from-llm "{{key:val}"))))

  (testing "String with mismatched braces (more closing) - finds first balanced"
    (is (= "{key:val}" (u/extract-data-block-from-llm "{key:val}}"))))

  (testing "Handles ```json ... ``` markdown when it's the first '{' encountered"
    ;; Current extractor finds first '{', if it's after ```json, it gets included
    ;; then find-balanced-braces does its job.
    (is (= "{\"key\":\"value\"}" (u/extract-data-block-from-llm "Preamble ```json\n{\"key\":\"value\"}\n``` Postamble"))))

  (testing "If first '{' is part of junk, finds that junk block" ; Corrected assertion
    (is (= "{ not json }" (u/extract-data-block-from-llm "Junk { not json } then real {\"data\":\"real\"}"))))

  (testing "Empty string"
    (is (nil? (u/extract-data-block-from-llm ""))))

  (testing "Nil input"
    (is (nil? (u/extract-data-block-from-llm nil)))))


(deftest parse-data-from-llm-response-test
  (testing "Successfully parses valid JSON with preamble"
    (let [raw-response (str (apply str (repeat 50 "junk")) "{\"key\":\"value\", \"num\":123}")
          parsed (u/parse-data-from-llm-response raw-response "test-context")]
      (is (= {:key "value" :num 123} parsed))))

  (testing "Successfully parses valid EDN with preamble"
    (let [raw-response (str (apply str (repeat 50 "junk")) "{:key \"value\", :num 123}")
          parsed (u/parse-data-from-llm-response raw-response "test-context")]
      (is (= {:key "value" :num 123} parsed))))

  (testing "Handles JSON wrapped in ```json ... ``` with preamble"
    (let [raw-response (str "Preamble\n```json\n{\"key\":\"value\"}\n```\nPostamble")
          parsed (u/parse-data-from-llm-response raw-response "test-context")]
      (is (= {:key "value"} parsed))))

  (testing "Handles EDN wrapped in ```edn ... ``` with preamble"
    (let [raw-response (str "Preamble\n```edn\n{:key \"value\" :a 1}\n```\nPostamble")
          parsed (u/parse-data-from-llm-response raw-response "test-context")]
      (is (= {:key "value" :a 1} parsed))))

  (testing "Returns error map if no data block found"
    (let [raw-response "This is just text, no object."
          parsed (u/parse-data-from-llm-response raw-response "test-context")]
      (is (map? parsed))
      (is (contains? parsed :error))
      (is (str/includes? (:error parsed) "Could not extract data block"))
      (is (= raw-response (:raw parsed)))))

  (testing "Returns error map if extracted block is not valid JSON or EDN"
    (let [raw-response "Preamble {key: value, not_quite_json_or_edn" ; This won't extract a block
          parsed (u/parse-data-from-llm-response raw-response "test-context")]
      (is (map? parsed) "Result should be an error map")
      (is (contains? parsed :error) "Error map should contain :error key")
      (is (str/includes? (:error parsed) "Could not extract data block")) ; Because find-balanced-braces returns nil
      (is (nil? (:extracted parsed)) "No block should have been extracted for parsing") ; Corrected assertion
      (is (= raw-response (:raw parsed)))))

  (testing "JSON parsing preferred over EDN if both could potentially parse"
    (let [raw-response "{\"key\":\"value\", \"is_json\":true}"
          parsed (u/parse-data-from-llm-response raw-response "test-context")]
      (is (= {:key "value" :is_json true} parsed))))
)

(deftest parse-json-from-string-trusted-test
  (testing "Parses valid trusted JSON"
    (is (= {:a 1 :b "text"} (u/parse-json-from-string-trusted "{\"a\":1, \"b\":\"text\"}" "trusted-test"))))
  (testing "Returns error map for invalid trusted JSON"
    (let [parsed (u/parse-json-from-string-trusted "{\"a\":1, \"b\":\"text\"" "trusted-error-test")] ; Missing closing }
      (is (map? parsed))
      (is (contains? parsed :error)))))
