(ns director.planning
  (:require [director.persistence :as p]
            [director.util :as util]))

(defn execute-planning-phase [base-dir ; New first argument
                              planner-prompt-filepath
                              force-plan?
                              call-model-fn
                              planner-model-name-cfg]
  (println "--- Starting Planning Phase ---")
  (let [planner-prompt-text (p/load-planner-prompt-text planner-prompt-filepath)]
    (if-not planner-prompt-text
      (do (println "Error: Could not load planner prompt text from" planner-prompt-filepath) nil)
      (let [call-llm-and-save (fn []
                                (println "Director: Calling Planner model (" planner-model-name-cfg ") for prompt:" planner-prompt-filepath)
                                (let [planner-response-str (call-model-fn planner-model-name-cfg planner-prompt-text planner-model-name-cfg)
                                      parsed-llm-output (util/parse-data-from-llm-response planner-response-str "Planner Output")
                                      planner-data (if (and parsed-llm-output (not (:error parsed-llm-output)))
                                                     (assoc parsed-llm-output :planner_model planner-model-name-cfg)
                                                     parsed-llm-output)]
                                  (if (and planner-data (not (:error planner-data))
                                           (:game_title planner-data) (:initial_game_state planner-data) (:player_instructions planner-data))
                                    (do
                                      (println "Director: Received and parsed planner output.")
                                      (println "Director: Game Title - " (:game_title planner-data))
                                      (if (p/save-planner-output! base-dir planner-data planner-model-name-cfg)
                                        (assoc planner-data :game_design_dir (p/prompt-filepath->design-dir-file base-dir planner-prompt-filepath))
                                        (do (println "Failed to save game design.") nil)))
                                    (do (println "Director: Planner output was invalid, incomplete, or planner returned an error.")
                                        (when (:raw planner-data) (println "Planner raw response was logged (first 200 chars):" (subs (:raw planner-data) 0 (min 200 (count (:raw planner-data))))))
                                        nil))))]

        (if force-plan?
          (do (println "Forcing re-planning for" planner-prompt-filepath)
              (call-llm-and-save))
          (if (p/design-exists? base-dir planner-prompt-filepath)
            (do (println "Found existing design for" planner-prompt-filepath ". Loading.")
                (p/load-design-for-prompt base-dir planner-prompt-filepath))
            (do (println "No existing design found for" planner-prompt-filepath ". Generating new one.")
                (call-llm-and-save))))))))
