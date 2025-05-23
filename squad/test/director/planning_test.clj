(ns director.planning-test
  (:require [clojure.test :refer :all]
            [director.planning :as planning]
            [director.persistence :as p] ; For referring to original fns to be mocked
            [director.util :as u]))     ; For referring to original fns to be mocked

;; --- Mock Data and State ---

(def ^:private mock-planner-model-name "mock-planner-v1")
(def ^:private sample-prompt-filepath "dummy/prompts/game_of_tests_prompt.txt")
(def ^:private sample-prompt-text "Design a game about testing.")

(def ^:private valid-llm-planner-output-map
  {:game_title "Game of Tests"
   :game_id "got_v1"
   :initial_game_state {:desc "Initial state for testing game" :next_player_to_act "TestPlayer"}
   :player_instructions {:TestPlayer "Test a lot."}})

(def ^:private valid-llm-planner-output-json-str
  (clojure.data.json/write-str valid-llm-planner-output-map))

(def ^:private llm-output-missing-keys-map
  {:game_title "Incomplete Game"
   :player_instructions {:P "instr"}})

(def ^:private llm-output-missing-keys-json-str
  (clojure.data.json/write-str llm-output-missing-keys-map))


;; --- Test Fixture for Mocks ---
(defonce ^:private mock-calls (atom {}))

(defn- reset-mock-calls! [] (reset! mock-calls {}))
(defn- record-mock-call! [fn-kw & args]
  (swap! mock-calls update fn-kw (fnil conj []) args))
(defn- get-mock-calls [fn-kw] (get @mock-calls fn-kw []))

;; CORRECTED set-mock-config!
(defn- set-mock-config! [key value] ; Takes a simple key, not a path
  (swap! mock-calls assoc-in [:configs key] value))

;; Mock implementations
(defn mock-load-planner-prompt-text [filepath]
  (record-mock-call! :load-planner-prompt-text filepath)
  (if (= filepath sample-prompt-filepath)
    sample-prompt-text
    nil))

(defn mock-design-exists? [base-dir prompt-filepath]
  (record-mock-call! :design-exists? base-dir prompt-filepath)
  (let [exists-val (get-in @mock-calls [:configs :design-exists?] false)] ; Now accesses :design-exists? correctly
    (println (str "DEBUG: mock-design-exists? called for prompt '" prompt-filepath
                  "'. Configured value for :design-exists?: " exists-val
                  ". Current @mock-calls :configs key: " (:configs @mock-calls)))
    exists-val))

(defn mock-load-design-for-prompt [base-dir prompt-filepath]
  (record-mock-call! :load-design-for-prompt base-dir prompt-filepath)
  (if (get-in @mock-calls [:configs :design-exists?] false) ; Correct access
    (let [loaded-data (get-in @mock-calls [:configs :loaded-design-data])] ; Correct access
      (if (:error loaded-data)
        (do (println "DEBUG: mock-load-design-for-prompt returning configured error for" prompt-filepath) nil)
        (do (println "DEBUG: mock-load-design-for-prompt returning configured/default success data for" prompt-filepath)
            (or loaded-data
                (assoc valid-llm-planner-output-map
                       :loaded_from_file true
                       :planner_model "loaded-mock-planner"
                       :game_design_dir (p/prompt-filepath->design-dir-file base-dir prompt-filepath))))))
    (do (println "DEBUG: mock-load-design-for-prompt not returning data as design-exists? is false for" prompt-filepath)
        nil)))

(defn mock-save-planner-output! [base-dir planner-output planner-model-cfg]
  (record-mock-call! :save-planner-output! base-dir planner-output planner-model-cfg)
  (get-in @mock-calls [:configs :save-succeeds?] true)) ; Correct access

(defn mock-call-model-fn [model-name prompt-text _config-arg-ignored]
  (record-mock-call! :call-model-fn model-name prompt-text)
  (if (= model-name mock-planner-model-name)
    (get-in @mock-calls [:configs :llm-response] valid-llm-planner-output-json-str) ; Correct access
    "ERROR: Unexpected model called in mock"))

(defn with-planning-mocks [f]
  (reset-mock-calls!)
  (with-redefs [p/load-planner-prompt-text mock-load-planner-prompt-text
                p/design-exists? mock-design-exists?
                p/load-design-for-prompt mock-load-design-for-prompt
                p/save-planner-output! mock-save-planner-output!]
    (f)))

(use-fixtures :each with-planning-mocks)

;; --- Tests ---

(deftest execute-planning-phase-test-force-plan
  (testing "Force plan: generates and saves new design"
    (set-mock-config! :llm-response valid-llm-planner-output-json-str)
    (set-mock-config! :save-succeeds? true)

    (let [result (planning/execute-planning-phase "dummy-base-dir"
                                                  sample-prompt-filepath
                                                  true ; force-plan?
                                                  mock-call-model-fn
                                                  mock-planner-model-name)]
      (is (map? result) "Result should be a map")
      (is (= (:game_id valid-llm-planner-output-map) (:game_id result)) "Game ID should match")
      (is (= mock-planner-model-name (:planner_model result)) "Planner model should be set in result")
      (is (= false (:loaded_from_file result)) "Should be marked as not loaded from file")

      (is (= 1 (count (get-mock-calls :load-planner-prompt-text))) "Prompt should be loaded once")
      (is (= 1 (count (get-mock-calls :call-model-fn))) "LLM should be called once")
      (is (= mock-planner-model-name (first (first (get-mock-calls :call-model-fn)))) "Correct planner model called")
      (is (= sample-prompt-text (second (first (get-mock-calls :call-model-fn)))) "Correct prompt text sent to LLM")
      (is (= 1 (count (get-mock-calls :save-planner-output!))) "Save should be called once")
      (let [saved-data (second (first (get-mock-calls :save-planner-output!)))]
        (is (= (:game_id valid-llm-planner-output-map) (:game_id saved-data)))
        (is (= mock-planner-model-name (:planner_model saved-data))))
      (is (empty? (get-mock-calls :design-exists?)) "design-exists? should not be called when forcing plan")
      (is (empty? (get-mock-calls :load-design-for-prompt)) "load-design should not be called when forcing plan"))))

(deftest execute-planning-phase-test-force-plan-llm-error
  (testing "Force plan: LLM returns malformed JSON"
    (set-mock-config! :llm-response "this is not json")
    (let [result (planning/execute-planning-phase "dummy-base-dir"
                                                  sample-prompt-filepath
                                                  true ; force-plan?
                                                  mock-call-model-fn
                                                  mock-planner-model-name)]
      (is (nil? result) "Result should be nil on LLM error/parsing error")
      (is (= 1 (count (get-mock-calls :load-planner-prompt-text))))
      (is (= 1 (count (get-mock-calls :call-model-fn))))
      (is (empty? (get-mock-calls :save-planner-output!)) "Save should not be called on error"))))

(deftest execute-planning-phase-test-force-plan-llm-incomplete-data
  (testing "Force plan: LLM returns JSON with missing essential keys"
    (set-mock-config! :llm-response llm-output-missing-keys-json-str)
    (let [result (planning/execute-planning-phase "dummy-base-dir"
                                                  sample-prompt-filepath
                                                  true ; force-plan?
                                                  mock-call-model-fn
                                                  mock-planner-model-name)]
      (is (nil? result) "Result should be nil if planner output is incomplete")
      (is (= 1 (count (get-mock-calls :call-model-fn))))
      (is (empty? (get-mock-calls :save-planner-output!)) "Save should not be called"))))


(deftest execute-planning-phase-test-no-force-design-exists
  (testing "No force plan, design exists: loads existing design"
    (set-mock-config! :design-exists? true)
    (let [expected-loaded-design (assoc valid-llm-planner-output-map
                                        :loaded_from_file true
                                        :planner_model "loaded-mock-planner-model"
                                        :game_design_dir (p/prompt-filepath->design-dir-file "dummy-base-dir" sample-prompt-filepath))]
      (set-mock-config! :loaded-design-data expected-loaded-design)

      (let [result (planning/execute-planning-phase "dummy-base-dir"
                                                    sample-prompt-filepath
                                                    false ; force-plan?
                                                    mock-call-model-fn
                                                    mock-planner-model-name)]
        (is (map? result) "Result should be a map")
        (is (:loaded_from_file result) "Should be marked as loaded from file")
        (is (= (:game_id expected-loaded-design) (:game_id result)))
        (is (= (:planner_model expected-loaded-design) (:planner_model result)))
        (is (= (:game_design_dir expected-loaded-design) (:game_design_dir result)))


        (is (= 1 (count (get-mock-calls :load-planner-prompt-text))))
        (is (= 1 (count (get-mock-calls :design-exists?))))
        (is (= 1 (count (get-mock-calls :load-design-for-prompt))))
        (is (empty? (get-mock-calls :call-model-fn)) "LLM should NOT be called")
        (is (empty? (get-mock-calls :save-planner-output!)) "Save should NOT be called")))))

(deftest execute-planning-phase-test-no-force-design-not-exists
  (testing "No force plan, design does not exist: generates and saves new design"
    (set-mock-config! :design-exists? false)
    (set-mock-config! :llm-response valid-llm-planner-output-json-str)
    (set-mock-config! :save-succeeds? true)

    (let [result (planning/execute-planning-phase "dummy-base-dir"
                                                  sample-prompt-filepath
                                                  false ; force-plan?
                                                  mock-call-model-fn
                                                  mock-planner-model-name)]
      (is (map? result) "Result should be a map")
      (is (= (:game_id valid-llm-planner-output-map) (:game_id result)))
      (is (= mock-planner-model-name (:planner_model result)))
      (is (= false (:loaded_from_file result)))

      (is (= 1 (count (get-mock-calls :load-planner-prompt-text))))
      (is (= 1 (count (get-mock-calls :design-exists?))))
      (is (empty? (get-mock-calls :load-design-for-prompt)) "Load should not be called if design doesn't exist")
      (is (= 1 (count (get-mock-calls :call-model-fn))))
      (is (= 1 (count (get-mock-calls :save-planner-output!)))))))

(deftest execute-planning-phase-test-no-force-load-fails-then-generates
  (testing "No force plan, design exists but load fails: generates and saves new design"
    (set-mock-config! :design-exists? true)
    (set-mock-config! :loaded-design-data {:error "Simulated load failure from mock"})
    (set-mock-config! :llm-response valid-llm-planner-output-json-str)
    (set-mock-config! :save-succeeds? true)

    (let [result (planning/execute-planning-phase "dummy-base-dir"
                                                  sample-prompt-filepath
                                                  false ; force-plan?
                                                  mock-call-model-fn
                                                  mock-planner-model-name)]
      (is (map? result) "Result should be a map from generation")
      (is (= false (:loaded_from_file result)) "Should not be marked as loaded if load failed and regenerated")
      (is (= (:game_id valid-llm-planner-output-map) (:game_id result)))
      (is (= mock-planner-model-name (:planner_model result)))

      (is (= 1 (count (get-mock-calls :load-planner-prompt-text))))
      (is (= 1 (count (get-mock-calls :design-exists?))))
      (is (= 1 (count (get-mock-calls :load-design-for-prompt))) "Load attempt should be made")
      (is (= 1 (count (get-mock-calls :call-model-fn))) "LLM should be called after load fails")
      (is (= 1 (count (get-mock-calls :save-planner-output!))) "Save should be called for new design"))))


(deftest execute-planning-phase-test-prompt-load-fails
  (testing "Planner prompt file cannot be loaded"
    (let [result (planning/execute-planning-phase "dummy-base-dir"
                                                  "non_existent_prompt.txt"
                                                  false
                                                  mock-call-model-fn
                                                  mock-planner-model-name)]
      (is (nil? result) "Result should be nil if prompt cannot be loaded")
      (is (= 1 (count (get-mock-calls :load-planner-prompt-text))))
      (is (empty? (get-mock-calls :design-exists?)))
      (is (empty? (get-mock-calls :call-model-fn)))
      (is (empty? (get-mock-calls :save-planner-output!))))))
