# HelpDesk AI

A customer support ticketing system. Customers raise tickets on the web, and agents work them on a
dashboard with an SLA timer.

This repo is a QA/SDET portfolio project:

- the **app** (`backend/`) is the system under test
- **`tests/`** holds the test framework: 236 API and UI tests with TestNG, Rest Assured and Selenium
- **GitHub Actions** runs the full regression suite on every push to `dev`
- failed runs are **triaged automatically**: an n8n workflow reads the failures and files them in
  Jira, as new bugs, comments on existing bugs, or one task for environment problems

See [Regression failure triage](#regression-failure-triage-ci--n8n--jira) for how that works.

## Tech stack

| Layer | Tool |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA |
| Database | PostgreSQL 16 + Flyway migrations |
| Frontend | Plain HTML/CSS/JavaScript served by the backend (no build step) |
| API docs | Swagger UI (springdoc-openapi) |
| Containers | Docker + Docker Compose |
| Tests | TestNG, Rest Assured, Selenium 4, Jackson (in `tests/`) |
| CI | GitHub Actions |
| Failure triage | n8n, Jira Cloud |

## Project structure

```
helpdesk-ai/
├── .github/workflows/
│   └── regression.yml            CI: runs the suite, uploads results, notifies n8n on failure
├── backend/                      Spring Boot app (API + frontend)
│   └── src/main/
│       ├── java/com/helpdesk/
│       │   ├── auth/             login, register, JWT tokens
│       │   ├── user/             users, roles, demo data seeder
│       │   ├── ticket/           tickets, comments, lifecycle rules, filters
│       │   ├── sla/              SLA deadlines + breach checker (scheduled job)
│       │   ├── config/           security, Swagger, app properties
│       │   └── common/           error handling
│       └── resources/
│           ├── application.yml   all settings (DB, JWT, SLA times…)
│           ├── db/migration/     Flyway SQL (database schema)
│           └── static/           frontend pages, CSS, JS
├── tests/                        test framework (API + UI)
│   ├── pom.xml                   TestNG, Rest Assured, Selenium, Jackson
│   ├── testng.xml                suite file (API and UI blocks, failure listener)
│   └── src/test/
│       ├── java/com/helpdesk/tests/
│       │   ├── api/              base specs, endpoints, POJOs, utils (config, DB), API tests
│       │   ├── ui/               driver factory, base test, Page Objects, UI tests
│       │   └── listeners/        FailureReportListener: writes test-failures.json
│       └── resources/
│           ├── config.properties URLs, demo accounts, browser settings
│           ├── schemas/          JSON schemas for response validation
│           └── testdata/         test data files
├── n8n/
│   └── regression-triage.json    the n8n triage workflow (import into n8n)
├── docs/                         API and UI test case sheets (Excel)
├── perf/                         JMeter test plans (planned)
├── docker-compose.yml
└── .env.example
```

## Running it

**You need:** Java 17+, Maven, Docker Desktop.

```bash
# 1. Start the database
docker compose up -d

# 2. Start the app
cd backend
mvn spring-boot:run
```

Open **http://localhost:8080** and log in with a demo account:

| Role | Email | Password |
|---|---|---|
| Admin | admin@helpdesk.local | Admin@123 |
| Agent | agent1@helpdesk.local | Agent@123 |
| Agent | agent2@helpdesk.local | Agent@123 |
| Customer | customer@helpdesk.local | Customer@123 |

Swagger UI: **http://localhost:8080/swagger-ui.html**. Call `POST /api/auth/login`, copy the `token`,
click **Authorize**, and paste it.

**Other ways to run:**

- **Everything in Docker:** `docker compose --profile app up -d --build`
- **Fast SLA breaches for testing:** `SLA_MEDIUM=PT2M SLA_CHECK_INTERVAL=PT10S mvn spring-boot:run`
- **Reset the database:** `docker compose down -v && docker compose up -d`

## Running the tests

With the database and the app running:

```bash
cd tests
mvn test -Dheadless=true
```

- This runs `testng.xml`: the API block, then the UI block (Chrome by default; set `browser` in
  `config.properties` to `firefox` or `edge`).
- The SLA tests need the app started with short SLA times, so they're excluded from the suite. Run
  them separately: `mvn test -Dtest=SlaApiTest`
- Results: `tests/target/surefire-reports/` (TestNG HTML report and XML), plus
  `tests/target/test-failures.json` from the failure listener.

## Regression failure triage (CI → n8n → Jira)

When the regression suite fails, nobody has to read the logs to work out what broke. Each failure is
sorted and filed in Jira automatically.

```
push to dev
   │
   ▼
GitHub Actions (regression.yml)
   starts PostgreSQL + the app, runs all tests
   FailureReportListener writes test-failures.json
   uploads it as a run artifact
   tests failed? → POST run details to the n8n webhook
   │
   ▼
n8n (n8n/regression-triage.json)
   downloads test-failures.json from the GitHub API
   splits it into one item per failure
   │
   ├── environment error? (connection refused, browser didn't start, 502/503/504…)
   │      └──► one Jira Task per run, listing every affected test
   │
   └── everything else
          ├── open bug with this fingerprint exists? ──► comment "failed again in run …"
          └── no open bug ─────────────────────────────► new Jira Bug
```

### The pieces

| Piece | What it does |
|---|---|
| `tests/.../listeners/FailureReportListener.java` | TestNG listener. On each failed test it records the class, method, parameters, exception type, message and the stack frames from project code, then writes them to `target/test-failures.json` |
| `.github/workflows/regression.yml` | Runs the suite on every push to `dev`. Uploads the surefire reports, `test-failures.json`, the backend log and UI screenshots as artifacts. On failure, sends the run ID, commit and branch to n8n |
| `n8n/regression-triage.json` | Downloads the failures, separates environment errors from real failures, checks Jira for an existing bug, then comments or creates |

### What a Jira bug contains

- **Summary:** `[Regression] UserApiTest.fullNameCannotBeOnlySpaces – AssertionError`
- **Labels:** the fingerprint, plus `auto-triage`
- **Description:** test name, data-driven parameters, error message, the failing line in the test
  code, and links to the GitHub run and commit

### Design decisions

- **Fingerprint = test class + method name**, for example `UserApiTest.fullNameCannotBeOnlySpaces`.
  It leaves out data-driven parameters, which can contain object IDs that change on every run, so the
  same failure always gets the same fingerprint.
- **Jira is the only memory.** Duplicates are found by searching Jira for an open bug with the
  fingerprint label. n8n keeps no list of its own, so when a bug is closed and the test fails again,
  that's correctly reported as a new bug (a regression) and not ignored.
- **One item per fingerprint per run.** A data-driven test can fail several times in one run; it
  still produces one bug.
- **Environment problems are not bugs.** When the backend is down, every test fails for the same
  reason. Those failures are grouped into a single Jira Task for whoever owns the pipeline, so
  developers only see real defects.
- **Rules first.** Environment errors are recognised by exception names and messages: free, instant
  and predictable.
- **Least privilege.** The GitHub token can only read Actions data in this one repository. The n8n
  webhook URL is stored as a GitHub secret, not in the workflow file. No credentials are in this repo.

### Running the triage yourself

1. Import `n8n/regression-triage.json` into n8n (**⋯ → Import from file**).
2. In n8n, create the credentials the nodes refer to:
   - **github token**: Header Auth, `Authorization: Bearer <fine-grained token with Actions: read>`
   - **Jira SW Cloud account**: your Atlassian email, an API token and your Jira site URL
3. Change the Jira site URL and project key in the Jira nodes to your own.
4. Make n8n reachable from GitHub (for example `ngrok http 5678`) and **publish** the workflow.
5. In the GitHub repo, add the secret `N8N_WEBHOOK_URL` = `https://<your-public-n8n>/webhook/regression-failed`.

## Frontend pages

| Page | Who | What |
|---|---|---|
| `/login.html`, `/register.html` | everyone | Log in; customers can self-register |
| `/my-tickets.html` | customer | Their own tickets |
| `/new-ticket.html` | everyone | Raise a ticket (staff can raise one on behalf of a customer) |
| `/ticket.html?id=N` | everyone | Ticket detail and conversation. Staff also get the update form |
| `/dashboard.html` | agent, admin | Stats, filters, ticket table with SLA countdown |
| `/users.html` | admin | Add users, activate or deactivate them |

Every interactive element has a `data-testid` attribute (e.g. `login-email`, `ticket-submit`,
`update-status`, `reply-body`) for stable Selenium locators.

## API overview

All endpoints are under `/api`. Everything except login and register needs
`Authorization: Bearer <token>`.

| Method | Endpoint | Who | Notes |
|---|---|---|---|
| POST | `/auth/register` | public | Creates a CUSTOMER, returns a token |
| POST | `/auth/login` | public | Returns `{token, expiresAt, user}` |
| GET | `/auth/me` | any | Current user |
| POST | `/tickets` | any | `{subject, description, category?}`. Customer: own ticket. Staff: must also send `customerEmail` |
| GET | `/tickets` | any | Customers see only their own. Filters: `status, category, priority, assigneeId, unassigned, breached, q`, plus `page, size, sort` |
| GET | `/tickets/stats` | agent, admin | Counts for the dashboard |
| GET | `/tickets/{id}` | any | Customers get 404 for other people's tickets |
| PATCH | `/tickets/{id}` | agent, admin | `status, category, priority, assigneeId, unassign` |
| DELETE | `/tickets/{id}` | admin | |
| GET/POST | `/tickets/{id}/comments` | any | `internal: true` notes are staff-only and hidden from customers |
| GET/POST | `/users` | agent+ / admin | List users; admin creates users |
| PATCH | `/users/{id}` | admin | `fullName, role, active` |

Errors always use the same shape:
`{timestamp, status, error, message, path, fieldErrors}`.

## Business rules

**Ticket lifecycle**

```
OPEN ──────────► IN_PROGRESS ◄──────► WAITING_ON_CUSTOMER
  │                  │                        │
  │                  ▼                        │
  │               RESOLVED ◄──────────────────┘
  │                  │  ▲ (reopen → IN_PROGRESS)
  ▼                  ▼
CLOSED ◄─────────────┘            (CLOSED is final)
```

- An invalid move returns **409 Conflict**.
- Moving an unassigned ticket to IN_PROGRESS assigns it to the agent who moved it.
- A customer reply on a WAITING_ON_CUSTOMER or RESOLVED ticket moves it back to IN_PROGRESS.
- No one can comment on a CLOSED ticket (409).

**SLA** (time to resolve, counted from creation)

| Priority | Default |
|---|---|
| URGENT | 4 hours |
| HIGH | 8 hours |
| MEDIUM | 24 hours (every new ticket starts here; agents change it) |
| LOW | 72 hours |

- Changing priority recalculates the deadline from the creation time.
- A background job checks every minute and flags OPEN or IN_PROGRESS tickets past their deadline as
  breached, and adds an internal system note.
- WAITING_ON_CUSTOMER tickets aren't flagged while waiting. Once a ticket is breached, it stays breached.

**Categories:** BILLING, BUG, FEATURE_REQUEST, ACCOUNT, OTHER. The customer can pick one when raising a
ticket (optional), and agents can change it.
