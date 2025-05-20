curl -X POST http://localhost:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  # -H "Authorization: Bearer your-litellm-master-key" # Only if master_key is set in litellm_config.yaml
  -d '{
        "model": "openai-summarizer",
        "messages": [
          {
            "role": "user",
            "content": "Summarize quantum physics in one sentence."
          }
        ]
      }'
