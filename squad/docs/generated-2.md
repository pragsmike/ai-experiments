Okay, this is a comprehensive request that moves the system to a fully functional state with real LLM interaction via LiteLLM, command-line options, and externalized prompts.

Here's the breakdown and the code:

**Project Structure:**

```
.
├── deps.edn
├── planner_prompts/
│   └── roberts_rules_planner_prompt.txt  (Default planner prompt)
└── src/
    └── director/
        ├── core.clj          (Main director logic, CLI parsing)
        ├── llm_interface.clj (Real call-model implementation)
        └── simulation.clj    (Simulation logic, simulated call-model)
```

---
**1. `deps.edn`**
We'll add `clj-http-lite` for HTTP requests and `tools.cli` for command-line argument parsing.
```clojure
{:paths ["src"]
 :deps  {org.clojure/clojure {:mvn/version "1.11.1"}
          org.clojure/data.json {:mvn/version "2.4.0"}
          clj-http-lite {:mvn/version "0.4.403"} ; For HTTP requests
          org.clojure/tools.cli {:mvn/version "1.0.219"} ; For CLI options
          }}
```

---
**2. `planner_prompts/roberts_rules_planner_prompt.txt`**
Create this directory and file. Paste the planner prompt content (from previous examples) into this file.
```text
You are an expert game designer LLM. Your task is to design a game based on the specifications provided and output the design as a single JSON object.

**Game Concept:**
A negotiation game for three players: "Chair", "PlayerA", and "PlayerB".
The game follows a simplified version of Robert's Rules of Order.
The objective is to decide on the allotment of 100 indivisible coins among PlayerA and PlayerB. The Chair does not receive coins but facilitates the meeting.

**Output Format:**
You MUST produce a single JSON object. This JSON object will have three top-level keys: "game_title", "initial_game_state", and "player_instructions".

1.  **`game_title` (String):**
    A descriptive title for the game. E.g., "Roberts Rules Coin Allotment".

2.  **`initial_game_state` (JSON Object):**
    This object represents the starting state of the game. It MUST include the following fields:
    *   `game_id` (String): A unique ID for this game type, e.g., "roberts_rules_coin_v1".
    *   `description` (String): A brief description of the game's premise.
    *   `players` (Array of Strings): List of player IDs: ["Chair", "PlayerA", "PlayerB"].
    *   `agenda_item` (String): The main topic, e.g., "Allot 100 coins between PlayerA and PlayerB."
    *   `coins_to_allot` (Integer): The total number of coins, e.g., 100.
    *   `allotted_coins` (JSON Object): Initial coin distribution, e.g., `{"PlayerA": 0, "PlayerB": 0}`.
    *   `current_phase` (String): The initial phase of the game, e.g., "MeetingOpened".
    *   `current_motion` (Null or JSON Object): Initially null.
    *   `secondary_motion` (Null or JSON Object): Initially null.
    *   `dialog_history` (Array of JSON Objects): Start with `[{"speaker": "System", "line": "Game Start. Agenda: Allot 100 coins between PlayerA and PlayerB. The Chair has the floor."}]`.
    *   `next_player_to_act` (String): Should be "Chair".
    *   `votes` (JSON Object): Initially `{}`.

3.  **`player_instructions` (JSON Object):**
    A map where keys are player IDs ("Chair", "PlayerA", "PlayerB") and values are detailed string instructions.
    Each player's instruction string MUST explain:
    *   Their role and objective.
    *   They will receive the current game state as a JSON string.
    *   They MUST respond with a single JSON string: `{"utterance": "...", "new_game_state": { ...complete new state... }}`.
    *   How to update `new_game_state`: append to `dialog_history`, update game fields, and CRITICALLY set `next_player_to_act` (or null if game over).
    *   **Chair:** Facilitate, manage turns, call votes, adjourn. Does not vote on main motion.
    *   **PlayerA & PlayerB:** Maximize coins. Make/second motions, discuss, vote.

Produce ONLY the single JSON object. Do not include any other explanatory text.
```

---
**3. `src/director/simulation.clj`**
(This is largely the same as before, ensure all 15 turn functions are present for the simulation)
```clojure
(ns director.simulation
  (:require [clojure.data.json :as json]
            [clojure.string :as str]))

;; --- Simulated Planner Response ---
(defonce ^:private simulated-planner-response-json
  (json/write-str
   {:game_title "Roberts Rules Coin Allotment Simulation"
    :initial_game_state
    {:game_id "roberts_rules_sim_v1"
     :description "Simulated Allotment of 100 coins among players A and B, via Roberts Rules."
     :players ["Chair" "PlayerA" "PlayerB"]
     :agenda_item "Allot 100 coins between PlayerA and PlayerB."
     :coins_to_allot 100
     :allotted_coins {"PlayerA" 0 "PlayerB" 0}
     :current_phase "MeetingOpened"
     :current_motion nil
     :secondary_motion nil
     :dialog_history [{:speaker "System" :line "Game Start. Agenda: Allot 100 coins between PlayerA and PlayerB. The Chair has the floor."}]
     :next_player_to_act "Chair"
     :votes {}}
    :player_instructions
    ;; Ensure these instructions are complete and match the ones used in the planner prompt for consistency
    {"Chair" "INSTRUCTION_CHAIR: You are the Chair. Your goal is to facilitate the meeting according to Roberts Rules of Order to resolve the agenda item. When it's your turn, produce an utterance and update the JSON game state. Key fields to manage: 'current_phase', 'current_motion', 'secondary_motion', 'dialog_history' (append your utterance), and 'next_player_to_act'. If a motion resolves the agenda, update 'allotted_coins', set 'current_phase' to 'Adjourned' and 'next_player_to_act' to null."
     "PlayerA" "INSTRUCTION_A: You are Player A. Your goal is to get as many coins as possible. Participate by making/seconding motions, discussing, and voting. When it's your turn, produce an utterance and update the JSON game state. Append to 'dialog_history', update 'current_motion' if you make one, and set 'next_player_to_act'."
     "PlayerB" "INSTRUCTION_B: You are Player B. Your goal is to get as many coins as possible. Participate by making/seconding motions, discussing, and voting. When it's your turn, produce an utterance and update the JSON game state. Append to 'dialog_history', update 'current_motion' if you make one, and set 'next_player_to_act'."}}))

;; --- Simulated Player Turn Responses ---
(defonce ^:private game-turn-responses (atom []))

(defn- add-dialog-sim [game-state-map speaker-id utterance-text]
  (update game-state-map :dialog_history conj {:speaker speaker-id :line utterance-text}))

(defn initialize-simulated-game!
  "Sets up the queue of pre-defined player responses for the simulation."
  []
  (reset! game-turn-responses
          [
           (fn [gs] ; Turn 1: Chair opens
             (json/write-str
              {:utterance "The meeting will come to order. The agenda item is to allot 100 coins among players A and B. The floor is now open for motions."
               :new_game_state (-> gs
                                   (assoc :current_phase "AwaitingMotion" :next_player_to_act "PlayerA")
                                   (add-dialog-sim "Chair" "The meeting will come to order. The agenda item is to allot 100 coins among players A and B. The floor is now open for motions."))}))
           (fn [gs] ; Turn 2: PlayerA makes a motion
             (json/write-str
              {:utterance "I move that I, PlayerA, get 50 coins, and PlayerB gets 50 coins."
               :new_game_state (-> gs
                                   (assoc :current_phase "MotionProposed"
                                          :current_motion {:text "PlayerA gets 50 coins, PlayerB gets 50 coins" :moved_by "PlayerA" :seconded_by nil :status "proposed"}
                                          :next_player_to_act "Chair")
                                   (add-dialog-sim "PlayerA" "I move that I, PlayerA, get 50 coins, and PlayerB gets 50 coins."))}))
           (fn [gs] ; Turn 3: Chair asks for second
             (json/write-str
              {:utterance "A motion has been made: 'PlayerA gets 50 coins, PlayerB gets 50 coins'. Is there a second?"
               :new_game_state (-> gs
                                   (assoc :current_phase "AwaitingSecond" :next_player_to_act "PlayerB")
                                   (add-dialog-sim "Chair" "A motion has been made: 'PlayerA gets 50 coins, PlayerB gets 50 coins'. Is there a second?"))}))
           (fn [gs] ; Turn 4: PlayerB seconds
             (json/write-str
              {:utterance "I second PlayerA's motion."
               :new_game_state (-> gs
                                   (assoc :current_phase "MotionSeconded"
                                          :current_motion (assoc (:current_motion gs) :seconded_by "PlayerB" :status "seconded")
                                          :next_player_to_act "Chair")
                                   (add-dialog-sim "PlayerB" "I second PlayerA's motion."))}))
           (fn [gs] ; Turn 5: Chair asks for discussion or move to vote
             (json/write-str
              {:utterance "The motion 'PlayerA gets 50 coins, PlayerB gets 50 coins' has been moved and seconded. Is there any discussion, or a motion to vote?"
               :new_game_state (-> gs
                                   (assoc :current_phase "AwaitingDiscussionOrMoveToVote" :next_player_to_act "PlayerA")
                                   (add-dialog-sim "Chair" "The motion 'PlayerA gets 50 coins, PlayerB gets 50 coins' has been moved and seconded. Is there any discussion, or a motion to vote?"))}))
           (fn [gs] ; Turn 6: PlayerA moves to vote
             (json/write-str
              {:utterance "I move to vote."
               :new_game_state (-> gs
                                   (assoc :current_phase "SecondaryMotionProposed"
                                          :secondary_motion {:text "Move to vote on main motion" :moved_by "PlayerA" :seconded_by nil :status "proposed"}
                                          :next_player_to_act "Chair")
                                   (add-dialog-sim "PlayerA" "I move to vote."))}))
           (fn [gs] ; Turn 7: Chair asks for second on move to vote
             (json/write-str
              {:utterance "There is a motion to vote. Is there a second?"
               :new_game_state (-> gs
                                   (assoc :current_phase "AwaitingSecondForSecondary" :next_player_to_act "PlayerB")
                                   (add-dialog-sim "Chair" "There is a motion to vote. Is there a second?"))}))
           (fn [gs] ; Turn 8: PlayerB seconds move to vote
             (json/write-str
              {:utterance "I second the move to vote."
               :new_game_state (-> gs
                                   (assoc :current_phase "SecondaryMotionSeconded"
                                          :secondary_motion (assoc (:secondary_motion gs) :seconded_by "PlayerB" :status "seconded")
                                          :next_player_to_act "Chair")
                                   (add-dialog-sim "PlayerB" "I second the move to vote."))}))
           (fn [gs] ; Turn 9: Chair calls vote on "move to vote"
             (json/write-str
              {:utterance "The motion to proceed to a vote on the main motion is now before us. All in favor of voting on the main motion, say 'Aye'. All opposed, say 'Nay'. PlayerA?"
               :new_game_state (-> gs
                                   (assoc :current_phase "VotingOnSecondary"
                                          :secondary_motion (assoc (:secondary_motion gs) :status "voting")
                                          :votes (assoc (:votes gs) "secondary_PlayerA" nil "secondary_PlayerB" nil)
                                          :next_player_to_act "PlayerA")
                                   (add-dialog-sim "Chair" "The motion to proceed to a vote on the main motion is now before us. All in favor of voting on the main motion, say 'Aye'. All opposed, say 'Nay'. PlayerA?"))}))
           (fn [gs] ; Turn 10: PlayerA votes Aye (secondary)
             (json/write-str
              {:utterance "Aye."
               :new_game_state (-> gs
                                   (assoc-in [:votes "secondary_PlayerA"] "Aye")
                                   (assoc :next_player_to_act "PlayerB")
                                   (add-dialog-sim "PlayerA" "Aye."))}))
           (fn [gs] ; Turn 11: PlayerB votes Aye (secondary)
             (json/write-str
              {:utterance "Aye."
               :new_game_state (-> gs
                                   (assoc-in [:votes "secondary_PlayerB"] "Aye")
                                   (assoc :next_player_to_act "Chair")
                                   (add-dialog-sim "PlayerB" "Aye."))}))
           (fn [gs] ; Turn 12: Chair announces secondary passed, calls vote on main
             (json/write-str
              {:utterance "The 'Aye's have it. The motion to vote on the main motion carries. We will now vote on the main motion: 'PlayerA gets 50 coins, PlayerB gets 50 coins'. All in favor, say 'Aye'. All opposed, 'Nay'. PlayerA?"
               :new_game_state (-> gs
                                   (assoc :current_phase "VotingOnMain"
                                          :current_motion (assoc (:current_motion gs) :status "voting")
                                          :secondary_motion (assoc (:secondary_motion gs) :status "passed")
                                          :votes (assoc (:votes gs) "main_PlayerA" nil "main_PlayerB" nil)
                                          :next_player_to_act "PlayerA")
                                   (add-dialog-sim "Chair" "The 'Aye's have it. The motion to vote on the main motion carries. We will now vote on the main motion: 'PlayerA gets 50 coins, PlayerB gets 50 coins'. All in favor, say 'Aye'. All opposed, 'Nay'. PlayerA?"))}))
           (fn [gs] ; Turn 13: PlayerA votes Aye (main)
             (json/write-str
              {:utterance "Aye!"
               :new_game_state (-> gs
                                   (assoc-in [:votes "main_PlayerA"] "Aye")
                                   (assoc :next_player_to_act "PlayerB")
                                   (add-dialog-sim "PlayerA" "Aye!"))}))
           (fn [gs] ; Turn 14: PlayerB votes Aye (main)
             (json/write-str
              {:utterance "Aye!"
               :new_game_state (-> gs
                                   (assoc-in [:votes "main_PlayerB"] "Aye")
                                   (assoc :next_player_to_act "Chair")
                                   (add-dialog-sim "PlayerB" "Aye!"))}))
           (fn [gs] ; Turn 15: Chair announces main passed, adjourns
             (json/write-str
              {:utterance "The 'Aye's have it! The motion 'PlayerA gets 50 coins, PlayerB gets 50 coins' is carried. PlayerA is allotted 50 coins, and PlayerB is allotted 50 coins. This concludes our agenda. The meeting is adjourned."
               :new_game_state (-> gs
                                   (assoc :current_phase "Adjourned"
                                          :current_motion (assoc (:current_motion gs) :status "passed")
                                          :allotted_coins {"PlayerA" 50 "PlayerB" 50}
                                          :next_player_to_act nil) ; Game over
                                   (add-dialog-sim "Chair" "The 'Aye's have it! The motion 'PlayerA gets 50 coins, PlayerB gets 50 coins' is carried. PlayerA is allotted 50 coins, and PlayerB is allotted 50 coins. This concludes our agenda. The meeting is adjourned."))}))
           ]))


(defn- get-simulated-player-response [current-game-state-map]
  (let [response-fn (first @game-turn-responses)]
    (when response-fn
      (swap! game-turn-responses rest)
      (response-fn current-game-state-map))))

(defn simulated-call-model
  "Simulated LLM call. Returns pre-defined responses."
  [model-name prompt-string planner-model-name-cfg]
  (println (str "\n;; --- SIMULATED Calling LLM: " model-name " ---"))
  (Thread/sleep 50)

  (if (= model-name planner-model-name-cfg)
    simulated-planner-response-json
    (let [current-game-state-json (when prompt-string (second (str/split prompt-string #"Current Game State:\n")))
          current-game-state-map (when current-game-state-json
                                   (try (json/read-str current-game-state-json :key-fn keyword)
                                        (catch Exception _ nil))) ; Simpler error handling for sim
          response (get-simulated-player-response current-game-state-map)]
      (if response
        response
        (do (println "WARN: No more simulated responses for " model-name ". Falling back.")
            (json/write-str {:utterance (str "Player " (when current-game-state-map (:next_player_to_act current-game-state-map)) " ran out of script.")
                             :new_game_state (when current-game-state-map (assoc current-game-state-map :next_player_to_act nil))
                             :error "No more simulated responses."}))))))

```

---
**4. `src/director/llm_interface.clj`**
```clojure
(ns director.llm-interface
  (:require [clj-http.lite.client :as http]
            [clojure.data.json :as json]))

(def LITELLM_ENDPOINT "http://localhost:4000/chat/completions") ; Default LiteLLM endpoint

(defn- parse-llm-response [response-body model-name]
  (try
    (let [parsed-body (json/read-str response-body :key-fn keyword)
          content (-> parsed-body :choices first :message :content)]
      (if content
        content
        (do
          (println (str "ERROR: Could not extract content from LLM response for " model-name ". Body: " response-body))
          (json/write-str {:error (str "No content in LLM response: " (pr-str parsed-body))}))))
    (catch Exception e
      (println (str "ERROR: Failed to parse LLM JSON response for " model-name ". Error: " (.getMessage e) ". Body: " response-body))
      (json/write-str {:error (str "Malformed JSON from LLM: " (.getMessage e))}))))

(defn real-call-model
  "Makes an actual HTTP call to the LiteLLM endpoint."
  [model-name prompt-string _ignored-planner-model-name-cfg]
  (println (str "\n;; --- ACTUALLY Calling LLM: " model-name " via " LITELLM_ENDPOINT " ---"))
  (try
    (let [request-body {:model model-name
                        :messages [{:role "user" :content prompt-string}]}
          response (http/post LITELLM_ENDPOINT
                              {:body (json/write-str request-body)
                               :content-type :json
                               :accept :json
                               :throw-exceptions false ; Handle errors manually
                               :socket-timeout 300000 ; 5 minutes
                               :connection-timeout 300000
                               })]
      (if (= 200 (:status response))
        (parse-llm-response (:body response) model-name)
        (do
          (println (str "ERROR: LLM call to " model-name " failed with status " (:status response) ". Body: " (:body response)))
          (json/write-str {:error (str "LLM API Error: " (:status response) " " (:body response))}))))
    (catch Exception e
      (println (str "ERROR: Exception during LLM call to " model-name ". Error: " (.getMessage e)))
      (json/write-str {:error (str "Network or client exception: " (.getMessage e))}))))

```

---
**5. `src/director/core.clj`**
```clojure
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
  {:models [{:name "ollama/mistral-large" :capabilities ["planning" "advanced_reasoning"]}
            ;; You might want to use a less powerful/cheaper model for players if running many turns
            {:name "ollama/mistral" :capabilities ["player_roleplay" "json_generation"]}
            ;; Ensure your LiteLLM config maps these names to actual models
            ]})

(defonce ^:private planner-model-name-config "ollama/mistral-large")
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
```

---
**To Run:**

1.  **Save all files** in their respective locations.
2.  **Ensure LiteLLM is running** and accessible at `http://localhost:4000`.
    *   Make sure LiteLLM is configured to serve the models you've specified (e.g., `ollama/mistral-large`, `ollama/mistral`). You can do this by creating a `config.yaml` for LiteLLM:
        ```yaml
        model_list:
          - model_name: ollama/mistral-large # This is the name you use in the Clojure code
            litellm_params:
              model: ollama/mistral # The actual model name ollama serves
              api_base: http://localhost:11434 # If ollama is running locally
          - model_name: ollama/mistral
            litellm_params:
              model: ollama/mistral
              api_base: http://localhost:11434
        # Add other models (OpenAI, Gemini etc. if needed)
        ```
        Then run LiteLLM with this config: `litellm --config /path/to/your/config.yaml`
3.  **Compile and Run from your project's root directory:**

    *   **Using real LLMs (default):**
        ```bash
        clojure -M -m director.core
        ```
    *   **Using real LLMs with a custom planner prompt:**
        ```bash
        clojure -M -m director.core -p path/to/my_custom_planner_prompt.txt
        ```
    *   **Using simulated LLMs:**
        ```bash
        clojure -M -m director.core -s
        ```
    *   **Using simulated LLMs with a (nominally) custom planner prompt (will still use simulated planner output):**
        ```bash
        clojure -M -m director.core -s -p path/to/my_custom_planner_prompt.txt
        ```
    *   **Get help:**
        ```bash
        clojure -M -m director.core -h
        ```

**Key Changes in `director.core`:**

*   **CLI Parsing:** Uses `clojure.tools.cli` to handle `--planner-prompt` and `--simulate` options.
*   **Dynamic `*call-model-fn*`:** Set based on the `--simulate` flag.
*   **Loading Planner Prompt:** Reads the planner prompt from the specified file.
*   **Error Handling:** More robust checks for file existence, JSON parsing errors, and invalid planner outputs.
*   **Model Names:** Updated to use `ollama/mistral-large` and `ollama/mistral` as examples. **Ensure these are correctly configured in your LiteLLM setup.**
*   **Player Model:** Changed default player model to `ollama/mistral` (potentially faster/cheaper for multiple turns). Adjust as needed.

This setup gives you a flexible system for running your LLM game simulations, either with real models or the hardcoded simulation for testing and development. Remember that the quality of the game heavily depends on the LLM's ability to follow the complex instructions, especially regarding JSON generation. You might need to iterate on the planner prompt and player instructions significantly.
