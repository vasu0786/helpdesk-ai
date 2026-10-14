-- Users: customers, agents and admins all live here, told apart by role.
CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(120) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tickets (
    id                  BIGSERIAL PRIMARY KEY,
    subject             VARCHAR(200) NOT NULL,
    description         TEXT         NOT NULL,
    customer_email      VARCHAR(255) NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    category            VARCHAR(30),
    priority            VARCHAR(20)  NOT NULL,
    assignee_id         BIGINT REFERENCES users (id),

    -- SLA
    sla_due_at          TIMESTAMPTZ  NOT NULL,
    sla_breached        BOOLEAN      NOT NULL DEFAULT FALSE,
    sla_breached_at     TIMESTAMPTZ,

    resolved_at         TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_tickets_status         ON tickets (status);
CREATE INDEX idx_tickets_customer_email ON tickets (customer_email);
CREATE INDEX idx_tickets_assignee       ON tickets (assignee_id);
CREATE INDEX idx_tickets_sla_open       ON tickets (sla_due_at) WHERE sla_breached = FALSE;

CREATE TABLE comments (
    id            BIGSERIAL PRIMARY KEY,
    ticket_id     BIGINT       NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    author_id     BIGINT REFERENCES users (id),
    author_email  VARCHAR(255) NOT NULL,
    author_role   VARCHAR(20)  NOT NULL,
    body          TEXT         NOT NULL,
    internal      BOOLEAN      NOT NULL DEFAULT FALSE,   -- internal notes are hidden from customers
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_comments_ticket ON comments (ticket_id);
