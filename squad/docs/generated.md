Okay, this is a solid foundation. The system description is now quite detailed, especially regarding the JSON data formats. This makes it feasible to start implementing the Clojure director.

Here's the Clojure code for the director system. This implementation will include:
1.  A `deps.edn` for dependencies.
2.  A `src/director/core.clj` file with the main logic.
3.  A **simulated** `call-model` function. In a real system, this would make HTTP calls to LiteLLM. Here, it will return pre-defined responses to allow the Roberts Rules example game to play out as described.
4.  The main director logic for the planning and play phases.

This code will be runnable and demonstrate the described flow.

---
**1. `deps.edn`**
```clojure
{:paths ["src"]
 :deps  {org.clojure/clojure {:mvn/version "1.11.1"}
          org.clojure/data.json {:mvn/version "2.4.0"}}}
```

---
**2. `src/director/core.clj`**
```clojure
(ns director.core
  (:require [clojure.data.json :as json]
            [clojure.string :as str])
  (:gen-class))

;; --- Configuration ---
(defonce llm-roster
  {:models [{:name "ollama/mistral-large" :capabilities ["planning" "advanced_reasoning"]}
            {:name "openai/gpt-3.5-turbo" :capabilities ["player_roleplay" "json_generation"]}
            ;; Add more models as needed
            ]})

(defonce planner-model-name "ollama/mistral-large")
(defonce default-player-model-name "openai/gpt-3.5-turbo")

(defonce human-written-planner-prompt
  "You are an expert game designer LLM. Your task is to design a game based on the specifications provided and output the design as a single JSON object.

**Game Concept:**
A negotiation game for three players: \"Chair\", \"PlayerA\", and \"PlayerB\".
The game follows a simplified version of Robert's Rules of Order.
The objective is to decide on the allotment of 100 indivisible coins among PlayerA and PlayerB. The Chair does not receive coins but facilitates the meeting.

**Output Format:**
You MUST produce a single JSON object. This JSON object will have three top-level keys: \"game_title\", \"initial_game_state\", and \"player_instructions\".

1.  **`game_title` (String):**
    A descriptive title for the game. E.g., \"Roberts Rules Coin Allotment\".

2.  **`initial_game_state` (JSON Object):**
    This object represents the starting state of the game. It MUST include the following fields:
    *   `game_id` (String): A unique ID for this game type, e.g., \"roberts_rules_coin_v1\".
    *   `description` (String): A brief description of the game's premise.
    *   `players` (Array of Strings): List of player IDs: [\"Chair\", \"PlayerA\", \"PlayerB\"].
    *   `agenda_item` (String): The main topic, e.g., \"Allot 100 coins between PlayerA and PlayerB.\"
    *   `coins_to_allot` (Integer): The total number of coins, e.g., 100.
    *   `allotted_coins` (JSON Object): Initial coin distribution, e.g., `{\"PlayerA\": 0, \"PlayerB\": 0}`.
    *   `current_phase` (String): The initial phase of the game, e.g., \"MeetingOpened\".
    *   `current_motion` (Null or JSON Object): Initially null.
    *   `secondary_motion` (Null or JSON Object): Initially null.
    *   `dialog_history` (Array of JSON Objects): Start with `[{\"speaker\": \"System\", \"line\": \"Game Start. Agenda: Allot 100 coins between PlayerA and PlayerB. The Chair has the floor.\"}]`.
    *   `next_player_to_act` (String): Should be \"Chair\".
    *   `votes` (JSON Object): Initially `{}`.

3.  **`player_instructions` (JSON Object):**
    A map where keys are player IDs (\"Chair\", \"PlayerA\", \"PlayerB\") and values are detailed string instructions.
    Each player's instruction string MUST explain:
    *   Their role and objective.
    *   They will receive the current game state as a JSON string.
    *   They MUST respond with a single JSON string: `{\"utterance\": \"...\", \"new_game_state\": { ...complete new state... }}`.
    *   How to update `new_game_state`: append to `dialog_history`, update game fields, and CRITICALLY set `next_player_to_act` (or null if game over).
    *   **Chair:** Facilitate, manage turns, call votes, adjourn. Does not vote on main motion.
    *   **PlayerA & PlayerB:** Maximize coins. Make/second motions, discuss, vote.

Produce ONLY the single JSON object. Do not include any other explanatory text.")

;; --- Simulated LLM Call ---
;; In a real system, this would make an HTTP request to LiteLLM.
;; For this example, it returns pre-defined responses to simulate the Roberts Rules game.

(defonce simulated-planner-response-json
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
    {"Chair" "INSTRUCTION_CHAIR: You are the Chair. Your goal is to facilitate the meeting according to Roberts Rules of Order to resolve the agenda item. When it's your turn, produce an utterance and update the JSON game state. Key fields to manage: 'current_phase', 'current_motion', 'secondary_motion', 'dialog_history' (append your utterance), and 'next_player_to_act'. If a motion resolves the agenda, update 'allotted_coins', set 'current_phase' to 'Adjourned' and 'next_player_to_act' to null."
     "PlayerA" "INSTRUCTION_A: You are Player A. Your goal is to get as many coins as possible. Participate by making/seconding motions, discussing, and voting. When it's your turn, produce an utterance and update the JSON game state. Append to 'dialog_history', update 'current_motion' if you make one, and set 'next_player_to_act'."
     "PlayerB" "INSTRUCTION_B: You are Player B. Your goal is to get as many coins as possible. Participate by making/seconding motions, discussing, and voting. When it's your turn, produce an utterance and update the JSON game state. Append to 'dialog_history', update 'current_motion' if you make one, and set 'next_player_to_act'."}}))

;; This atom will hold a queue of pre-defined player responses for the simulation.
;; Each element is a function that takes the current game state and returns the player's JSON response string.
(defonce ^:private game-turn-responses (atom []))

(defn- get-simulated-player-response [current-game-state-map]
  (let [response-fn (first @game-turn-responses)]
    (when response-fn
      (swap! game-turn-responses rest)
      (response-fn current-game-state-map))))

(defn- add-dialog [game-state-map speaker-id utterance-text]
  (update game-state-map :dialog_history conj {:speaker speaker-id :line utterance-text}))

(defn- initialize-simulated-responses! []
  (reset! game-turn-responses
          [(fn [gs] ; Turn 1: Chair opens
             (json/write-str
              {:utterance "The meeting will come to order. The agenda item is to allot 100 coins among players A and B. The floor is now open for motions."
               :new_game_state (-> gs
                                   (assoc :current_phase "AwaitingMotion" :next_player_to_act "PlayerA")
                                   (add-dialog "Chair" "The meeting will come to order. The agenda item is to allot 100 coins among players A and B. The floor is now open for motions."))}))
           (fn [gs] ; Turn 2: PlayerA makes a motion
             (json/write-str
              {:utterance "I move that I, PlayerA, get 50 coins, and PlayerB gets 50 coins."
               :new_game_state (-> gs
                                   (assoc :current_phase "MotionProposed"
                                          :current_motion {:text "PlayerA gets 50 coins, PlayerB gets 50 coins" :moved_by "PlayerA" :seconded_by nil :status "proposed"}
                                          :next_player_to_act "Chair")
                                   (add-dialog "PlayerA" "I move that I, PlayerA, get 50 coins, and PlayerB gets 50 coins."))}))
           (fn [gs] ; Turn 3: Chair asks for second
             (json/write-str
              {:utterance "A motion has been made: 'PlayerA gets 50 coins, PlayerB gets 50 coins'. Is there a second?"
               :new_game_state (-> gs
                                   (assoc :current_phase "AwaitingSecond" :next_player_to_act "PlayerB")
                                   (add-dialog "Chair" "A motion has been made: 'PlayerA gets 50 coins, PlayerB gets 50 coins'. Is there a second?"))}))
           (fn [gs] ; Turn 4: PlayerB seconds
             (json/write-str
              {:utterance "I second PlayerA's motion."
               :new_game_state (-> gs
                                   (assoc :current_phase "MotionSeconded"
                                          :current_motion (assoc (:current_motion gs) :seconded_by "PlayerB" :status "seconded")
                                          :next_player_to_act "Chair")
                                   (add-dialog "PlayerB" "I second PlayerA's motion."))}))
           (fn [gs] ; Turn 5: Chair asks for discussion or move to vote
             (json/write-str
              {:utterance "The motion 'PlayerA gets 50 coins, PlayerB gets 50 coins' has been moved and seconded. Is there any discussion, or a motion to vote?"
               :new_game_state (-> gs
                                   (assoc :current_phase "AwaitingDiscussionOrMoveToVote" :next_player_to_act "PlayerA")
                                   (add-dialog "Chair" "The motion 'PlayerA gets 50 coins, PlayerB gets 50 coins' has been moved and seconded. Is there any discussion, or a motion to vote?"))}))
           (fn [gs] ; Turn 6: PlayerA moves to vote
             (json/write-str
              {:utterance "I move to vote."
               :new_game_state (-> gs
                                   (assoc :current_phase "SecondaryMotionProposed"
                                          :secondary_motion {:text "Move to vote on main motion" :moved_by "PlayerA" :seconded_by nil :status "proposed"}
                                          :next_player_to_act "Chair")
                                   (add-dialog "PlayerA" "I move to vote."))}))
           (fn [gs] ; Turn 7: Chair asks for second on move to vote
             (json/write-str
              {:utterance "There is a motion to vote. Is there a second?"
               :new_game_state (-> gs
                                   (assoc :current_phase "AwaitingSecondForSecondary" :next_player_to_act "PlayerB")
                                   (add-dialog "Chair" "There is a motion to vote. Is there a second?"))}))
           (fn [gs] ; Turn 8: PlayerB seconds move to vote
             (json/write-str
              {:utterance "I second the move to vote."
               :new_game_state (-> gs
                                   (assoc :current_phase "SecondaryMotionSeconded"
                                          :secondary_motion (assoc (:secondary_motion gs) :seconded_by "PlayerB" :status "seconded")
                                          :next_player_to_act "Chair")
                                   (add-dialog "PlayerB" "I second the move to vote."))}))
           (fn [gs] ; Turn 9: Chair calls vote on "move to vote"
             (json/write-str
              {:utterance "The motion to proceed to a vote on the main motion is now before us. All in favor of voting on the main motion, say 'Aye'. All opposed, say 'Nay'. PlayerA?"
               :new_game_state (-> gs
                                   (assoc :current_phase "VotingOnSecondary"
                                          :secondary_motion (assoc (:secondary_motion gs) :status "voting")
                                          :votes (assoc (:votes gs) "secondary_PlayerA" nil "secondary_PlayerB" nil)
                                          :next_player_to_act "PlayerA")
                                   (add-dialog "Chair" "The motion to proceed to a vote on the main motion is now before us. All in favor of voting on the main motion, say 'Aye'. All opposed, say 'Nay'. PlayerA?"))}))
           (fn [gs] ; Turn 10: PlayerA votes Aye (secondary)
             (json/write-str
              {:utterance "Aye."
               :new_game_state (-> gs
                                   (assoc-in [:votes "secondary_PlayerA"] "Aye")
                                   (assoc :next_player_to_act "PlayerB")
                                   (add-dialog "PlayerA" "Aye."))}))
           (fn [gs] ; Turn 11: PlayerB votes Aye (secondary)
             (json/write-str
              {:utterance "Aye."
               :new_game_state (-> gs
                                   (assoc-in [:votes "secondary_PlayerB"] "Aye")
                                   (assoc :next_player_to_act "Chair")
                                   (add-dialog "PlayerB" "Aye."))}))
           (fn [gs] ; Turn 12: Chair announces secondary passed, calls vote on main
             (json/write-str
              {:utterance "The 'Aye's have it. The motion to vote on the main motion carries. We will now vote on the main motion: 'PlayerA gets 50 coins, PlayerB gets 50 coins'. All in favor, say 'Aye'. All opposed, 'Nay'. PlayerA?"
               :new_game_state (-> gs
                                   (assoc :current_phase "VotingOnMain"
                                          :current_motion (assoc (:current_motion gs) :status "voting")
                                          :secondary_motion (assoc (:secondary_motion gs) :status "passed")
                                          :votes (assoc (:votes gs) "main_PlayerA" nil "main_PlayerB" nil)
                                          :next_player_to_act "PlayerA")
                                   (add-dialog "Chair" "The 'Aye's have it. The motion to vote on the main motion carries. We will now vote on the main motion: 'PlayerA gets 50 coins, PlayerB gets 50 coins'. All in favor, say 'Aye'. All opposed, 'Nay'. PlayerA?"))}))
           (fn [gs] ; Turn 13: PlayerA votes Aye (main)
             (json/write-str
              {:utterance "Aye!"
               :new_game_state (-> gs
                                   (assoc-in [:votes "main_PlayerA"] "Aye")
                                   (assoc :next_player_to_act "PlayerB")
                                   (add-dialog "PlayerA" "Aye!"))}))
           (fn [gs] ; Turn 14: PlayerB votes Aye (main)
             (json/write-str
              {:utterance "Aye!"
               :new_game_state (-> gs
                                   (assoc-in [:votes "main_PlayerB"] "Aye")
                                   (assoc :next_player_to_act "Chair")
                                   (add-dialog "PlayerB" "Aye!"))}))
           (fn [gs] ; Turn 15: Chair announces main passed, adjourns
             (json/write-str
              {:utterance "The 'Aye's have it! The motion 'PlayerA gets 50 coins, PlayerB gets 50 coins' is carried. PlayerA is allotted 50 coins, and PlayerB is allotted 50 coins. This concludes our agenda. The meeting is adjourned."
               :new_game_state (-> gs
                                   (assoc :current_phase "Adjourned"
                                          :current_motion (assoc (:current_motion gs) :status "passed")
                                          :allotted_coins {"PlayerA" 50 "PlayerB" 50}
                                          :next_player_to_act nil) ; Game over
                                   (add-dialog "Chair" "The 'Aye's have it! The motion 'PlayerA gets 50 coins, PlayerB gets 50 coins' is carried. PlayerA is allotted 50 coins, and PlayerB is allotted 50 coins. This concludes our agenda. The meeting is adjourned."))}))
           ]))


(defn call-model [model-name prompt-string]
  (println (str "\n;; --- Calling LLM: " model-name " ---"))
  ;; (println ";; Prompt:\n" prompt-string) ;; Uncomment to see full prompts
  (Thread/sleep 50) ;; Simulate network latency

  (if (= model-name planner-model-name)
    simulated-planner-response-json
    (let [current-game-state-json (second (str/split prompt-string #"Current Game State:\n"))
          current-game-state-map (when current-game-state-json
                                   (try (json/read-str current-game-state-json :key-fn keyword)
                                        (catch Exception e
                                          (println "Error parsing game state in call-model for player: " (.getMessage e))
                                          nil)))
          response (get-simulated-player-response current-game-state-map)]
      (if response
        response
        (do (println "WARN: No more simulated responses. Falling back to generic player response.")
            (json/write-str {:utterance (str "Player " (:next_player_to_act current-game-state-map) " is thinking...")
                             :new_game_state (assoc current-game-state-map :next_player_to_act nil)}))))))


;; --- JSON Parsing Helpers ---
(defn parse-json-str [json-str]
  (try
    (json/read-str json-str :key-fn keyword)
    (catch Exception e
      (println (str "Error parsing JSON string: " (.getMessage e) "\nString was: " json-str))
      nil)))

;; --- Director Logic ---

(defn plan-game [planner-prompt]
  (println "--- Starting Planning Phase ---")
  (println "Director: Sending prompt to Planner model (" planner-model-name ")...")
  (let [planner-response-str (call-model planner-model-name planner-prompt)
        planner-output (parse-json-str planner-response-str)]
    (if planner-output
      (do
        (println "Director: Received and parsed planner output.")
        (println "Director: Game Title - " (:game_title planner-output))
        planner-output)
      (do
        (println "Director: Failed to parse planner output. Exiting.")
        nil))))

(defn play-game [initial-game-state player-instructions]
  (println "\n--- Starting Play Phase ---")
  (loop [current-game-state initial-game-state
         turn-number 1]
    (println (str "\n--- Turn " turn-number " ---"))
    (let [next-player-id (get current-game-state :next_player_to_act)]
      (if (nil? next-player-id)
        (do
          (println "Director: Game Over!")
          (println "Final Game State Highlights:")
          (println "  Phase:" (:current_phase current-game-state))
          (println "  Allotted Coins:" (:allotted_coins current-game-state))
          (let [last-dialog (last (:dialog_history current-game-state))]
            (when last-dialog
              (println (str "  Final Utterance (" (:speaker last-dialog) "): " (:line last-dialog)))))
          current-game-state)

        (let [player-instr (get player-instructions (keyword next-player-id) ; Ensure keyword for lookup
                               (get player-instructions next-player-id)) ; Fallback for string key
              _ (assert player-instr (str "No instruction found for player: " next-player-id))]
          (println (str "Director: It's " next-player-id "'s turn."))

          (let [player-model default-player-model-name ; Could be more dynamic based on roster/planner spec
                player-prompt-string (str player-instr
                                          "\n\nCurrent Game State:\n"
                                          (json/write-str current-game-state))]

            (println (str "Director: Sending prompt to " next-player-id " (" player-model ")..."))
            (let [player-response-str (call-model player-model player-prompt-string)
                  player-response (parse-json-str player-response-str)]

              (if player-response
                (let [utterance (:utterance player-response)
                      new-game-state (:new_game_state player-response)]
                  (println (str "Director: " next-player-id " says: \"" utterance "\""))
                  (recur new-game-state (inc turn-number)))
                (do
                  (println (str "Director: Failed to parse response from " next-player-id ". Game cannot continue."))
                  current-game-state)))))))))


(defn -main [& args]
  (println "=== Welcome to the LLM Game Director ===")
  (initialize-simulated-responses!)

  (if-let [planner-data (plan-game human-written-planner-prompt)]
    (let [initial-state (:initial_game_state planner-data)
          instructions (:player_instructions planner-data)]
      (if (and initial-state instructions)
        (play-game initial-state instructions)
        (println "Director: Could not extract initial state or instructions from planner. Halting.")))
    (println "Director: Planning phase failed. Halting."))

  (println "\n=== Director Finished ==="))

```

---
**How to Run:**

1.  Save `deps.edn` in your project's root directory.
2.  Save the Clojure code as `src/director/core.clj`.
3.  Ensure you have Clojure CLI tools installed.
4.  From your project's root directory, run:
    ```bash
    clojure -M -m director.core
    ```

**Explanation and Key Parts:**

1.  **`llm-roster`, `planner-model-name`, `default-player-model-name`:** Basic configuration.
2.  **`human-written-planner-prompt`:** The prompt you provided for the planner.
3.  **`simulated-planner-response-json`:** A hardcoded JSON string that the *simulated* planner will return. This defines the game's initial state and player instructions.
4.  **`game-turn-responses` & `initialize-simulated-responses!`:** This is the core of the simulation for player turns. `initialize-simulated-responses!` populates a queue with functions. Each function, when called, generates the JSON response for a specific turn of the Roberts Rules game, updating the state as per the example.
5.  **`call-model` (Simulated):**
    *   If called for the `planner-model-name`, it returns `simulated-planner-response-json`.
    *   If called for a player model, it dequeues a response function from `game-turn-responses`, calls it with the current game state, and returns the resulting JSON string. This allows the game to play out turn-by-turn.
    *   It includes a `Thread/sleep` to simulate network latency.
    *   It attempts to parse the `current_game_state` from the player's prompt to pass to the response generator function.
6.  **`parse-json-str`:** A helper to parse JSON, using keywords for map keys.
7.  **`plan-game`:**
    *   Calls the planner model.
    *   Parses its response.
    *   Prints some info.
8.  **`play-game`:**
    *   The main game loop. It continues as long as `next_player_to_act` in the `current-game-state` is not `nil`.
    *   Determines the current player and their instructions.
    *   Constructs the prompt for the player (instruction + current game state JSON).
    *   Calls the player model.
    *   Parses the player's response (`utterance` and `new_game_state`).
    *   Narrates the player's utterance.
    *   Updates `current-game-state` with `new_game_state`.
    *   Recurs to the next turn.
9.  **`-main`:**
    *   Initializes the simulated responses.
    *   Calls `plan-game`.
    *   If planning is successful, calls `play-game`.

This setup provides a fully runnable simulation of your described system, playing out the example game. To use it with actual LLMs, you would replace the `call-model` function with one that makes real HTTP requests to your LiteLLM endpoint. You would also remove the `simulated-planner-response-json` and `game-turn-responses` simulation logic, relying instead on the LLMs to generate the content.
