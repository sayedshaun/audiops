# Audiops developer commands. Run `make help` for the list.
# Override the JDK with `make JAVA_HOME=/path/to/jdk-17 …` when the default java has no javac.

GRADLE := ./gradlew
GIT    := $(if $(wildcard .audiops-git),GIT_DIR=$(CURDIR)/.audiops-git GIT_WORK_TREE=$(CURDIR) git,git)

.DEFAULT_GOAL := help
.PHONY: help format format-check test test-shared test-desktop lint build release check ci hooks run-desktop install clean

help: ## Show this help
	@awk 'BEGIN {FS = ":.*## "} /^[a-zA-Z_-]+:.*## / {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

format: ## Format all Kotlin and Gradle files (ktlint via Spotless)
	$(GRADLE) spotlessApply

format-check: ## Fail if any file is not formatted or breaks a style rule
	$(GRADLE) spotlessCheck

test-shared: ## DSP, model, repository and ViewModel tests
	$(GRADLE) :shared:desktopTest

test-desktop: ## Desktop engine and render tests
	$(GRADLE) :desktopApp:test

test: ## All JVM unit tests
	$(GRADLE) :shared:desktopTest :desktopApp:test

lint: ## Android lint (0 errors required)
	$(GRADLE) :app:lintDebug

build: ## Debug APK
	$(GRADLE) :app:assembleDebug

release: ## Release APK with R8 and resource shrinking
	$(GRADLE) :app:assembleRelease

check: ## What the pre-commit hook runs: format check and unit tests
	$(GRADLE) spotlessCheck :shared:desktopTest :desktopApp:test

ci: ## Everything CI runs before a merge to main
	$(GRADLE) spotlessCheck :shared:desktopTest :desktopApp:test :app:lintDebug :app:assembleRelease

hooks: ## Install the git pre-commit hook (.githooks/pre-commit)
	$(GIT) config core.hooksPath .githooks
	@chmod +x .githooks/pre-commit
	@echo "pre-commit hook installed"

run-desktop: ## Run the desktop app
	$(GRADLE) :desktopApp:run

install: ## Install the debug build on the connected device or emulator
	$(GRADLE) :app:installDebug

clean: ## Delete build outputs
	$(GRADLE) clean
