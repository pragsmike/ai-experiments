(ns director.planning
  (:require [director.persistence :as p]
            [director.util :as util]
            [clojure.java.io :as io]))

(defn execute-planning-phase [base-dir
                              planner-prompt-filepath
                              force-plan?
                              call-model-fn
                              planner-model-name-cfg]
  (println "--- Starting Planning Phase ---")
  (let [planner-prompt-text (p/load-planner-prompt-text planner-prompt-filepath)]
    (if-not planner-prompt-text
      (do (println "Error: Could not load planner prompt text from" planner-prompt-filepath) nil)
      ;; Prompt text loaded, proceed
      (letfn [(generate-and-save-design [] ; Renamed for clarity
                (println "Director: Calling Planner model (" planner-model-name-cfg ") for prompt:" planner-prompt-filepath)
                (let [planner-response-str (call-model-fn planner-model-name-cfg planner-prompt-text planner-model-name-cfg)
                      parsed-llm-output (util/parse-data-from-llm-response planner-response-str "Planner Output")
                      planner-data-with-model (if (and (map? parsed-llm-output) (not (:error parsed-llm-output)))
                                                 (assoc parsed-llm-output :planner_model planner-model-name-cfg)
                                                 parsed-llm-output)]

                  (if (and (map? planner-data-with-model)
                           (not (:error planner-data-with-model))
                           (:game_title planner-data-with-model)
                           (:initial_game_state planner-data-with-model)
                           (:player_instructions planner-data-with-model))
                    (do
                      (println "Director: Received and parsed planner output.")
                      (println "Director: Game Title - " (:game_title planner-data-with-model))
                      (if (p/save-planner-output! base-dir planner-data-with-model planner-model-name-cfg)
                        ;; Return the full planner data including the game_design_dir
                        (assoc planner-data-with-model :game_design_dir (p/prompt-filepath->design-dir-file base-dir planner-prompt-filepath)
                                                       :loaded_from_file false) ; Explicitly mark as not loaded
                        (do (println "Failed to save game design.") nil)))
                    (do
                      (println "Director: Planner output was invalid, incomplete, or an error occurred during parsing.")
                      (when (:raw planner-data-with-model) (println "Planner raw response was logged (first 200 chars):" (subs (:raw planner-data-with-model) 0 (min 200 (count (:raw planner-data-with-model))))))
                      (when (:extracted planner-data-with-model) (println "Extracted string for parsing was:" (:extracted planner-data-with-model)))
                      nil))))]

        ;; Main logic for deciding to load or generate
        (if force-plan?
          (do (println "Forcing re-planning for" planner-prompt-filepath)
              (generate-and-save-design)) ; Directly return the result of generation
          (if (p/design-exists? base-dir planner-prompt-filepath)
            (let [loaded-design (p/load-design-for-prompt base-dir planner-prompt-filepath)]
              (if (and loaded-design (:loaded_from_file loaded-design) (not (:error loaded-design)))
                (do (println "Found and successfully loaded existing design for" planner-prompt-filepath)
                    loaded-design) ; Return loaded design
                (do (println "Existing design found for" planner-prompt-filepath ", but failed to load it properly or it was invalid. Generating new one.")
                    (generate-and-save-design)))) ; Return result of generation
            (do (println "No existing design found for" planner-prompt-filepath ". Generating new one.")
                (generate-and-save-design)))))))) ; Return result of generation
