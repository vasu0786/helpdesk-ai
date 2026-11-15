# Performance tests (k6)

Load tests for the HelpDesk API, written in JavaScript and run with [Grafana k6](https://grafana.com/docs/k6/latest/).

## Install

```
brew install k6
```

## Layout

```
perf/
├── config/       load profiles (stages) and thresholds per test, selected with -e PROFILE=<name>
├── lib/          the only files that know URLs and payloads (env, auth, tickets, checks)
├── data/         accounts and fixtures loaded with SharedArray
├── scenarios/    entry points you run with `k6 run`
├── scripts/      one-off helpers such as seeding data
└── results/      JSON summaries written by handleSummary (git-ignored)
```

## Run

From inside `perf/`, with the app and Postgres already running:

```
k6 run -e PROFILE=smoke scenarios/login.js
```
