restart:
	docker compose up -d
restart-ollama:
	docker compose up -d --no-deps --force-recreate ollama
restart-litellm:
	docker compose up -d --no-deps --force-recreate litellm
ps:
	docker compose ps
configured-images:
	docker compose config --images
running-images:
	docker ps --filter label=com.docker.compose.project=llm --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
litellm-version:
	docker exec litellm_service python -c 'import importlib.metadata as m; print(m.version("litellm"))'

# Create a 'pack' file containing all project files, suitable for
# uploading to an AI assistant for context.
pack:
	(for i in Makefile *.yml *.yaml test/*; \
	   do echo $$i; cat $$i; echo ----; done ; \
	) > ~/litellm-pack.txt
