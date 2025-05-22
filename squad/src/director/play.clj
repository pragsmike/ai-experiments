(ns director.play
  (:require [clojure.data.json :as json]
            [director.util :as util]))

(defn execute-play-phase [initial-game-state
                          player-instructions
                          call-model-fn
                          default-player-model-name-cfg
                          planner-model-name-cfg] ; Passed to call-model-fn
  (println "\n--- Starting Play Phase ---")
  (loop [current-game-state initial-game-state
         turn-number 1]
    (println (str "\n--- Turn " turn-number " ---"))
    (let [next-player-id-anycase (get current-game-state :next_player_to_act)
          ;; Normalize player ID to string for consistent lookup, then keyword for map access
          next-player-id-str (when next-player-id-anycase (name (keyword next-player-id-anycase)))]
      (if (or (nil? next-player-id-str) (empty? next-player-id-str))
        (do (println "Director: Game Over! (No next player specified or game concluded)")
            (println "Final Game State Highlights:") (println "  Phase:" (:current_phase current-game-state))
            (println "  Allotted Coins:" (:allotted_coins current-game-state))
            (let [last-dialog (last (:dialog_history current-game-state))]
              (when last-dialog (println (str "  Final Utterance (" (:speaker last-dialog) "): " (:line last-dialog)))))
            current-game-state)

        (let [player-id-kw (keyword next-player-id-str)
              player-instr (get player-instructions player-id-kw)
              _ (when-not player-instr
                  (throw (AssertionError. (str "No instruction found for player: " next-player-id-str
                                               " (keyword: " player-id-kw "). Available instructions for players: "
                                               (map name (keys player-instructions))))))]
          (println (str "Director: It's " next-player-id-str "'s turn."))
          (let [player-model default-player-model-name-cfg
                player-prompt-string (str player-instr "\n\nCurrent Game State:\n" (json/write-str current-game-state))]
            (println (str "Director: Sending prompt to " next-player-id-str " (" player-model ")..."))
            (let [player-response-str (call-model-fn player-model player-prompt-string planner-model-name-cfg) ; Pass planner_model_name for call-model's logic if needed
                  player-response (util/parse-data-from-llm-response player-response-str (str next-player-id-str "'s Response"))]
              (if (and player-response (:utterance player-response) (:new_game_state player-response) (not (:error player-response)))
                (let [utterance (:utterance player-response) new-game-state (:new_game_state player-response)]
                  (println (str "Director: " next-player-id-str " says: \"" utterance "\""))
                  (if (map? new-game-state)
                     (recur new-game-state (inc turn-number))
                     (do (println (str "Director: ERROR! Player " next-player-id-str " returned an invalid new_game_state (not a map). Game cannot continue."))
                         (println "Received new_game_state: " (pr-str new-game-state))
                         current-game-state)))
                (do (println (str "Director: Failed to parse response from " next-player-id-str ", player returned an error, or response malformed. Game cannot continue."))
                    (when (:raw player-response) (println "Player raw response was logged (first 200 chars):" (subs (:raw player-response) 0 (min 200 (count (:raw player-response))))))
                    current-game-state)))))))))
