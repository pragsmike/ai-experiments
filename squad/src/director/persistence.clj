(ns director.persistence
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [director.util :as util])) ; For sanitize-filename and trusted JSON parsing

(def ^:private game-designs-base-dir "game_designs")

(defn get-game-design-dir-path [game-title-or-id]
  (let [sanitized-title (util/sanitize-filename (or game-title-or-id "unknown_game"))]
    (io/file game-designs-base-dir sanitized-title)))

(defn save-game-design! [game-design-dir planner-output]
  (try
    (.mkdirs game-design-dir)
    (let [{:keys [game_title game_id initial_game_state player_instructions planner_model]} planner-output
          players (keys player_instructions)]

      (spit (io/file game-design-dir "initial_state.json") (json/write-str initial_game_state {:escape-unicode false :escape-slash false}))
      (println "Saved initial_state.json to" (str game-design-dir))

      (doseq [[player-id instruction] player_instructions]
        (let [player-file-name (str (util/sanitize-filename (name player-id)) ".txt")]
          (spit (io/file game-design-dir player-file-name) instruction)
          (println "Saved" player-file-name "to" (str game-design-dir))))

      (let [meta-data {:game_title game_title
                       :game_id game_id
                       :players (map name players)
                       :planner_model planner_model}] ; Storing which model planned it
        (spit (io/file game-design-dir "game_meta.json") (json/write-str meta-data {:escape-unicode false :escape-slash false}))
        (println "Saved game_meta.json to" (str game-design-dir)))
      true)
    (catch Exception e
      (println "Error saving game design:" (.getMessage e))
      false)))

(defn- parse-json-file-local [filepath error-context] ; Renamed to avoid conflict if core also had it
  (try
    (let [content (slurp filepath)]
      (util/parse-json-from-string-trusted content (str error-context " from file " filepath)))
    (catch Exception e
      (println (str "Error reading or parsing JSON file " filepath " for " error-context ": " (.getMessage e)))
      {:error (str "File read or JSON parsing error: " (.getMessage e)) :filepath filepath})))


(defn load-game-design [game-design-dir]
  (try
    (println "Attempting to load game design from" (str game-design-dir))
    (let [meta-file (io/file game-design-dir "game_meta.json")
          initial-state-file (io/file game-design-dir "initial_state.json")]
      (if (and (.exists meta-file) (.exists initial-state-file))
        (let [meta-data (parse-json-file-local meta-file "Game Meta")
              initial-state (parse-json-file-local initial-state-file "Initial State")]
          (if (or (:error meta-data) (:error initial-state))
            (do (println "Error loading meta or initial state from files.") nil)
            (let [player-ids (:players meta-data)
                  player-instructions (into {}
                                            (map (fn [player-id-str]
                                                   (let [player-id (keyword player-id-str) ; Convert back to keyword for consistency
                                                         instr-file (io/file game-design-dir (str (util/sanitize-filename player-id-str) ".txt"))]
                                                     (if (.exists instr-file)
                                                       [player-id (slurp instr-file)]
                                                       (do (println "Warning: Instruction file not found for" player-id-str)
                                                           [player-id "ERROR: Instruction not found"]))))
                                                 player-ids))]
              (println "Successfully loaded game design from files.")
              {:game_title (:game_title meta-data)
               :game_id (:game_id meta-data)
               :initial_game_state initial-state
               :player_instructions player-instructions
               :loaded_from_file true})))
        (do (println "Meta or initial state file not found in" (str game-design-dir)) nil)))
    (catch Exception e
      (println "Error loading game design from directory " (str game-design-dir) ":" (.getMessage e))
      nil)))

(defn load-planner-prompt [filepath]
  (try (slurp filepath)
       (catch Exception e
         (println (str "Error reading planner prompt file " filepath ": " (.getMessage e)))
         nil)))

(defn prompt-file->game_id [prompt-file]
  (-> prompt-file (io/file) (.getName) (clojure.string/split #"\.") first))

(defn game_id->design-dir [game-id]
  (get-game-design-dir-path game-id))

(defn prompt-file->design-dir [prompt-file]
  (-> prompt-file prompt-file->game_id game_id->design-dir))

(defn design-exists? [prompt-file]
  (-> prompt-file prompt-file->design-dir io/file .exists))

(defn load-design [prompt-file]
  (let [game-design-dir (prompt-file->design-dir prompt-file)
        loaded-design (load-game-design game-design-dir)]
    (assoc loaded-design :game_design_dir game-design-dir)))

(defn save-design! [out]
  (let [game-design-dir (get-game-design-dir-path (:game_id out))]
    (save-game-design! game-design-dir out)))

