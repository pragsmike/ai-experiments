(ns director.play-test
  (:require [clojure.test :refer :all]
            [director.play :as play]
            [director.util :as u]
            [clojure.data.json :as json]))

;; --- Mock Data and State for Play Tests ---

(def ^:private sample-player-model-name "mock-player-mistral")

(def ^:private sample-instructions
  {:PlayerA "Instructions for Player A: Be assertive."
   :PlayerB "Instructions for Player B: Be cooperative."
   :Chair "Instructions for Chair: Facilitate."})

(def ^:private initial-game-state-turn1
  {:game_id "play_test_g1"
   :description "A simple game for testing play."
   :dialog_history [{:speaker "System" :line "Game Start."}]
   :next_player_to_act "PlayerA"
   :turn_count 1
   :other_data "initial"})

;; --- Mocking Utilities for Play Tests (Definitions moved up) ---

(defonce ^:private mock-player-llm-calls (atom []))
(defonce ^:private mock-player-response-queue (atom []))

(defn- reset-play-mocks! []
  (reset! mock-player-llm-calls [])
  (reset! mock-player-response-queue []))

(defn- record-player-llm-call! [& args]
  (swap! mock-player-llm-calls conj (vec args)))

(defn- prime-player-responses! [responses]
  ;; It's good practice to reset calls when priming responses for a new scenario generally,
  ;; but the fixture already does reset-play-mocks! for the whole atom.
  ;; This function just sets the response queue.
  (reset! mock-player-response-queue (vec responses)))

;; Corrected mock-player-call-model-fn, defined AFTER its helpers
(defn mock-player-call-model-fn [model-name prompt-string] ; Now takes 2 arguments
  (record-player-llm-call! model-name prompt-string) ; This will now resolve
  (if-let [response (first @mock-player-response-queue)]
    (do
      (swap! mock-player-response-queue rest) ; Consume response
      (if (fn? response)
        (response prompt-string)
        response))
    (do
      (println "ERROR (Mock): Player LLM mock ran out of responses!")
      (json/write-str {:error "Mock ran out of responses"
                       :utterance "Mock default: I am confused."
                       :new_game_state (assoc initial-game-state-turn1 :next_player_to_act nil)}))))

(defn with-play-mocks-fixture [f]
  (reset-play-mocks!) ; Resets both calls and queue atom states
  (f))

(use-fixtures :each with-play-mocks-fixture)

;; --- Test Scenarios ---
;; (The deftest blocks remain the same as the version where I corrected
;; the calls to play/execute-play-phase to have 4 arguments.
;; I'll include one for completeness of this file.)

(deftest execute-play-phase-simple-two-turn-game
  (testing "A simple two-turn game that ends correctly"
    (let [p-a-state-update (assoc initial-game-state-turn1
                                  :dialog_history [{:speaker "System" :line "Game Start."}
                                                   {:speaker "PlayerA" :line "Player A acts."}]
                                  :next_player_to_act "PlayerB"
                                  :turn_count 2
                                  :other_data "PlayerA_acted")
          p-b-state-update (assoc p-a-state-update
                                  :dialog_history (conj (:dialog_history p-a-state-update)
                                                        {:speaker "PlayerB" :line "Player B ends it."})
                                  :next_player_to_act nil ; End game
                                  :turn_count 3
                                  :other_data "PlayerB_acted")]
      (prime-player-responses!
       [(json/write-str {:utterance "Player A acts." :new_game_state p-a-state-update})
        (json/write-str {:utterance "Player B ends it." :new_game_state p-b-state-update})])

      (let [final-state (play/execute-play-phase initial-game-state-turn1
                                                 sample-instructions
                                                 mock-player-call-model-fn
                                                 sample-player-model-name)]
        (is (= p-b-state-update final-state) "Final game state should match Player B's update")
        (is (= 2 (count @mock-player-llm-calls)) "Should have been two LLM calls")

        (let [[call1 call2] @mock-player-llm-calls]
          (is (= sample-player-model-name (first call1)) "Player A model name correct")
          (is (clojure.string/includes? (second call1) (:PlayerA sample-instructions)))
          (is (clojure.string/includes? (second call1) "\"next_player_to_act\":\"PlayerA\""))

          (is (= sample-player-model-name (first call2)) "Player B model name correct")
          (is (clojure.string/includes? (second call2) (:PlayerB sample-instructions)))
          (is (clojure.string/includes? (second call2) "\"next_player_to_act\":\"PlayerB\""))
          )))))

(deftest execute-play-phase-malformed-llm-response
  (testing "Handles malformed JSON response from player LLM"
    (prime-player-responses! ["this is not valid json { definitely not"])
    (let [final-state (play/execute-play-phase initial-game-state-turn1
                                               sample-instructions
                                               mock-player-call-model-fn
                                               sample-player-model-name)]
      (is (= initial-game-state-turn1 final-state) "Game state should revert to before the error")
      (is (= 1 (count @mock-player-llm-calls)) "LLM should have been called once"))))

(deftest execute-play-phase-llm-response-missing-keys
  (testing "Handles LLM response that is valid JSON but missing :utterance or :new_game_state"
    (prime-player-responses! [(json/write-str {:utterance "Player A tries..."})])
    (let [final-state (play/execute-play-phase initial-game-state-turn1
                                               sample-instructions
                                               mock-player-call-model-fn
                                               sample-player-model-name)]
      (is (= initial-game-state-turn1 final-state) "Game should halt, state should be pre-error")
      (is (= 1 (count @mock-player-llm-calls))))))

(deftest execute-play-phase-new-game-state-not-a-map
  (testing "Handles LLM response where :new_game_state is not a map"
    (prime-player-responses! [(json/write-str {:utterance "Player A does something weird."
                                               :new_game_state "I am a string, not a map!"})])
    (let [final-state (play/execute-play-phase initial-game-state-turn1
                                               sample-instructions
                                               mock-player-call-model-fn
                                               sample-player-model-name)]
      (is (= initial-game-state-turn1 final-state) "Game should halt, state should be pre-error")
      (is (= 1 (count @mock-player-llm-calls))))))

(deftest execute-play-phase-no-instructions-for-player
  (testing "Throws AssertionError if no instructions found for next player"
    (let [state-no-instr (assoc initial-game-state-turn1 :next_player_to_act "PlayerC")]
      (is (thrown? AssertionError
                   (play/execute-play-phase state-no-instr
                                            sample-instructions
                                            mock-player-call-model-fn
                                            sample-player-model-name))))))

(deftest execute-play-phase-narration-captured
  (testing "Captures narration output from println"
    (let [p-a-state-update (assoc initial-game-state-turn1 :next_player_to_act nil)]
      (prime-player-responses!
       [(json/write-str {:utterance "Player A says hello and finishes." :new_game_state p-a-state-update})])
      (let [narration (with-out-str
                        (play/execute-play-phase initial-game-state-turn1
                                                 sample-instructions
                                                 mock-player-call-model-fn
                                                 sample-player-model-name))]
        (is (clojure.string/includes? narration "Director: PlayerA says: \"Player A says hello and finishes.\""))
        ))))

(deftest execute-play-phase-game-ends-if-next-player-is-nil-in-state
  (testing "Game ends if :next_player_to_act becomes nil in returned new_game_state"
    (let [state-ends-game (assoc initial-game-state-turn1 :next_player_to_act nil)]
      (prime-player-responses!
       [(json/write-str {:utterance "Player A takes the final turn."
                         :new_game_state state-ends-game})])
      (let [final-state (play/execute-play-phase initial-game-state-turn1
                                                 sample-instructions
                                                 mock-player-call-model-fn
                                                 sample-player-model-name)]
        (is (= state-ends-game final-state))
        (is (= 1 (count @mock-player-llm-calls)))))))

(deftest execute-play-phase-game-ends-if-next-player-is-empty-string
  (testing "Game ends if :next_player_to_act becomes an empty string"
    (let [state-ends-game (assoc initial-game-state-turn1 :next_player_to_act "")]
      (prime-player-responses!
       [(json/write-str {:utterance "Player A makes next player empty string."
                         :new_game_state state-ends-game})])
      (let [final-state (play/execute-play-phase initial-game-state-turn1
                                                 sample-instructions
                                                 mock-player-call-model-fn
                                                 sample-player-model-name)]
        (is (= state-ends-game final-state))
        (is (= 1 (count @mock-player-llm-calls)))))))
