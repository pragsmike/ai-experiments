Okay, this revision significantly clarifies the role of JSON and the player's output, which is a big step forward. Let's integrate the suggestions.

Here is the rewritten system description, incorporating the feedback.

---

**Revised System Description**

*   **Overview**

    We are creating a Clojure application that will orchestrate a set of LLM models. The models are accessible via HTTP through a single LiteLLM endpoint, which is already set up. Some of the models are hosted in ollama. Others are on OpenAI, Microsoft, or Gemini. The models have no memory; whatever they need to know to act must be supplied to them on each request.

    We are building a program that will conduct the design of a game and then conduct one or more plays of that game. This program is known as the **director**. The LLM models it controls are known as **agents**. These agents carry out the design and plays of the game. The director sequences them and manages communications.

    This system is intended to design and play many different games with different rules. The game will be determined by the prompt given to the **planner agent**, which for the moment we will assume has been pre-written by a human.

    The director operates by deciding which agent should act, sends that agent its instructions and information about the game's current state, and collects the agent's response. It repeats this sequence until the game ends.

    There is one agent known as the **planner**, and a set of agents known as **players**. The planner is typically a larger, relatively expensive model capable of advanced reasoning. The players can be less capable, less expensive models, as they will be playing the designed game, possibly several times, and each play may take several turns.

    First, the planner designs the game based on its prompt. This is done once per game design.
    Then, the players play the game. Each player takes a turn, acting according to the rules embedded in their instructions and the current game state. The game ends at some point, as indicated by the game state (e.g., `next_player_to_act` being null). This set of turns is a **play** of the game. There may be more than one play of the same game design.

*   **Data Objects**

    *   **LLM Roster (JSON or EDN):** Used by the director. Describes available LLM models, their names (as understood by LiteLLM), and potentially capabilities or costs. The director uses this to select models for the planner and players.
        *Example: `{"models": [{"name": "ollama/mistral-large", "capabilities": ["planning", "advanced_reasoning"]}, {"name": "openai/gpt-3.5-turbo", "capabilities": ["player_roleplay", "json_generation"]}]}`*
    *   **Planner Prompt (String):** A human-written prompt that instructs the planner LLM on how to design the game, including the theme, number of players, roles, objectives, and the required output format.
    *   **Planner Output (JSON String):** The response from the planner LLM. This is a single JSON string that, when parsed, yields an object containing:
        *   `initial_game_state (JSON Object)`: The complete initial state of the game, formatted as JSON. This JSON object *must* include a field like `next_player_to_act` indicating whose turn it is. It should also include a `dialog_history` array, initially perhaps with a system message.
        *   `player_instructions (JSON Object)`: A map where keys are player IDs (e.g., "PlayerA", "Chair") and values are string instructions for each respective player.
        *   `game_title (String, Optional)`: A descriptive title for the game.
    *   **Player Instructions (String):** A specific string for each player, generated by the planner. This tells the player their role, objectives, how to interpret the game state, how to behave, and critically, how to formulate their response (including updating the game state JSON).
    *   **Game State (JSON Object):** The current state of the game, initially produced by the planner, then subsequently by the player whose turn it was. This is always a complete JSON object. It includes all relevant information: board positions, scores, active motions, whose turn it is (`next_player_to_act`), and an accumulated `dialog_history` (an array of objects, each like `{"speaker": "PlayerID", "line": "utterance text"}`). The game is over when `next_player_to_act` is `null` or an equivalent terminal state is reached as defined by the game's logic.
    *   **Player Input (String):** The prompt sent by the Director to a player LLM. This will typically be a combination of the player's specific `Player Instruction` string and the `current_game_state` (as a JSON string).
    *   **Player Response (JSON String):** The response from a player LLM. This is a single JSON string that, when parsed, yields an object containing:
        *   `utterance (String)`: The natural language utterance of the player for their turn (e.g., "I move to get 50 coins."). This is used by the Director for narration.
        *   `new_game_state (JSON Object)`: The complete, updated game state after the player's action. The player LLM is responsible for constructing this new state correctly, including updating `dialog_history` by appending its own `utterance`, modifying relevant game-specific fields, and setting `next_player_to_act` for the following turn.

*   **The Planner Agent**

    The planner designs the game. Its task is mechanism design: defining roles, rules, objectives, and the initial setup.
    The Director sends the `Planner Prompt` to the planner LLM.
    The planner LLM responds with the `Planner Output` (a JSON string as described above). This output defines the initial `game_state` (including who acts first and an initial `dialog_history`) and the `player_instructions` for each player role.

*   **The Player Agent**

    On each of its turns, the Director sends the player LLM its specific `Player Instruction` and the `current_game_state` (as a JSON string).
    The player LLM must:
    1.  Interpret its instructions and the current JSON game state.
    2.  Decide on an action/utterance.
    3.  Formulate a natural language `utterance`.
    4.  Construct a `new_game_state` JSON object. This involves:
        *   Copying and appropriately modifying fields from the `current_game_state`.
        *   Appending an object like `{"speaker": "MyPlayerID", "line": "My Utterance"}` to the `dialog_history` array.
        *   Updating any game-specific state fields based on its action (e.g., scores, motion status).
        *   Determining and setting the `next_player_to_act` field for the subsequent turn, or setting it to `null` if its action ends the game.
    5.  Respond with a single JSON string representing the `Player Response` object: `{"utterance": "...", "new_game_state": { ... }}`.

*   **The Director Program (Clojure Application)**

    The director orchestrates the game. It will report the progress of the game in a conversational style for human observers, narrating the player, their `utterance`, and key outcomes of each turn. It won't print the full game state JSON on each turn unless in a debug mode.

    **During the Planning Phase:**
    1.  Is given a human-written `Planner Prompt` (string).
    2.  Selects a suitable planner model from the `LLM Roster`.
    3.  Sends the `Planner Prompt` to the chosen planner model via `(core.call-model model-name planner-prompt-string)`.
    4.  Receives the `Planner Output` (JSON string) from the planner model.
    5.  Parses this JSON string to get the `initial_game_state` object and the map of `player_instructions`. Stores these.

    **During the Play Phase:**
    1.  The `current_game_state` is initially the `initial_game_state` from the planner.
    2.  Loop:
        a.  Inspect `current_game_state.next_player_to_act`. If `null` or indicates a terminal state, the game play ends. The Director announces the final outcome based on the final `current_game_state`.
        b.  Identify the `currentPlayerID = current_game_state.next_player_to_act`.
        c.  Retrieve the `instructionString` for `currentPlayerID` from the stored `player_instructions`.
        d.  Select a suitable player model for `currentPlayerID` (e.g., from `LLM Roster`, or perhaps the planner specified model types).
        e.  Construct the `playerInputString` by combining `instructionString` and the `current_game_state` (serialized to a JSON string). Example: `(str instructionString "\n\nCurrent Game State:\n" (json/write-str current_game_state))`
        f.  Send `playerInputString` to the chosen player model: `(core.call-model player-model-name playerInputString)`.
        g.  Receive the `Player Response` (JSON string).
        h.  Parse this JSON string into a `Player Response` object (e.g., a Clojure map with `:utterance` and `:new_game_state` keys).
        i.  Narrate the turn: "Director: Player [currentPlayerID] says: '[playerResponse.utterance]'".
        j.  Update `current_game_state = playerResponse.new_game_state`.
        k.  Repeat loop.

*   **`core.call-model` Function**
    We are given a function `(core.call-model model-name prompt-string)` which takes the LiteLLM model name and a prompt string, and returns the LLM's response as a string. The Director will be responsible for any JSON parsing of this string output or stringification of JSON input if `core.call-model` strictly expects/returns raw strings.

*   **Example (Illustrative Game: Roberts Rules Coin Allotment)**
    The example from the previous response (with Chair, A, B, and JSON states/responses) is a good illustration of this flow. The key is that each player agent's response is a JSON string like:
    ```json
    // Example Player A response string (after parsing)
    {
      "utterance": "I move that I, Player A, get 50 coins, and Player B gets 50 coins.",
      "new_game_state": {
        "game_id": "roberts_rules_coin_v1",
        "description": "Allot 100 coins among players A and B, following Roberts Rules of Order.",
        "players": ["Chair", "A", "B"],
        "agenda_item": "Allot 100 coins among players A and B.",
        "coins_to_allot": 100,
        "allotted_coins": {"A": 0, "B": 0},
        "current_phase": "MotionProposed",
        "current_motion": { "text": "A gets 50 coins, B gets 50 coins", "moved_by": "A", "seconded_by": null, "status": "proposed" },
        "secondary_motion": null,
        "dialog_history": [
          {"speaker": "System", "line": "Game Start. Agenda: Allot 100 coins among players A and B."},
          {"speaker": "Chair", "line": "The meeting will come to order. The agenda item is to allot 100 coins among players A and B. The floor is now open for motions."},
          {"speaker": "A", "line": "I move that I, Player A, get 50 coins, and Player B gets 50 coins."}
        ],
        "next_player_to_act": "Chair", // A passes turn to Chair
        "votes": {}
      }
    }
    ```

---

**Sufficiency for Clojure Implementation**

This revised description is **largely sufficient** to begin producing the Clojure code for the director.

**What's well-defined for implementation:**
1.  **Overall flow:** The two main phases (Planning, Play) are clear.
2.  **Director's core loop:** The sequence of operations within the play phase is specified.
3.  **Data structures:** The key data objects (`Planner Output`, `Game State`, `Player Response`) are defined as JSON, making parsing and generation straightforward in Clojure (e.g., using `clojure.data.json`).
4.  **LLM Interaction Points:** It's clear when `core.call-model` is used and what kind of string data (prompts, JSON strings) is passed and expected.
5.  **State Management:** The concept of `current_game_state` being updated by each player's `new_game_state` is clear.

**Areas requiring further detail or assumptions during implementation (but not blockers for starting):**
1.  **`core.call-model` specifics:** If it has limitations (e.g., max token length for input/output) or specific error handling behaviors, the Director needs to account for them. We assume it handles HTTP calls to LiteLLM and returns the string content.
2.  **LLM Roster Format & Selection Logic:** The exact schema for the roster JSON/EDN and the logic for "selecting a suitable model" (e.g., specific model names per role, capability tags) needs to be implemented. Initially, this could be hardcoded.
3.  **Error Handling:**
    *   LLM call failures (network, API errors from LiteLLM).
    *   Malformed JSON from LLMs (planner or players).
    *   Players not adhering to instructions (e.g., not updating `next_player_to_act`, producing invalid game states).
    Initial implementation might skip robust error handling and assume LLMs behave perfectly.
4.  **Prompt Engineering Subtleties:** The quality of the `Planner Prompt` (human-written) and the `player_instructions` (generated by the planner) is *critical*. The Director implementation itself doesn't define these, but their effectiveness will determine the system's success.
5.  **Initial Human `Planner Prompt`:** This needs to be crafted for the first game.
6.  **Debug Mode:** Details of what the director prints in debug mode.

These are mostly refinements or robustness additions that can be layered onto a core implementation based on the current description.

---

**Prompt for the Planner (Roberts Rules Coin Allotment Game)**

```text
You are an expert game designer LLM. Your task is to design a game based on the specifications provided and output the design as a single JSON object.

**Game Concept:**
A negotiation game for three players: "Chair", "PlayerA", and "PlayerB".
The game follows a simplified version of Robert's Rules of Order.
The objective is to decide on the allotment of 100 indivisible coins among PlayerA and PlayerB. The Chair does not receive coins but facilitates the meeting.

**Output Format:**
You MUST produce a single JSON object. This JSON object will have three top-level keys: "game_title", "initial_game_state", and "player_instructions".

1.  **`game_title` (String):**
    A descriptive title for the game. E.g., "Roberts Rules Coin Allotment".

2.  **`initial_game_state` (JSON Object):**
    This object represents the starting state of the game. It MUST include the following fields:
    *   `game_id` (String): A unique ID for this game type, e.g., "roberts_rules_coin_v1".
    *   `description` (String): A brief description of the game's premise.
    *   `players` (Array of Strings): List of player IDs: ["Chair", "PlayerA", "PlayerB"].
    *   `agenda_item` (String): The main topic, e.g., "Allot 100 coins between PlayerA and PlayerB."
    *   `coins_to_allot` (Integer): The total number of coins, e.g., 100.
    *   `allotted_coins` (JSON Object): Initial coin distribution, e.g., `{"PlayerA": 0, "PlayerB": 0}`.
    *   `current_phase` (String): The initial phase of the game, e.g., "MeetingOpened" or "AwaitingMotions".
    *   `current_motion` (Null or JSON Object): Initially null. If a motion is active, it should be an object like: `{"text": "Motion details", "moved_by": "PlayerID", "seconded_by": "PlayerID_or_null", "status": "proposed/seconded/voting/passed/failed"}`.
    *   `secondary_motion` (Null or JSON Object): For procedural motions like "move to vote", similar structure to `current_motion`.
    *   `dialog_history` (Array of JSON Objects): An array to store the conversation. Start it with an initial system message, e.g., `[{"speaker": "System", "line": "Game Start. Agenda: Allot 100 coins between PlayerA and PlayerB. The Chair has the floor."}]`.
    *   `next_player_to_act` (String): The ID of the player who should act first. This should usually be "Chair".
    *   `votes` (JSON Object): To store vote counts, e.g., initially `{}`. Can be structured like `{"motion_id_or_description": {"PlayerA": "Aye", "PlayerB": "Nay"}}`.

3.  **`player_instructions` (JSON Object):**
    A map where keys are player IDs ("Chair", "PlayerA", "PlayerB") and values are detailed string instructions for that player.
    Each player's instruction string MUST clearly explain:
    *   Their specific role and general objective in the game.
    *   That on their turn, they will receive the full current game state as a JSON string.
    *   That they MUST respond with a single JSON string containing two keys:
        1.  `"utterance"`: A string for their natural language speech for the turn (e.g., "I move that PlayerA gets 60 coins.").
        2.  `"new_game_state"`: The complete, updated JSON game state object after their action.
    *   How to update the `new_game_state`:
        *   They MUST append their own utterance to the `dialog_history` array as an object: `{"speaker": "TheirPlayerID", "line": "Their Utterance"}`.
        *   They MUST correctly update game-specific fields like `current_phase`, `current_motion` (if they make/second/vote on one), `secondary_motion`, `allotted_coins` (if a motion passes and allocates coins), and `votes`.
        *   CRITICALLY, they MUST determine and set the `next_player_to_act` field in `new_game_state` according to simplified Robert's Rules. For example:
            *   Chair usually speaks to open, guide, or call votes.
            *   After a player makes a motion, the turn usually goes to the Chair to ask for a second.
            *   If the Chair asks for a second, any other player (e.g., PlayerB if PlayerA moved) might take a turn to second.
            *   During voting, players vote sequentially as called by the Chair.
            *   If their action concludes the game (e.g., Chair adjourning after a successful motion on the agenda item), `next_player_to_act` should be set to `null`.
    *   **Specific Instructions for Roles:**
        *   **Chair:** Objective is to facilitate the meeting to resolve the agenda item according to simplified Robert's Rules. Manage turns, recognize speakers, call for motions, seconds, discussion, and votes. Announce results. Adjourn when the agenda is resolved. Does not make motions on the main agenda item or vote on it (unless to break a tie, but simplify to not vote). Can vote on procedural motions (like "move to vote").
        *   **PlayerA & PlayerB:** Objective is to maximize their own coin allotment. They can make motions, second motions, discuss, and vote. A motion could be, e.g., "PlayerA gets X coins, PlayerB gets Y coins" (where X+Y <= 100).

Remember, the player LLMs will rely entirely on YOUR generated instructions and the game state to act correctly and produce valid JSON. Be very clear and precise in the player instructions, especially regarding JSON manipulation and turn progression.

Produce ONLY the single JSON object as described. Do not include any other explanatory text before or after the JSON object.
```
