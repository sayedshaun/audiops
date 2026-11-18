# Audiops developer commands. Run `make help` for the list.
# Override the JDK with `make JAVA_HOME=/path/to/jdk-17 …` when the default java has no javac.

GRADLE := ./gradlew
GIT    := $(if $(wildcard .audiops-git),GIT_DIR=$(CURDIR)/.audiops-git GIT_WORK_TREE=$(CURDIR) git,git)

.DEFAULT_GOAL := help
.PHONY: help format format-check test lint build release check ci hooks install clean

help: ## Show this help
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

format: ## Format all Kotlin and Gradle files (ktlint via Spotless)
	$(GRADLE) spotlessApply

format-check: ## Fail if any file is not formatted or breaks a style rule
	$(GRADLE) spotlessCheck

test: ## All JVM unit tests (DSP, model, repository, ViewModel)
	$(GRADLE) :shared:testAndroidHostTest

lint: ## Android lint (0 errors required)
	$(GRADLE) :app:lintDebug

build: ## Debug APK
	$(GRADLE) :app:assembleDebug

release: ## Release APK with R8 and resource shrinking
	$(GRADLE) :app:assembleRelease

check: ## What the pre-commit hook runs: format check and unit tests
	$(GRADLE) spotlessCheck :shared:testAndroidHostTest

ci: ## Everything CI runs before a merge to main
	$(GRADLE) spotlessCheck :shared:testAndroidHostTest :app:lintDebug :app:assembleRelease

hooks: ## Install the git pre-commit hook (.githooks/pre-commit)
	$(GIT) config core.hooksPath .githooks
	@chmod +x .githooks/pre-commit
	@echo "pre-commit hook installed"

install: ## Install the debug build on the connected device or emulator
	$(GRADLE) :app:installDebug

clean: ## Delete build outputs
	$(GRADLE) clean
