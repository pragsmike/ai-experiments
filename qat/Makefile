run:
	clj -M:run corpus


pack:
	(for i in copilot/onboard-1.md copilot/approaches* copilot/gemini-plan-1.md \
			copilot/prompt* \
     README.md docs/DESIGN.md deps.edn Makefile ;\
	   do echo $$i; cat $$i; echo ---- ; done ;\
	echo Source files; echo -----; \
	cat src/qat/*.clj ) >~/qat-pack.txt
