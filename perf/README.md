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

## Journey tests (PERF-JNY-001..007)

Whole user journeys from `lib/journeys.js`, one `group()` per step. The agent journey needs OPEN tickets, so seed once:

```
k6 run -e COUNT=2000 scripts/seed-tickets.js
```

| Test | Command | Time |
|---|---|---|
| JNY-001 customer journey | `k6 run -e PROFILE=JNY-001 scenarios/customer-journey.js` | 4.5 min |
| JNY-002 agent journey | `k6 run -e PROFILE=JNY-002 scenarios/agent-journey.js` | 4.5 min |
| JNY-003 mixed 70/20/10 | `k6 run -e PROFILE=JNY-003 scenarios/mixed-workload.js` | 4.5 min |
| JNY-004 mixed stress | `k6 run -e PROFILE=JNY-004 scenarios/mixed-workload.js` | 4 min |
| JNY-005 mixed soak | `k6 run -e PROFILE=JNY-005 scenarios/mixed-workload.js` | 33 min |
| JNY-006 breakpoint | `k6 run -e PROFILE=JNY-006 scenarios/mixed-workload.js` | up to 10 min |
| JNY-007 recovery smoke | `k6 run -e PROFILE=JNY-007 scenarios/mixed-workload.js` | 30 s |

- JNY-004, 005 and 006 print a short report after the k6 summary (p95 per minute, drift, job-tick spike, req/s at the break).
- JNY-005: restart the app with short SLAs first, so the SLA breach job has work every minute, and set them back afterwards:
  `SLA_LOW=PT3M SLA_MEDIUM=PT2M SLA_HIGH=PT1M SLA_URGENT=PT1M docker compose --profile app up -d app`
- JNY-006 then JNY-007: run them back to back and do not restart the app in between. Restart it after JNY-007.
