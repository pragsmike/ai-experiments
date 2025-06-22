(ns qat.llm-interface
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]))

(def LITELLM_ENDPOINT "http://localhost:8000/chat/completions") ; Default LiteLLM endpoint
(def LITELLM_API_KEY (System/getenv "LITELLM_API_KEY"))

(defn pre-flight-checks []
  (when-not (seq LITELLM_API_KEY)
    (println "---")
    (println "ERROR: LITELLM_API_KEY environment variable not set.")
    (println "Please set it before running the application.")
    (println "Example: export LITELLM_API_KEY='your-proxy-key'")
    (println "---")
    (System/exit 1)) ; Exit if the key is missing
  true)

(defn- parse-llm-response [response-body model-name]
  (println (str "DEBUG: Response from " model-name "\n" response-body))
  (try
    (let [parsed-body (json/read-str response-body :key-fn keyword)
          content (-> parsed-body :choices first :message :content)]
      (if content
        content
        (do
          (println (str "ERROR: Could not extract content from LLM response for " model-name ". Body: " response-body))
          (json/write-str {:error (str "No content in LLM response: " (pr-str parsed-body))}))))
    (catch Exception e
      (println (str "ERROR: Failed to parse LLM JSON response for " model-name ". Error: " (.getMessage e) ". Body: " response-body))
      (json/write-str {:error (str "Malformed JSON from LLM: " (.getMessage e))}))))

(defn call-model
  "Makes an actual HTTP call to the LiteLLM endpoint."
  [model-name prompt-string]
  (println (str "\n;; --- ACTUALLY Calling LLM: " model-name " via " LITELLM_ENDPOINT " ---"))
  (println (str "\nPrompt: \n" prompt-string))
  (try
    (let [request-body {:model model-name
                        :messages [{:role "user" :content prompt-string}]}


          headers {"Authorization" (str "Bearer " LITELLM_API_KEY)}
          response (http/post LITELLM_ENDPOINT
                              {:body (json/write-str request-body)
                               :content-type :json
                               :accept :json
                               :headers headers
                               :throw-exceptions false
                               :socket-timeout 300000
                               :connection-timeout 300000})]
      (if (= 200 (:status response))
        (parse-llm-response (:body response) model-name)
        (do
          (println (str "ERROR: LLM call to " model-name " failed with status " (:status response) ". Body: " (:body response)))
          (json/write-str {:error (str "LLM API Error: " (:status response) " " (:body response))}))))
    (catch Exception e
      (println (str "ERROR: Exception during LLM call to " model-name ". Error: " (.getMessage e)))
      (json/write-str {:error (str "Network or client exception: " (.getMessage e))}))))
