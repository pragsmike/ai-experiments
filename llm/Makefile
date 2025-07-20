restart:
	docker-compose up -d
restart-ollama:
	docker-compose up -d --no-deps --force-recreate ollama
restart-litellm:
	docker-compose up -d --no-deps --force-recreate litellm

# Create a 'pack' file containing all project files, suitable for
# uploading to an AI assistant for context.
pack:
	(for i in Makefile *.yml *.yaml test/*; \
	   do echo $$i; cat $$i; echo ----; done ; \
	) > ~/litellm-pack.txt
