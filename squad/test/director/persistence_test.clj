(ns director.persistence-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [director.persistence :as p]
            [director.util :as u])
  (:import [java.nio.file Files Path]
           [java.nio.file.attribute FileAttribute]))

;; --- Test Setup and Teardown ---

(def ^:dynamic *test-base-dir-path-str* nil) ; Will hold string path to temp base dir

(defn- create-temp-dir-path-str! [prefix]
  (let [temp-path (Files/createTempDirectory prefix (into-array FileAttribute []))]
    (.toString temp-path))) ; Return as string

(defn- delete-recursively! [file-path-str]
  (let [file (io/file file-path-str)]
    (when (.isDirectory file)
      (doseq [child (.listFiles file)]
        (delete-recursively! (.getAbsolutePath child))))
    (.delete file)))

(defn temp-dir-fixture [f]
  (binding [*test-base-dir-path-str* (create-temp-dir-path-str! "director-persistence-test-")]
    (println "Using temp base dir for tests:" *test-base-dir-path-str*)
    (.mkdirs (io/file *test-base-dir-path-str*)) ; Ensure base itself exists
    (f) ; Run the tests
    (println "Deleting temp base dir:" *test-base-dir-path-str*)
    (delete-recursively! *test-base-dir-path-str*)))

(use-fixtures :once temp-dir-fixture)

;; --- Sample Data ---
(def sample-planner-output
  {:game_title "Test Game Alpha"
   :game_id "test_game_alpha_v1"
   :initial_game_state {:description "A test game" :turn 1 :next_player_to_act "Player1"}
   :player_instructions {:Player1 "Instruction for P1"
                         :Player2 "Instruction for P2"}})

(def sample-planner-model-cfg "test_planner_model_v0.1")

;; --- Tests ---

(deftest get-game-design-dir-file-test
  (testing "Path construction and sanitization"
    (let [expected-path-1 (io/file *test-base-dir-path-str* "my_game_123")
          expected-path-2 (io/file *test-base-dir-path-str* "another_game_with_spaces")
          expected-path-3 (io/file *test-base-dir-path-str* "unknown_game")]
      (is (= (.getAbsolutePath expected-path-1) (.getAbsolutePath (p/get-game-design-dir-file *test-base-dir-path-str* "my_game_123"))))
      (is (= (.getAbsolutePath expected-path-2) (.getAbsolutePath (p/get-game-design-dir-file *test-base-dir-path-str* "Another Game With Spaces!"))))
      (is (= (.getAbsolutePath expected-path-3) (.getAbsolutePath (p/get-game-design-dir-file *test-base-dir-path-str* nil)))))))

(deftest save-and-load-game-design-test
  (let [game-id (:game_id sample-planner-output)
        ;; Construct game-specific directory path using the test base dir
        game-design-dir-file (p/get-game-design-dir-file *test-base-dir-path-str* game-id)]

    (testing "Saving game design"
      (is (true? (p/save-game-design-files! game-design-dir-file sample-planner-output sample-planner-model-cfg)))
      (is (.exists (io/file game-design-dir-file "initial_state.json")))
      (is (.exists (io/file game-design-dir-file "game_meta.json")))
      (is (.exists (io/file game-design-dir-file (str (u/sanitize-filename "Player1") ".txt"))))
      (is (.exists (io/file game-design-dir-file (str (u/sanitize-filename "Player2") ".txt")))))

    (testing "Verifying content of saved files"
      (let [meta-content (json/read-str (slurp (io/file game-design-dir-file "game_meta.json")) :key-fn keyword)
            init-state-content (json/read-str (slurp (io/file game-design-dir-file "initial_state.json")) :key-fn keyword)
            p1-instr (slurp (io/file game-design-dir-file (str (u/sanitize-filename "Player1") ".txt")))]
        (is (= (:game_title sample-planner-output) (:game_title meta-content)))
        (is (= (:game_id sample-planner-output) (:game_id meta-content)))
        (is (= ["Player1" "Player2"] (sort (:players meta-content))))
        (is (= sample-planner-model-cfg (:planner_model meta-content)))
        (is (= (:initial_game_state sample-planner-output) init-state-content))
        (is (= "Instruction for P1" p1-instr))))

    (testing "Loading game design"
      (let [loaded-design (p/load-game-design-from-files game-design-dir-file)]
        (is (not (nil? loaded-design)))
        (is (:loaded_from_file loaded-design))
        (is (= (.getCanonicalFile game-design-dir-file) (.getCanonicalFile (:game_design_dir loaded-design))))
        (is (= (:game_title sample-planner-output) (:game_title loaded-design)))
        (is (= (:initial_game_state sample-planner-output) (:initial_game_state loaded-design)))
        (is (= "Instruction for P1" (get-in loaded-design [:player_instructions :Player1])))
        (is (= "Instruction for P2" (get-in loaded-design [:player_instructions :Player2])))))

    (testing "Higher-level save and load using planner output structure"
      (let [temp-game-id "temp_save_load_game"
            planner-output-for-save (assoc sample-planner-output :game_id temp-game-id) ; :planner_model already in sample
            expected-dir (p/get-game-design-dir-file *test-base-dir-path-str* temp-game-id)]
        (is (true? (p/save-planner-output! *test-base-dir-path-str* planner-output-for-save sample-planner-model-cfg)))
        (is (.exists (io/file expected-dir "initial_state.json")))

        (let [dummy-prompt-filename (str temp-game-id "_prompt.txt")
              dummy-prompt-file (io/file *test-base-dir-path-str* dummy-prompt-filename)] ; Create dummy prompt in base test dir
          (spit dummy-prompt-file "This is a dummy prompt for loading test.")
          (is (.exists dummy-prompt-file))

          (let [loaded (p/load-design-for-prompt *test-base-dir-path-str* (.getAbsolutePath dummy-prompt-file))]
            (is (not (nil? loaded)))
            (is (:loaded_from_file loaded))
            (is (= temp-game-id (:game_id loaded)))
            (is (= "Instruction for P1" (get-in loaded [:player_instructions :Player1]))))
          (.delete dummy-prompt-file))))))


(deftest load-missing-design-test
  (testing "Loading a non-existent game design"
    (let [non-existent-dir (p/get-game-design-dir-file *test-base-dir-path-str* "non_existent_game_123")]
      (is (nil? (p/load-game-design-from-files non-existent-dir))))))

(deftest load-incomplete-design-test
  (let [game-id "incomplete_game"
        game-design-dir (p/get-game-design-dir-file *test-base-dir-path-str* game-id)]
    (.mkdirs game-design-dir)
    (spit (io/file game-design-dir "game_meta.json") "{\"game_title\":\"Incomplete\"}")

    (testing "Loading with missing initial_state.json"
      (let [loaded (p/load-game-design-from-files game-design-dir)]
        (is (nil? loaded))))
    (delete-recursively! (.getAbsolutePath game-design-dir)) ; Clean up specific test dir

    (testing "Loading with missing instruction file"
      (.mkdirs game-design-dir)
      (spit (io/file game-design-dir "game_meta.json") (json/write-str {:game_title "Missing Instr" :game_id game-id :players ["PlayerX"]}))
      (spit (io/file game-design-dir "initial_state.json") (json/write-str {:desc "valid state"}))
      (let [loaded (p/load-game-design-from-files game-design-dir)]
        (is (not (nil? loaded)))
        (is (= "ERROR: Instruction not found" (get-in loaded [:player_instructions :PlayerX]))))
      (delete-recursively! (.getAbsolutePath game-design-dir)))))

(deftest load-planner-prompt-text-test
  (let [temp-prompt-file (io/file *test-base-dir-path-str* "test_prompt.txt")]
    (testing "Loading an existing prompt file"
      (spit temp-prompt-file "This is a test prompt.")
      (is (= "This is a test prompt." (p/load-planner-prompt-text (.getAbsolutePath temp-prompt-file))))
      (.delete temp-prompt-file))

    (testing "Loading a non-existing prompt file"
      (is (nil? (p/load-planner-prompt-text (io/file *test-base-dir-path-str* "non_existent_prompt.txt"))))))

(deftest prompt-file-path-helpers-test
  (testing "Deriving game ID and design directory from prompt filepath"
    (is (= "my_sample_prompt" (p/prompt-filepath->game-id "planner_prompts/my_sample_prompt.txt")))
    (is (= "another_prompt" (p/prompt-filepath->game-id "/any/path/to/another_prompt.json")))
    (let [expected-dir (io/file *test-base-dir-path-str* "my_sample_prompt")]
      (is (= (.getAbsolutePath expected-dir)
             (.getAbsolutePath (p/prompt-filepath->design-dir-file *test-base-dir-path-str* "prompts/my_sample_prompt.txt")))))))

(deftest design-exists-test
  (let [dummy-base-dir *test-base-dir-path-str*
        existing-prompt-filename "existing_design_prompt.txt"
        design-dir-file (p/prompt-filepath->design-dir-file dummy-base-dir existing-prompt-filename)]
    (.mkdirs design-dir-file)
    (spit (io/file design-dir-file "initial_state.json") "{}")
    (spit (io/file design-dir-file "game_meta.json") "{}")

    (testing "design-exists? for an existing design"
      (is (true? (p/design-exists? dummy-base-dir existing-prompt-filename))))

    (testing "design-exists? for a non-existing design"
      (is (false? (p/design-exists? dummy-base-dir "non_existing_prompt_for_test.txt"))))
    (delete-recursively! (.getAbsolutePath design-dir-file))))
