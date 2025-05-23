(ns director.core
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            ;; Removed clojure.string as str as it's not directly used here anymore
            [director.simulation :as sim]
            [director.llm-interface :as llm-iface]
            [director.planning :as planning]
            [director.play :as play]
            ;; director.util is not directly used by core, but by other director namespaces
            ;; director.persistence is not directly used by core for loading prompts anymore
            )
  (:gen-class))

;; --- Configuration ---
(defonce ^:private game-designs-actual-base-dir "game_designs") ; The actual base directory

(defonce ^:private default-planner-prompt-file "planner_prompts/roberts_rules_planner_prompt.txt")

(defonce ^:private planner-model-name-config "openai/gpt-3.5-turbo") ; Was openai/turbo
(defonce ^:private default-player-model-name-config "ollama/mistral")

;; --- Dynamic var for LLM call function ---
(defonce ^:dynamic *call-model-fn* llm-iface/real-call-model)

;; --- CLI Option Parsing ---
(def ^:private cli-options
  [["-p" "--planner-prompt FILE" "Path to the planner prompt file"
    :default default-planner-prompt-file
    :validate [#(.exists (io/file %)) "Planner prompt file must exist"]]
   ["-s" "--simulate" "Use simulated LLM agents instead of real ones"]
   ["-f" "--force-plan" "Force regeneration of game design files, even if they exist"]
   ["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [options summary errors]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) (do (println "LLM Game Director Options:\n" summary) (System/exit 0))
      errors (do (println "Error(s) parsing options:\n" (clojure.string/join \newline errors)) (System/exit 1)))

    (println "=== Welcome to the LLM Game Director ===")
    (let [planner-prompt-filepath (:planner-prompt options)]
      (println "Using planner prompt from:" planner-prompt-filepath)
      (when (:force-plan options) (println "INFO: Forcing re-planning of game design."))

      (if (:simulate options)
        (do (println "INFO: Using SIMULATED LLM calls.")
            (alter-var-root #'*call-model-fn* (constantly sim/simulated-call-model))
            (sim/initialize-simulated-game!))
        (do (println "INFO: Using REAL LLM calls via LiteLLM.")
            (alter-var-root #'*call-model-fn* (constantly llm-iface/real-call-model))))

      ;; Pass the actual base directory to the planning phase
      (if-let [planner-data (planning/execute-planning-phase
                             game-designs-actual-base-dir ; Pass base dir
                             planner-prompt-filepath
                             (:force-plan options)
                             *call-model-fn*
                             planner-model-name-config)]
        (let [initial-state (:initial_game_state planner-data)
              instructions (:player_instructions planner-data)]
          (if (and (map? initial-state) (seq initial-state)
                   (map? instructions) (seq instructions)
                   (:next_player_to_act initial-state))
            (play/execute-play-phase
             initial-state
             instructions
             *call-model-fn*
             default-player-model-name-config
             planner-model-name-config) ; Pass planner model name in case call-model needs it for some reason
            (println "Director: Planner data was invalid or incomplete. Halting.")))
        (println "Director: Planning phase failed. Halting.")))
    (println "\n=== Director Finished ===")))
