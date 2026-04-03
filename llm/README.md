# LLM Compose Stack

This repository is a Docker Compose deployment for a local LLM stack:

- `ollama` serves local models on `localhost:11434`
- `litellm` exposes an OpenAI-compatible proxy on `localhost:8000`
- `postgres_db` stores LiteLLM state/logging
- `open-webui` provides a browser UI on `localhost:3000`

The model routing lives in `litellm_config.yaml`. The shell scripts in `test/` are smoke tests against the LiteLLM proxy.

## Normal Operations

Start or refresh the stack:

```bash
docker compose up -d
```

See configured image references from the compose file:

```bash
make configured-images
```

See the images actually running for this compose project:

```bash
make running-images
make ps
```

See the installed LiteLLM package version inside the running container:

```bash
make litellm-version
```

## Image Pinning

Each service image in `docker-compose.yml` is pinned as `tag@sha256:digest`.

That gives two properties:

- The tag still shows the intended update channel, for example `main-stable`.
- The digest makes the deployed artifact immutable.

This matters because restarting a container does not update its image. A restart only reuses the image that is already present locally. To move to a new container build, you must pull or otherwise select a new image reference and recreate the service.

## Update Policy

### LiteLLM

Treat LiteLLM as the highest-risk component in this stack because it sits on the request path for both local and remote model providers.

Update LiteLLM when one of these is true:

- A published security advisory affects the running version or image
- You need a specific upstream fix or feature
- A planned maintenance review approves a newer vetted release

Do not update LiteLLM just because the `main-stable` tag moved.

### Other Services

Review `ollama`, `open-webui`, and `postgres` on a regular cadence, typically monthly, and also when a security advisory, hardware support issue, or required feature change affects them.

## How To Vet And Roll Forward An Update

### 1. Identify a candidate image

For LiteLLM, inspect the current digest published for the tracked tag:

```bash
docker buildx imagetools inspect ghcr.io/berriai/litellm:main-stable
```

Do the equivalent for other services on their tracked tags.

### 2. Review the upstream change

Before deploying LiteLLM, review:

- Upstream release notes or changelog
- Upstream security advisories and incident reports
- Any CVEs or supply-chain notices relevant to the image you plan to run

### 3. Trial the candidate without changing the pinned default

Use an environment override to test a candidate digest:

```bash
LITELLM_IMAGE='ghcr.io/berriai/litellm:main-stable@sha256:<candidate-digest>' \
docker compose up -d --pull always --force-recreate litellm
```

Equivalent one-off overrides exist for the other services:

- `OLLAMA_IMAGE`
- `OPEN_WEBUI_IMAGE`
- `POSTGRES_IMAGE`

### 4. Verify the candidate

Check what is actually running:

```bash
docker inspect litellm_service --format '{{.Config.Image}} {{.Image}}'
make litellm-version
```

Run smoke tests:

```bash
bash test/test-mistral.sh
```

If you use remote providers through LiteLLM, also run the relevant scripts in `test/` for those providers.

### 5. Make the update permanent

After the candidate is vetted, replace the default digest in `docker-compose.yml` for that service and redeploy it:

```bash
docker compose pull litellm
docker compose up -d --force-recreate litellm
```

For services other than LiteLLM, use the same pattern with the service name you changed.

## How And When To Roll Back

Because the compose file stores immutable digests, rollback is straightforward:

1. Restore the previously vetted digest in `docker-compose.yml`
2. Recreate the affected service with `docker compose up -d --force-recreate <service>`
3. Re-run the same verification checks you used for the rollout

## Notes Specific To LiteLLM

- Check the installed package version inside the container, not just the image tag.
- If there is a supply-chain incident, inspect the actual running container before assuming the stack is affected.
- `pull_policy: always` only affects compose-driven pull or recreate operations. It does not make a plain container restart fetch new code.
