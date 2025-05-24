#!/bin/bash
set -e # Exit immediately if a command exits with a non-zero status.

# Load .env file if it exists to get variables
if [ -f .env ]; then
  export $(grep -v '^#' .env | xargs)
fi

# Variables for connecting to PostgreSQL (as superuser)
# Default values are used if not set in .env
PG_HOST="localhost" # The script runs on the host, connecting to the exposed port
PG_PORT="5432"
PG_SUPERUSER="${POSTGRES_SUPERUSER_NAME:-postgres}"
PG_SUPERUSER_PASSWORD="${POSTGRES_SUPERUSER_PASSWORD}"
PG_INITIAL_DB="${POSTGRES_INITIAL_DB:-app_db}" # Connect to this DB to perform admin tasks

# Variables for the LiteLLM database and user
LITELLM_USER="${LITELLM_DB_USER:-litellm_user}"
LITELLM_PASSWORD="${LITELLM_DB_PASSWORD:-your_litellm_db_password}" # Ensure this matches DATABASE_URL in compose
LITELLM_DATABASE="${LITELLM_DB_NAME:-litellm_log_db}" # Ensure this matches DATABASE_URL in compose

if [ -z "$PG_SUPERUSER_PASSWORD" ]; then
  echo "Error: POSTGRES_SUPERUSER_PASSWORD is not set. Please set it in your .env file or as an environment variable."
  exit 1
fi
if [ "$LITELLM_PASSWORD" == "your_litellm_db_password" ] || [ "$LITELLM_PASSWORD" == "another_strong_password_for_litellm_user" ]; then
  echo "Warning: LITELLM_DB_PASSWORD is set to a default value. Please change it for security."
  # For a real setup, you might want to exit 1 here if it's a default password.
fi


# Export PGPASSWORD so psql doesn't prompt
export PGPASSWORD="$PG_SUPERUSER_PASSWORD"

echo "Waiting for PostgreSQL to be ready on $PG_HOST:$PG_PORT..."

# Wait for PostgreSQL to be ready (simple loop, pg_isready is better if available locally)
# The healthcheck in docker-compose helps, but this script runs from the host.
max_retries=12
retry_count=0
until psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_SUPERUSER" -d "$PG_INITIAL_DB" -c '\q' > /dev/null 2>&1; do
  retry_count=$((retry_count + 1))
  if [ $retry_count -ge $max_retries ]; then
    echo "PostgreSQL did not become ready after $max_retries retries. Exiting."
    exit 1
  fi
  echo "PostgreSQL is unavailable - sleeping for 5s (retry $retry_count/$max_retries)..."
  sleep 5
done
echo "PostgreSQL is up and running."

# Use psql to execute commands.
# The -v ON_ERROR_STOP=1 ensures that the script will exit if any SQL command fails.

echo "Checking if LiteLLM user '${LITELLM_USER}' exists..."
USER_EXISTS=$(psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_SUPERUSER" -d "$PG_INITIAL_DB" -tAc "SELECT 1 FROM pg_roles WHERE rolname='${LITELLM_USER}'")

if [ "$USER_EXISTS" = "1" ]; then
  echo "User '${LITELLM_USER}' already exists."
else
  echo "Creating LiteLLM database user: ${LITELLM_USER}"
  psql -v ON_ERROR_STOP=1 --host "$PG_HOST" --port "$PG_PORT" --username "$PG_SUPERUSER" --dbname "$PG_INITIAL_DB" <<-EOSQL
    CREATE USER ${LITELLM_USER} WITH PASSWORD '${LITELLM_PASSWORD}';
EOSQL
  echo "User ${LITELLM_USER} created."
fi

echo "Checking if LiteLLM database '${LITELLM_DATABASE}' exists..."
DB_EXISTS=$(psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_SUPERUSER" -d "$PG_INITIAL_DB" -tAc "SELECT 1 FROM pg_database WHERE datname='${LITELLM_DATABASE}'")

if [ "$DB_EXISTS" = "1" ]; then
  echo "Database '${LITELLM_DATABASE}' already exists."
else
  echo "Creating LiteLLM database: ${LITELLM_DATABASE}"
  psql -v ON_ERROR_STOP=1 --host "$PG_HOST" --port "$PG_PORT" --username "$PG_SUPERUSER" --dbname "$PG_INITIAL_DB" <<-EOSQL
    CREATE DATABASE ${LITELLM_DATABASE} OWNER ${LITELLM_USER};
EOSQL
  echo "Database ${LITELLM_DATABASE} created and ownership assigned to ${LITELLM_USER}."
fi

# Optional: Grant all privileges if needed (OWNER usually has enough)
# echo "Granting privileges (if necessary)..."
# psql -v ON_ERROR_STOP=1 --host "$PG_HOST" --port "$PG_PORT" --username "$PG_SUPERUSER" --dbname "${LITELLM_DATABASE}" <<-EOSQL
#   GRANT ALL PRIVILEGES ON DATABASE ${LITELLM_DATABASE} TO ${LITELLM_USER};
#   -- If LiteLLM needs to create tables in specific schemas, grant usage on schema public or other schemas.
#   GRANT USAGE ON SCHEMA public TO ${LITELLM_USER};
#   GRANT CREATE ON SCHEMA public TO ${LITELLM_USER};
#   -- More granular permissions might be needed depending on what LiteLLM does with the DB beyond basic logging
#   -- For basic logging, CREATE TABLE, INSERT, SELECT, UPDATE, DELETE on its own tables should be sufficient,
#   -- which it gets by being the owner of the database or by default grants in the public schema.
# EOSQL

# Unset PGPASSWORD
unset PGPASSWORD

echo "PostgreSQL setup for LiteLLM complete."
echo "LiteLLM should be able to connect using:"
echo "User: ${LITELLM_USER}"
echo "Database: ${LITELLM_DATABASE}"
echo "Host (from LiteLLM container): postgres_db"
