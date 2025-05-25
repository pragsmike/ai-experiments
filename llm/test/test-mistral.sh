# Load .env file if it exists to get variables
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

curl -X POST http://localhost:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $LITELLM_USER_KEY" \
  -d '{
        "model": "ollama/mistral",
        "messages": [
          {
            "role": "user",
            "content": "Why is the sky blue?"
          }
        ]
      }'
