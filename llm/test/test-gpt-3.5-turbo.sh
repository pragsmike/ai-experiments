curl -v -X POST http://localhost:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{
        "model": "openai-summarizer",
        "messages": [
          {
            "role": "user",
            "content": "Summarize quantum physics in one sentence."
          }
        ]
      }'
