(ns squad.core
  (:require [clj-http.client :as client]
            [cheshire.core :as json])
  (:gen-class))

(def litellm-base-url "http://localhost:8000")
(def litellm-chat-completions-endpoint "/v1/chat/completions") ; LiteLLM mimics OpenAI's /v1/ endpoint

(defn call-litellm-model
  "Calls a specific model via LiteLLM with a given prompt."
  [model-name user-prompt]
  (println (str "\n--- Calling LiteLLM for model: " model-name " ---"))
  (println (str "Prompt: \"" user-prompt "\""))
  (try
    (let [request-body {:model model-name
                        :messages [{:role "user" :content user-prompt}]
                        :temperature 0.7 ; Adjust as needed
                        ;:max_tokens 150  ; Optional: uncomment and adjust
                        }
          response (client/post (str litellm-base-url litellm-chat-completions-endpoint)
                                {:form-params request-body
                                 :content-type :json
                                 :accept :json
                                 :as :json ; Automatically parse response body as JSON
                                 :throw-exceptions false ; Handle errors manually
                                 })]
      (if (= 200 (:status response))
        (let [content (get-in response [:body :choices 0 :message :content])]
          (println (str "Raw response body from " model-name ": " (:body response)))
          (println (str "Extracted content from " model-name ": \"" content "\""))
          content)
        (do
          (println (str "Error calling model " model-name ". Status: " (:status response)))
          (println (str "Response body: " (if (:body response)
                                            (try (json/generate-string (:body response) {:pretty true})
                                                 (catch Exception _ (:body response)))
                                            "No response body.")))
          nil)))
    (catch Exception e
      (println (str "Exception while calling model " model-name ": " (.getMessage e)))
      nil)))

(defn -main
  "Main function to run the chained LLM calls."
  [& args]
  (let [initial-prompt "Briefly explain the core concept of functional programming."
        model1-name "openai-summarizer"  ; Must match your LiteLLM config.yaml
        model2-name "ollama-expander"    ; Must match your LiteLLM config.yaml
        ]

    (println "Starting LLM chain process...")

    (if-let [response-from-model1 (call-litellm-model model1-name initial-prompt)]
      (if (empty? response-from-model1)
        (println (str "\nModel 1 (" model1-name ") returned an empty response. Cannot proceed."))
        (do
          (println (str "\n--- Preparing to call Model 2 (" model2-name ") ---"))
          (let [prompt-for-model2 (str "Expand on the following concept, providing examples: " response-from-model1)]
            (if-let [response-from-model2 (call-litellm-model model2-name prompt-for-model2)]
              (if (empty? response-from-model2)
                (println (str "\nModel 2 (" model2-name ") returned an empty response."))
                (println (str "\n\n=== Final Result from " model2-name " ===\n" response-from-model2)))
              (println (str "\nFailed to get response from Model 2 (" model2-name ")."))))))
      (println (str "\nFailed to get response from Model 1 (" model1-name "). Chain aborted.")))

    (println "\n--- LLM chain process complete. ---")))

(comment
  (call-litellm-model "ollama-expander" "tell me about yourself")

;;
  )
;; To run this from the CLI (assuming you are in the project root directory):
;; 1. Ensure LiteLLM is running and configured.
;; 2. Ensure Ollama is running and serving the model specified for `ollama-expander`.
;; 3. Ensure your OPENAI_API_KEY is available to LiteLLM.
;; 4. Run: clj -M -m squad.core
