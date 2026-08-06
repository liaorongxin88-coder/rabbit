RABBIT := ./rabbit
ENV ?= dev
TEST_ENV ?= test
MODE ?= debug
ARGS ?=
DEVICE ?=

.PHONY: help bootstrap doctor deps analyze test check verify run devices apk aab release-apk size e2e

help:
	@$(RABBIT) help

bootstrap:
	@$(RABBIT) bootstrap

doctor:
	@$(RABBIT) doctor

deps:
	@$(RABBIT) deps

analyze:
	@$(RABBIT) analyze

test:
	@$(RABBIT) test $(TEST_ENV) $(ARGS)

check:
	@$(RABBIT) check

verify:
	@$(RABBIT) verify

run:
	@$(RABBIT) run $(ENV) --$(MODE) $(if $(DEVICE),-d $(DEVICE),) $(ARGS)

devices:
	@$(RABBIT) devices

apk:
	@$(RABBIT) apk $(ENV) --$(MODE) $(ARGS)

aab:
	@$(RABBIT) release aab $(ARGS)

release-apk:
	@$(RABBIT) release apk $(ARGS)

size:
	@$(RABBIT) release size $(ARGS)

e2e:
	@$(RABBIT) e2e
