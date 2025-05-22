(ns director.planning
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [director.persistence :as persistence]
            [director.util :as util]))

(defn execute-planning-phase [planner-prompt-text
                              force-plan?
                              call-model-fn
                              planner-model-name-cfg
                              default-prompt-filename] ; For guessing dir if not forcing
  (println "--- Starting Planning Phase ---")
  (let [call-planner-and-save (fn []
                                (println "Director: Calling Planner model (" planner-model-name-cfg ")...")
                                (let [planner-response-str (call-model-fn planner-model-name-cfg planner-prompt-text planner-model-name-cfg) ; Pass cfg to call-model
                                      planner-output (util/parse-data-from-llm-response planner-response-str "Planner Output")]
                                  (if (and planner-output (not (:error planner-output)) (:game_title planner-output) (:initial_game_state planner-output) (:player_instructions planner-output))
                                    (let [game-design-dir (persistence/get-game-design-dir-path (or (:game_id planner-output) (:game_title planner-output)))]
                                      (println "Director: Received and parsed planner output.")
                                      (println "Director: Game Title - " (:game_title planner-output))
                                      (if (persistence/save-game-design! game-design-dir planner-output planner-model-name-cfg)
                                        (assoc planner-output :game_design_dir game-design-dir)
                                        (do (println "Failed to save game design.") nil)))
                                    (do (println "Director: Planner output was invalid, incomplete, or planner returned an error.")
                                        (when (:raw planner-output) (println "Planner raw response was logged (first 200 chars):" (subs (:raw planner-output) 0 (min 200 (count (:raw planner-output))))))
                                        nil))))]
    (if force-plan?
      (call-planner-and-save)
      (let [;; Simplistic way to guess directory if not forcing plan
            potential-game-title-from-prompt-filename (-> default-prompt-filename (io/file) (.getName) (clojure.string/split #"\.") first)
            game-design-dir (persistence/get-game-design-dir-path potential-game-title-from-prompt-filename)
            loaded-design (persistence/load-game-design game-design-dir)]
        (if loaded-design
          (assoc loaded-design :game_design_dir game-design-dir)
          (do (println "No existing design found or failed to load for" (str game-design-dir) ". Generating new one.")
              (call-planner-and-save)))))))
