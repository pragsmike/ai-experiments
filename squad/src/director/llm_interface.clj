(ns director.llm-interface
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]))

(def LITELLM_ENDPOINT "http://localhost:4000/chat/completions") ; Default LiteLLM endpoint

(defn- parse-llm-response [response-body model-name]
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

(defn real-call-model
  "Makes an actual HTTP call to the LiteLLM endpoint."
  [model-name prompt-string _ignored-planner-model-name-cfg]
  (println (str "\n;; --- ACTUALLY Calling LLM: " model-name " via " LITELLM_ENDPOINT " ---"))
  (try
    (let [request-body {:model model-name
                        :messages [{:role "user" :content prompt-string}]}
          response (http/post LITELLM_ENDPOINT
                              {:body (json/write-str request-body)
                               :content-type :json
                               :accept :json
                               :throw-exceptions false ; Handle errors manually
                               :socket-timeout 300000 ; 5 minutes
                               :connection-timeout 300000
                               })]
      (if (= 200 (:status response))
        (parse-llm-response (:body response) model-name)
        (do
          (println (str "ERROR: LLM call to " model-name " failed with status " (:status response) ". Body: " (:body response)))
          (json/write-str {:error (str "LLM API Error: " (:status response) " " (:body response))}))))
    (catch Exception e
      (println (str "ERROR: Exception during LLM call to " model-name ". Error: " (.getMessage e)))
      (json/write-str {:error (str "Network or client exception: " (.getMessage e))}))))
