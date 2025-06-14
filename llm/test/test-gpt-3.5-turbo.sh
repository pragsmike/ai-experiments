# Load .env file if it exists to get variables
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

curl -v -X POST http://localhost:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $LITELLM_USER_KEY" \
  -d '{
        "model": "openai/gpt-3.5-turbo",
        "messages": [
          {
            "role": "user",
            "content": "Summarize quantum physics in one sentence."
          }
        ]
      }'
