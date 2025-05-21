(ns director.core
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [director.simulation :as sim]
            [director.llm-interface :as llm-iface])
  (:gen-class))

;; --- Configuration ---
(defonce ^:private default-planner-prompt-file "planner_prompts/roberts_rules_planner_prompt.txt")

(defonce ^:private llm-roster
  {:models [{:name "openai/turbo" :capabilities ["planning" "advanced_reasoning"]}
            ;; You might want to use a less powerful/cheaper model for players if running many turns
            {:name "ollama/mistral" :capabilities ["player_roleplay" "json_generation"]}
            ;; Ensure your LiteLLM config maps these names to actual models
            ]})

(defonce ^:private planner-model-name-config "openai/turbo")
(defonce ^:private default-player-model-name-config "ollama/mistral") ; Using a potentially smaller model for players

;; --- Dynamic var for LLM call function ---
(defonce ^:dynamic *call-model-fn* llm-iface/real-call-model) ; Default to real LLMs

;; --- CLI Option Parsing ---
(def ^:private cli-options
  [["-p" "--planner-prompt FILE" "Path to the planner prompt file"
    :default default-planner-prompt-file
    :validate [#(.exists (io/file %)) "Planner prompt file must exist"]]
   ["-s" "--simulate" "Use simulated LLM agents instead of real ones"]
   ["-h" "--help"]])

;; --- Helper Functions ---
(defn- parse-json-str [json-str error-context]
  (try
    (json/read-str json-str :key-fn keyword)
    (catch Exception e
      (println (str "Error parsing JSON string for " error-context ": " (.getMessage e) "\nString was: " json-str))
      {:error (str "JSON parsing error: " (.getMessage e)) :raw json-str}))) ; Include raw string on error

(defn- load-planner-prompt [filepath]
  (try
    (slurp filepath)
    (catch Exception e
      (println (str "Error reading planner prompt file " filepath ": " (.getMessage e)))
      nil)))

;; --- Director Logic ---
(defn- plan-game [planner-prompt-text]
  (println "--- Starting Planning Phase ---")
  (println "Director: Sending prompt to Planner model (" planner-model-name-config ")...")
  (let [planner-response-str (*call-model-fn* planner-model-name-config planner-prompt-text planner-model-name-config)
        planner-output (parse-json-str planner-response-str "Planner Output")]
    (if (and planner-output (not (:error planner-output)))
      (do
        (println "Director: Received and parsed planner output.")
        (println "Director: Game Title - " (:game_title planner-output))
        planner-output)
      (do
        (println "Director: Failed to parse planner output or planner returned an error.")
        (when (:raw planner-output) (println "Planner raw response was logged."))
        nil))))

(defn- play-game [initial-game-state player-instructions]
  (println "\n--- Starting Play Phase ---")
  (loop [current-game-state initial-game-state
         turn-number 1]
    (println (str "\n--- Turn " turn-number " ---"))
    (let [next-player-id (get current-game-state :next_player_to_act)]
      (if (or (nil? next-player-id) (empty? (str next-player-id))) ; Check for nil or empty string
        (do
          (println "Director: Game Over! (No next player specified or game concluded)")
          (println "Final Game State Highlights:")
          (println "  Phase:" (:current_phase current-game-state))
          (println "  Allotted Coins:" (:allotted_coins current-game-state))
          (let [last-dialog (last (:dialog_history current-game-state))]
            (when last-dialog
              (println (str "  Final Utterance (" (:speaker last-dialog) "): " (:line last-dialog)))))
          current-game-state)

        (let [player-instr (get player-instructions (keyword next-player-id)
                               (get player-instructions next-player-id))
              _ (when-not player-instr
                  (throw (AssertionError. (str "No instruction found for player: " next-player-id ". Available instructions for: " (keys player-instructions)))))]
          (println (str "Director: It's " next-player-id "'s turn."))

          (let [player-model default-player-model-name-config
                player-prompt-string (str player-instr
                                          "\n\nCurrent Game State:\n"
                                          (json/write-str current-game-state))]

            (println (str "Director: Sending prompt to " next-player-id " (" player-model ")..."))
            (let [player-response-str (*call-model-fn* player-model player-prompt-string planner-model-name-config)
                  player-response (parse-json-str player-response-str (str next-player-id "'s Response"))]

              (if (and player-response (:utterance player-response) (:new_game_state player-response) (not (:error player-response)))
                (let [utterance (:utterance player-response)
                      new-game-state (:new_game_state player-response)]
                  (println (str "Director: " next-player-id " says: \"" utterance "\""))

                  ;; Basic validation of new_game_state
                  (if (map? new-game-state)
                     (recur new-game-state (inc turn-number))
                     (do
                       (println (str "Director: ERROR! Player " next-player-id " returned an invalid new_game_state (not a map). Game cannot continue."))
                       (println "Received new_game_state: " (pr-str new-game-state))
                       current-game-state)))
                (do
                  (println (str "Director: Failed to parse response from " next-player-id ", player returned an error, or response malformed. Game cannot continue."))
                  (when (:raw player-response) (println "Player raw response was logged."))
                  current-game-state)))))))))

(defn -main [& args]
  (let [{:keys [options summary errors]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) (do (println "LLM Game Director Options:\n" summary) (System/exit 0))
      errors (do (println "Error(s) parsing options:\n" (clojure.string/join \newline errors)) (System/exit 1)))

    (println "=== Welcome to the LLM Game Director ===")
    (println "Using planner prompt from:" (:planner-prompt options))

    (if (:simulate options)
      (do
        (println "INFO: Using SIMULATED LLM calls.")
        (alter-var-root #'*call-model-fn* (constantly sim/simulated-call-model))
        (sim/initialize-simulated-game!))
      (do
        (println "INFO: Using REAL LLM calls via LiteLLM.")
        (alter-var-root #'*call-model-fn* (constantly llm-iface/real-call-model))))

    (if-let [planner-prompt-text (load-planner-prompt (:planner-prompt options))]
      (if-let [planner-data (plan-game planner-prompt-text)]
        (let [initial-state (:initial_game_state planner-data)
              instructions (:player_instructions planner-data)]
          (if (and (map? initial-state) (seq initial-state)
                   (map? instructions) (seq instructions)
                   (:next_player_to_act initial-state))
            (play-game initial-state instructions)
            (println "Director: Planner output was invalid or incomplete (missing initial_state, instructions, or next_player_to_act). Halting.")))
        (println "Director: Planning phase failed. Halting."))
      (println "Director: Could not load planner prompt. Halting."))

    (println "\n=== Director Finished ===")))
