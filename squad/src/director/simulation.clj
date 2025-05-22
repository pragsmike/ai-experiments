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
  [model-name prompt-string]
  (println (str "\n;; --- SIMULATED Calling LLM: " model-name " ---"))
  (Thread/sleep 50)

  (if (= model-name "openai/turbo")
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
