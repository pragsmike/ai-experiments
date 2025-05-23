(ns director.planning
  (:require [director.persistence :refer (load-planner-prompt load-design save-design! design-exists?)]
            [director.util :as util]))

(defn execute-planning-phase [prompt-filename
                              force-plan?
                              call-model-fn
                              planner-model-name]

  (println "--- Starting Planning Phase ---")
  (if-let [planner-prompt-text (load-planner-prompt prompt-filename)]
    (let [call-planner-and-save (fn []
                                  (println "Director: Calling Planner model (" planner-model-name ")...")
                                  (let [respstr (call-model-fn planner-model-name planner-prompt-text)
                                        out     (util/parse-data-from-llm-response respstr "Planner Output")
;; Add planner_model to the output map before further processing or saving
                                        out     (if (and out (not (:error out)))
                                                  (assoc out :planner_model planner-model-name)
                                                  out)] ; Pass through 'out' if it's already an error or nil

                                    (if (and out (not (:error out)) (:game_title out) (:initial_game_state out) (:player_instructions out))
                                      (do
                                        (println "Director: Received and parsed planner output.")
                                        (println "Director: Game Title - " (:game_title out))

                                        (if (save-design! out)
                                          out
                                          (do
                                            (println "Failed to save game design.")
                                            nil)))
                                      (do (println "Director: Planner output was invalid, incomplete, or planner returned an error.")
                                          (when (:raw out) (println "Planner raw response was logged (first 200 chars):" (subs (:raw out) 0 (min 200 (count (:raw out))))))
                                          nil))))]
      (if (or force-plan? (not (design-exists? prompt-filename)))
        (call-planner-and-save)
        (let [loaded-design (load-design prompt-filename)]
          (when-not (:loaded? loaded-design)
            (println "No existing design found or failed to load for" (:game-design-dir loaded-design) ". Generating new one.")
            (call-planner-and-save)))))
    (println "Director: Could not load planner prompt. Halting.")))
