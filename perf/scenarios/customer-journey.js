// Customer-side ticket tests. Pick one with -e PROFILE=<test case id>. Run from inside perf/:
//   k6 run -e PROFILE=TKC-001 scenarios/customer-journey.js
//
// PERF-TKC-001  Create ticket smoke                    PERF-TKG-001  Open own ticket (with comments)
// PERF-TKC-002  Create ticket under load               PERF-TKG-002  Open another customer's ticket: 404
// PERF-TKC-004  Create ticket stress + row count       PERF-CMT-001  Reply on own ticket
// PERF-TKC-005  Blank subject under load: 400          PERF-CMT-003  List 200 comments on one ticket
// PERF-JNY-001  Whole customer journey: login -> create -> list -> open -> reply (lib/journeys.js)
import http from "k6/http";
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";
import { login } from "../lib/auth.js";
import { createTicket, listTickets, getTicket, listComments, addComment } from "../lib/tickets.js";
import { customerJourney } from "../lib/journeys.js";
import { PROFILE } from "../lib/env.js";

// ---------- INIT ----------

// The test case picks its load profile (LP-01/02/03) and its own thresholds
const LOAD = JSON.parse(open("../config/load-profiles.json"));
const test = JSON.parse(open("../config/customer-journey.json"))[PROFILE];
if (!test) throw new Error(`Unknown PROFILE "${PROFILE}". Use a test case id such as TKC-001.`);

export const options = { stages: LOAD[test.load], thresholds: test.thresholds };

// Negative tests: the error status is the right answer, so it must not count as failed.
// 2xx stays expected because setup() still logs in and creates data.
if (PROFILE === "TKC-005") http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 400));
if (PROFILE === "TKG-002") http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 404));

const CUSTOMER = "customer@helpdesk.local";
const samples = new SharedArray("tickets", () => JSON.parse(open("../data/tickets.json")));
const CATEGORIES = ["BILLING", "BUG", "FEATURE_REQUEST", "ACCOUNT", "OTHER"];
const pick = (list) => list[Math.floor(Math.random() * list.length)];

// A random, valid ticket body from data/tickets.json
function randomTicket() {
    const s = pick(samples);
    return { subject: s.subject, description: s.description, category: pick(CATEGORIES) };
}

// Checks every successful create must pass
const CREATED = {
    "ticket created (201)": (r) => r.status === 201,
    "has an id": (r) => typeof r.json("id") === "number",
    "status is OPEN": (r) => r.json("status") === "OPEN",
    "SLA due date is set": (r) => r.json("sla.dueAt") != null,
    "owner is the customer": (r) => r.json("customerEmail") === CUSTOMER,
};

// ---------- SETUP: runs once, its return value is passed to every VU as `data` ----------
export function setup() {
    const res = login(CUSTOMER, "Customer@123");
    check(res, { "customer login 200": (r) => r.status === 200 });
    const data = { token: res.json("token") };

    if (PROFILE === "TKC-004") {
        // Row count before the stress run; teardown() compares it with the count after
        data.before = listTickets(data.token, { size: 1 }).json("page.totalElements");
    }
    if (PROFILE === "TKG-001") {
        // 20 own tickets with 3 comments each, so "open ticket" has something to show
        data.ids = [];
        for (let i = 0; i < 20; i++) {
            const id = createTicket(data.token, randomTicket()).json("id");
            for (let c = 0; c < 3; c++) addComment(data.token, id, { body: `Comment ${c}`, internal: false });
            data.ids.push(id);
        }
    }
    if (PROFILE === "TKG-002") {
        // An agent creates tickets that belong to a DIFFERENT customer
        const agent = login("agent1@helpdesk.local", "Agent@123").json("token");
        data.ids = [];
        for (let i = 0; i < 20; i++) {
            const body = { ...randomTicket(), customerEmail: "someone.else@helpdesk.local" };
            data.ids.push(createTicket(agent, body).json("id"));
        }
    }
    if (PROFILE === "CMT-001") {
        // One ticket per VU (LP-02 peaks at 20 VUs)
        data.ids = [];
        for (let i = 0; i < 20; i++) data.ids.push(createTicket(data.token, randomTicket()).json("id"));
    }
    if (PROFILE === "CMT-003") {
        // One ticket with 200 comments
        data.id = createTicket(data.token, randomTicket()).json("id");
        for (let c = 0; c < 200; c++) addComment(data.token, data.id, { body: `Comment number ${c}`, internal: false });
    }
    return data;
}

// ---------- ONE FUNCTION PER TEST CASE ----------

// PERF-TKC-001: the same fixed ticket every time
function createSmoke(data) {
    const res = createTicket(data.token, {
        subject: "Perf smoke ticket",
        description: "Created by the k6 smoke test",
        category: "BILLING",
    });
    check(res, CREATED);
}

// PERF-TKC-002 and 004: random subject, description and category
function createRandom(data) {
    check(createTicket(data.token, randomTicket()), CREATED);
}

// PERF-TKC-005: blank subject must be rejected by validation before any DB work
function createInvalid(data) {
    const res = createTicket(data.token, { subject: "", description: "Blank subject", category: "BUG" });
    check(res, {
        "blank subject is 400": (r) => r.status === 400,
        "error names the subject field": (r) => r.json("fieldErrors.subject") != null,
    });
}

// PERF-TKG-001: open one of my tickets, like the ticket page does (ticket + its comments)
function openOwn(data) {
    const id = pick(data.ids);
    const res = getTicket(data.token, id);
    check(res, {
        "ticket is 200": (r) => r.status === 200,
        "right ticket": (r) => r.json("id") === id,
        "it is mine": (r) => r.json("customerEmail") === CUSTOMER,
    });
    check(listComments(data.token, id), { "comments are 200": (r) => r.status === 200 });
}

// PERF-TKG-002: someone else's ticket must look like it does not exist
function openOthers(data) {
    const res = getTicket(data.token, pick(data.ids));
    check(res, { "other customer's ticket is 404": (r) => r.status === 404 });
}

// PERF-CMT-001: each VU replies on its own ticket
function reply(data) {
    const id = data.ids[(__VU - 1) % data.ids.length];
    const res = addComment(data.token, id, { body: `Reply from VU ${__VU}, iteration ${__ITER}`, internal: false });
    check(res, {
        "comment created (201)": (r) => r.status === 201,
        "on the right ticket": (r) => r.json("ticketId") === id,
        "written by the customer": (r) => r.json("authorRole") === "CUSTOMER",
        "not internal": (r) => r.json("internal") === false,
    });
}

// PERF-CMT-003: read a long comment thread
function listLongThread(data) {
    const res = listComments(data.token, data.id);
    check(res, {
        "comments are 200": (r) => r.status === 200,
        "all 200+ comments returned": (r) => r.json().length >= 200,
    });
}

const TESTS = {
    "TKC-001": createSmoke,
    "TKC-002": createRandom,
    "TKC-004": createRandom,
    "TKC-005": createInvalid,
    "TKG-001": openOwn,
    "TKG-002": openOthers,
    "CMT-001": reply,
    "CMT-003": listLongThread,
    "JNY-001": customerJourney,
};

// ---------- VU CODE ----------
export default function (data) {
    TESTS[PROFILE](data);
    sleep(1);
}

// ---------- TEARDOWN: runs once after all VUs finish ----------
export function teardown(data) {
    if (PROFILE === "TKC-004") {
        // No lost or duplicate tickets: this number must equal the passes of "ticket created (201)"
        const after = listTickets(data.token, { size: 1 }).json("page.totalElements");
        console.log(`TKC-004 rows added during the run: ${after - data.before}`);
    }
}

// ---------- END ----------
export function handleSummary(data) {
    return {
        stdout: textSummary(data, { indent: " ", enableColors: true }),
        [`results/customer-journey-${PROFILE}.json`]: JSON.stringify(data, null, 2),
    };
}
