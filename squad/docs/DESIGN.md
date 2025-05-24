Okay, I will reproduce the system design document, incorporating the refinements and clarifications we've made throughout our conversation. This version will reflect the multi-file structure, the command-line options, the persistence mechanism, and the robust JSON/EDN parsing for LLM responses.

---

**System Design Document: LLM-Driven Game Simulator**

**1. Overview**

This document describes a Clojure application designed to orchestrate Large Language Model (LLM) instances for designing and playing text-based games. The system comprises a **director** program that manages interactions between a **planner agent** (an LLM responsible for game design) and multiple **player agents** (LLMs that play the designed game).

The models are accessed via HTTP through a single LiteLLM endpoint (assumed to be running at `http://localhost:8000/chat/completions`). Models have no inherent memory; all necessary context must be provided in each request.

The system aims to:
1.  Use a planner agent to design a game, including rules, roles, initial state, and player-specific instructions.
2.  Persist the designed game artifacts to disk.
3.  Allow one or more "plays" of the designed game, where player agents take turns according to the rules.
4.  Support both real LLM interactions and a simulated mode for testing and development.

**2. Core Components and Modules**

The application is structured into several Clojure namespaces:

*   **`director.core`**: The main entry point and orchestrator. Handles command-line interface (CLI) parsing, sets up the environment (real vs. simulated LLMs), and sequences the planning and play phases by calling functions from other modules.
*   **`director.llm-interface`**: Provides the `real-call-model` function for interacting with the actual LiteLLM endpoint.
*   **`director.simulation`**: Provides `simulated-call-model` for running with pre-canned game scenarios and player responses. Includes `initialize-simulated-game!` to set up the simulation state.
*   **`director.util`**: Contains utility functions, notably for robust parsing of LLM responses (handling JSON/EDN, preambles, backticks) and filename sanitization.
*   **`director.persistence`**: Manages saving and loading game designs to/from the filesystem. Handles directory structures and file formats for game artifacts.
*   **`director.planning`**: Encapsulates the logic for the game planning phase. Interacts with `director.persistence` to load or save designs and calls the designated `*call-model-fn*` for the planner agent.
*   **`director.play`**: Encapsulates the logic for the game playing phase. Manages the game loop, player turns, state updates, and calls the designated `*call-model-fn*` for player agents.

**3. Data Objects and Formats**

*   **Planner Prompt (Text File):** A human-written prompt instructing the planner LLM on how to design the game. The path to this file is configurable via CLI.
    *   *Default:* `planner_prompts/roberts_rules_planner_prompt.txt`
*   **Planner Output (Internal Data / Persisted as JSON & Text):**
    The conceptual output from the planner (either generated or loaded). This is not a single monolithic object passed around after initial planning, but rather its components are saved/loaded separately.
    *   `game_title` (String)
    *   `game_id` (String)
    *   `initial_game_state` (JSON Object): The complete starting state of the game. Persisted as `initial_state.json`. Must include `next_player_to_act` and `dialog_history`.
    *   `player_instructions` (Map: PlayerID (Keyword) -> Instruction (String)): Instructions for each player. Each instruction string is saved to `[PlayerID_sanitized].txt`.
    *   `planner_model` (String): The name of the LLM used as the planner for this design.
*   **Game Meta File (`game_meta.json`):** Persisted by `director.persistence`. Contains:
    *   `game_title` (String)
    *   `game_id` (String)
    *   `players` (Array of Strings - player IDs)
    *   `planner_model` (String)
*   **LLM Call Function (`*call-model-fn*`):** A dynamic var in `director.core` holding the function to call an LLM. This function takes two arguments: `model-name` (String) and `prompt-string` (String), and returns the LLM's raw response string.
*   **Player Input (String):** The prompt sent by the Director to a player LLM. This combines the player's specific instruction string and the current `game_state` (serialized to a JSON string).
*   **Player Response (LLM Raw String -> Parsed Map):** The raw string response from a player LLM. `director.util/parse-data-from-llm-response` attempts to parse this into a Clojure map of the form:
    ```clojure
    {:utterance "Player's spoken line for the turn."
     :new_game_state {;; complete, updated game state as a Clojure map (from JSON/EDN)
                      :game_id "...",
                      ;; ... other game state fields ...
                      :dialog_history [{:speaker "...", :line "..."}, ...],
                      :next_player_to_act "NextPlayerID_or_null"
                      }}
    ```
*   **Game State (Clojure Map, derived from JSON/EDN):** The current state of the game, represented as a Clojure map. Includes all relevant information: board positions, scores, active motions, `dialog_history`, and `next_player_to_act`. The game ends when `next_player_to_act` is `nil`.

**4. Workflow**

**4.1. Initialization (`director.core`)**
1.  Parse CLI options:
    *   `-p, --planner-prompt FILE`: Path to planner prompt (default: `planner_prompts/roberts_rules_planner_prompt.txt`).
    *   `-s, --simulate`: Use simulated agents.
    *   `-f, --force-plan`: Force regeneration of game design, overwriting existing files.
    *   `-h, --help`: Display help.
2.  Set `*call-model-fn*` to either `sim/simulated-call-model` (and call `sim/initialize-simulated-game!`) or `llm-iface/real-call-model` based on the `-s` flag.
3.  Define `game-designs-actual-base-dir` (e.g., "game_designs").

**4.2. Planning Phase (`director.planning`, orchestrated by `director.core`)**
1.  `planning/execute-planning-phase` is called with `base-dir`, `planner-prompt-filepath`, `force-plan?`, `*call-model-fn*`, and `planner-model-name-config`.
2.  The planner prompt text is loaded from `planner-prompt-filepath` via `p/load-planner-prompt-text`.
3.  **If `force-plan?` is true OR if `p/design-exists?` (for the given `base-dir` and prompt file) is false:**
    a.  The "generate and save" path is taken.
    b.  The `*call-model-fn*` is invoked with `planner-model-name-config` and the loaded prompt text.
    c.  The planner LLM's raw string response is parsed by `util/parse-data-from-llm-response` into a Clojure map (expected to contain `game_title`, `game_id`, `initial_game_state`, `player_instructions`).
    d.  The `planner-model-name-config` is added to this map as `:planner_model`.
    e.  If parsing is successful and all required keys are present:
        i.  `p/save-planner-output!` is called to persist the artifacts ( `initial_state.json`, `[PlayerID].txt` files for instructions, `game_meta.json`) into a subdirectory within `base-dir` (named after `game_id` or `game_title`).
        ii. The complete planner data map (including `:game_design_dir` and `:loaded_from_file false`) is returned.
    f.  If parsing or validation fails, or saving fails, `nil` is returned.
4.  **Else ( `force-plan?` is false AND design exists):**
    a.  `p/load-design-for-prompt` is called to load the game design from persisted files based on `base-dir` and `planner-prompt-filepath`.
    b.  If loading is successful, the loaded data (a map similar to planner output, with `:loaded_from_file true`) is returned.
    c.  If loading fails, the system falls back to the "generate and save" path (Step 3a).
5.  If the planning phase returns `nil` (due to critical failure), the director halts. Otherwise, it proceeds with the returned `planner-data`.

**4.3. Play Phase (`director.play`, orchestrated by `director.core`)**
1.  `play/execute-play-phase` is called with `initial_game_state`, `player_instructions` (from `planner-data`), `*call-model-fn*`, and `default-player-model-name-config`.
2.  A game loop begins, managed by `current-game-state` (initially `initial_game_state`).
3.  **In each turn:**
    a.  The `next_player_to_act` is determined from `current-game-state`. If `nil`, the game ends.
    b.  The specific instruction string for the current player is retrieved.
    c.  A prompt is constructed for the player LLM, combining its instructions and the JSON string representation of `current-game-state`.
    d.  `*call-model-fn*` is invoked with `default-player-model-name-config` (or a player-specific model if configured) and the constructed prompt.
    e.  The player LLM's raw string response is parsed by `util/parse-data-from-llm-response` into a map `{:utterance "..." :new_game_state {...}}`.
    f.  If parsing is successful and the structure is valid:
        i.  The director narrates the `utterance`.
        ii. `current-game-state` is updated to `:new_game_state` from the player's response.
        iii. The loop continues to the next turn.
    g.  If parsing fails or the response structure is invalid, the game halts with an error.
4.  When the game loop terminates (e.g., `next_player_to_act` is `nil`), the director announces the game's conclusion.

**5. Configuration**

*   **`planner-model-name-config`**: (e.g., `"openai/gpt-3.5-turbo"`) - Model used for planning.
*   **`default-player-model-name-config`**: (e.g., `"ollama/mistral"`) - Default model for players.
*   **`game-designs-actual-base-dir`**: (e.g., `"game_designs"`) - Root directory for storing game designs.
*   **LiteLLM Endpoint**: Hardcoded in `director.llm-interface` as `http://localhost:8000/chat/completions`.

**6. Key LLM Interactions and Requirements**

*   **Planner LLM:**
    *   Input: A detailed text prompt.
    *   Output: A single, valid JSON string parseable into a map containing `game_title`, `game_id`, `initial_game_state` (JSON object), and `player_instructions` (JSON object mapping player IDs to instruction strings). All keys in the generated JSON must be double-quoted strings.
*   **Player LLMs:**
    *   Input: An instruction string and the current game state (as a JSON string).
    *   Output: A single, valid JSON string (or EDN string that `util/parse-data-from-llm-response` can handle) parseable into a map `{:utterance "..." :new_game_state {...}}`. All keys within the JSON parts must be double-quoted strings if outputting JSON. The `new_game_state` must be a complete and valid representation of the game state after the player's turn.

**7. Error Handling (Basic)**

*   File I/O errors are caught and printed.
*   JSON/EDN parsing errors from LLM responses are caught; `util/parse-data-from-llm-response` returns an error map.
*   Planning/Play phases halt if critical data (planner output, player response) is invalid or missing.
*   Arity exceptions or other runtime errors will cause the program to crash.

**8. Future Considerations / Potential Improvements**

*   More sophisticated error handling and retry mechanisms for LLM calls.
*   Schema validation for LLM outputs (planner and player).
*   More flexible configuration for LLM models per player role.
*   A more robust way to link planner prompts to game design directories (beyond filename derivation).
*   Web interface or more interactive CLI.
*   Support for games with non-textual state components.
*   Advanced game state management (e.g., diffs, history traversal beyond dialog).

---

This document should provide a comprehensive overview of the system as it currently stands based on our discussions.
