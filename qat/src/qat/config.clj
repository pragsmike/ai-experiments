(ns qat.config
  "Centralized configuration for the QAT system.")

;; --- Model Definitions ---
;; Defines the specific LLM models to be used for each agent role.
;; These names must correspond to models available via the configured LLM proxy.

(def GUEST_MODEL "openai/gpt-3.5-turbo")    ; For asking questions
(def EXPERT_MODEL "openai/gpt-4.1-nano")      ; For generating initial answers
(def FINALIZER_MODEL "openai/gpt-4.1-nano") ; For correcting answers based on critique
(def CRITIC_MODEL "openai/gpt-4.1-nano")      ; For quality scoring
