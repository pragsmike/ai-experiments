curl -X POST http://localhost:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
        "model": "ollama-expander",
        "messages": [
          {
            "role": "user",
            "content": "Why is the sky blue?"
          }
        ]
      }'
