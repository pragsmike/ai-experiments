(ns director.persistence
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [director.util :as util]))

(defn get-game-design-dir-file
  "Constructs a File object for a game's design directory within a given base directory."
  [base-dir game-title-or-id]
  (let [sanitized-title (util/sanitize-filename (or game-title-or-id "unknown_game"))]
    (io/file base-dir sanitized-title)))

(defn save-game-design-files!
  "Saves game design artifacts to the specified game-design-dir.
   game-design-dir is the fully resolved directory for this specific game."
  [game-design-dir planner-output planner-model-name-cfg]
  (try
    (.mkdirs game-design-dir)
    (let [{:keys [game_title game_id initial_game_state player_instructions]} planner-output
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
                       :planner_model planner-model-name-cfg}]
        (spit (io/file game-design-dir "game_meta.json") (json/write-str meta-data {:escape-unicode false :escape-slash false}))
        (println "Saved game_meta.json to" (str game-design-dir)))
      true)
    (catch Exception e
      (println "Error saving game design to" (str game-design-dir) ":" (.getMessage e))
      false)))

(defn- parse-json-file-local [filepath error-context]
  (try
    (let [content (slurp filepath)]
      (util/parse-json-from-string-trusted content (str error-context " from file " filepath)))
    (catch Exception e
      (println (str "Error reading or parsing JSON file " filepath " for " error-context ": " (.getMessage e)))
      {:error (str "File read or JSON parsing error: " (.getMessage e)) :filepath filepath})))

(defn load-game-design-from-files
  "Loads a game design from the specified game-design-dir."
  [game-design-dir] ; game-design-dir is the fully resolved directory for this specific game
  (try
    (println "Attempting to load game design from" (str game-design-dir))
    (let [meta-file (io/file game-design-dir "game_meta.json")
          initial-state-file (io/file game-design-dir "initial_state.json")]
      (if (and (.exists meta-file) (.exists initial-state-file))
        (let [meta-data (parse-json-file-local meta-file "Game Meta")
              initial-state (parse-json-file-local initial-state-file "Initial State")]
          (if (or (:error meta-data) (:error initial-state))
            (do (println "Error loading meta or initial state from files in" (str game-design-dir)) nil)
            (let [player-ids (:players meta-data)
                  player-instructions (into {}
                                            (map (fn [player-id-str]
                                                   (let [player-id (keyword player-id-str)
                                                         instr-file (io/file game-design-dir (str (util/sanitize-filename player-id-str) ".txt"))]
                                                     (if (.exists instr-file)
                                                       [player-id (slurp instr-file)]
                                                       (do (println "Warning: Instruction file not found for" player-id-str "in" (str game-design-dir))
                                                           [player-id "ERROR: Instruction not found"]))))
                                                 player-ids))]
              (println "Successfully loaded game design from files in" (str game-design-dir))
              {:game_title (:game_title meta-data)
               :game_id (:game_id meta-data)
               :initial_game_state initial-state
               :player_instructions player-instructions
               :game_design_dir game-design-dir
               :loaded_from_file true})))
        (do (println "Meta or initial state file not found in" (str game-design-dir)) nil)))
    (catch Exception e
      (println "Error loading game design from directory " (str game-design-dir) ":" (.getMessage e))
      nil)))

(defn load-planner-prompt-text [filepath]
  (try (slurp filepath)
       (catch Exception e
         (println (str "Error reading planner prompt file " filepath ": " (.getMessage e)))
         nil)))

(defn prompt-filepath->game-id [prompt-filepath]
  (-> prompt-filepath (io/file) (.getName) (str/split #"\.") first))

;; Helper that now takes base-dir
(defn prompt-filepath->design-dir-file [base-dir prompt-filepath]
  (let [game-id (prompt-filepath->game-id prompt-filepath)]
    (get-game-design-dir-file base-dir game-id)))

(defn design-exists? [base-dir prompt-filepath]
  (.exists (prompt-filepath->design-dir-file base-dir prompt-filepath)))

(defn load-design-for-prompt [base-dir prompt-filepath]
  (let [game-design-dir (prompt-filepath->design-dir-file base-dir prompt-filepath)]
    (load-game-design-from-files game-design-dir)))

(defn save-planner-output! [base-dir planner-output planner-model-name-cfg]
  (let [game-id (or (:game_id planner-output) (:game_title planner-output) "unknown_game_id")
        game-design-dir (get-game-design-dir-file base-dir game-id)]
    (save-game-design-files! game-design-dir planner-output planner-model-name-cfg)))
