# HelpDesk AI

A customer support ticketing system. Customers raise tickets on the web, and agents work them on a
dashboard with an SLA timer.

This repo is a QA/SDET portfolio project: the app is the system under test, and `tests/` holds the
test framework (API and UI).

**Planned for later** (after the test suite): AI triage (classification, priority, suggested replies)
and n8n automation (email → ticket, SLA breach alerts).

## Tech stack

| Layer | Tool |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA |
| Database | PostgreSQL 16 + Flyway migrations |
| Frontend | Plain HTML/CSS/JavaScript served by the backend (no build step) |
| API docs | Swagger UI (springdoc-openapi) |
| Containers | Docker + Docker Compose |
| Tests | TestNG, Rest Assured, Selenium 4 (in `tests/`) |

## Project structure

```
helpdesk-ai/
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
├── tests/                        test project (you write the tests here)
│   ├── pom.xml                   TestNG, Rest Assured, Selenium, Jackson
│   ├── testng.xml                suite file
│   └── src/test/
│       ├── java/com/helpdesk/tests/
│       │   ├── base/             base classes (setup, specs, driver)
│       │   ├── api/              API tests
│       │   ├── ui/pages/         Page Objects
│       │   ├── ui/               UI tests
│       │   └── utils/            helpers
│       └── resources/
│           ├── schemas/          JSON schemas for response validation
│           └── testdata/         test data files
├── perf/                         JMeter test plans
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
