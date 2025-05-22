(ns director.core
  (:require [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [clojure.string :as str]
            [director.simulation :as sim]
            [director.llm-interface :as llm-iface]
            [director.planning :as planning]
            [director.play :as play]
            [director.util :as util]) ; Though not directly used here, good to have if future utils are needed by core
  (:gen-class))

;; --- Configuration ---
(defonce ^:private default-planner-prompt-file "planner_prompts/roberts_rules_planner_prompt.txt")

(defonce ^:private planner-model-name-config "openai/turbo")
(defonce ^:private default-player-model-name-config "ollama/mistral") ; Example, adjust as needed

;; --- Dynamic var for LLM call function ---
(defonce ^:dynamic *call-model-fn* llm-iface/real-call-model) ; Default to real LLMs

;; --- CLI Option Parsing ---
(def ^:private cli-options
  [["-p" "--planner-prompt FILE" "Path to the planner prompt file"
    :default default-planner-prompt-file
    :validate [#(.exists (io/file %)) "Planner prompt file must exist"]]
   ["-s" "--simulate" "Use simulated LLM agents instead of real ones"]
   ["-f" "--force-plan" "Force regeneration of game design files, even if they exist"]
   ["-h" "--help"]])

(defn- load-planner-prompt [filepath]
  (try (slurp filepath)
       (catch Exception e
         (println (str "Error reading planner prompt file " filepath ": " (.getMessage e)))
         nil)))

(defn -main [& args]
  (let [{:keys [options summary errors]} (cli/parse-opts args cli-options)]
    (cond
      (:help options) (do (println "LLM Game Director Options:\n" summary) (System/exit 0))
      errors (do (println "Error(s) parsing options:\n" (clojure.string/join \newline errors)) (System/exit 1)))

    (println "=== Welcome to the LLM Game Director ===")
    (println "Using planner prompt from:" (:planner-prompt options))
    (when (:force-plan options) (println "INFO: Forcing re-planning of game design."))

    (if (:simulate options)
      (do (println "INFO: Using SIMULATED LLM calls.")
          (alter-var-root #'*call-model-fn* (constantly sim/simulated-call-model))
          (sim/initialize-simulated-game!))
      (do (println "INFO: Using REAL LLM calls via LiteLLM.")
          (alter-var-root #'*call-model-fn* (constantly llm-iface/real-call-model))))

    (if-let [planner-prompt-text (load-planner-prompt (:planner-prompt options))]
      (if-let [planner-data (planning/execute-planning-phase
                             planner-prompt-text
                             (:force-plan options)
                             *call-model-fn*
                             planner-model-name-config
                             (:planner-prompt options))] ; Pass original prompt filename for guessing dir
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
             planner-model-name-config) ; Pass planner model name in case call-model needs it
            (println "Director: Planner output was invalid or incomplete. Halting.")))
        (println "Director: Planning phase failed or could not load/generate design. Halting."))
      (println "Director: Could not load planner prompt. Halting."))

    (println "\n=== Director Finished ===")))
