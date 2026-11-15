// Agent- and admin-side ticket tests. Pick one with -e PROFILE=<test case id>. Run from inside perf/:
//   k6 run -e PROFILE=TKS-001 scenarios/agent-journey.js
//
// PERF-TKC-003  Create ticket for a customer           PERF-TKU-002  Assign + change priority (SLA recomputed)
// PERF-TKS-001  Dashboard stats smoke                  PERF-TKU-003  Many agents update ONE ticket
// PERF-TKS-002  Dashboard stats under load             PERF-TKU-004  CLOSED -> OPEN: 409
// PERF-TKU-001  OPEN -> IN_PROGRESS                    PERF-TKD-001  Admin deletes tickets with comments
// PERF-CMT-002  Agent public reply / internal note
import http from "k6/http";
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";
import { Rate } from "k6/metrics";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";
import { login } from "../lib/auth.js";
import { createTicket, ticketStats, getTicket, updateTicket, deleteTicket, addComment } from "../lib/tickets.js";
import { PROFILE } from "../lib/env.js";

// ---------- INIT ----------

const LOAD = JSON.parse(open("../config/load-profiles.json"));
const test = JSON.parse(open("../config/agent-journey.json"))[PROFILE];
if (!test) throw new Error(`Unknown PROFILE "${PROFILE}". Use a test case id such as TKS-001.`);

export const options = { stages: LOAD[test.load], thresholds: test.thresholds };

// 409 is a correct answer in these two tests (2xx stays expected for setup and normal updates)
if (PROFILE === "TKU-003" || PROFILE === "TKU-004") {
    http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409));
}
// TKD-001 reads the ticket back after deleting it, and 404 is the proof that it is gone
if (PROFILE === "TKD-001") http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 404));

// TKU-003: share of updates rejected because another agent saved the ticket first
const conflicts = new Rate("update_conflicts");

const CUSTOMER = "customer@helpdesk.local";
const samples = new SharedArray("tickets", () => JSON.parse(open("../data/tickets.json")));
const CATEGORIES = ["BILLING", "BUG", "FEATURE_REQUEST", "ACCOUNT", "OTHER"];
const pick = (list) => list[Math.floor(Math.random() * list.length)];

// A random ticket that an agent opens on behalf of the demo customer
function ticketForCustomer() {
    const s = pick(samples);
    return { subject: s.subject, description: s.description, category: pick(CATEGORIES), customerEmail: CUSTOMER };
}

// ---------- SETUP ----------
export function setup() {
    const res = login("agent1@helpdesk.local", "Agent@123");
    check(res, { "agent login 200": (r) => r.status === 200 });
    const data = { token: res.json("token"), agentId: res.json("user.id") };

    if (PROFILE === "TKU-003") {
        // The one ticket every VU fights over
        data.id = createTicket(data.token, ticketForCustomer()).json("id");
    }
    if (PROFILE === "TKU-004") {
        // 20 tickets moved straight to CLOSED (allowed from OPEN), which is final
        data.ids = [];
        for (let i = 0; i < 20; i++) {
            const id = createTicket(data.token, ticketForCustomer()).json("id");
            updateTicket(data.token, id, { status: "CLOSED" });
            data.ids.push(id);
        }
    }
    if (PROFILE === "TKD-001") {
        data.adminToken = login("admin@helpdesk.local", "Admin@123").json("token");
    }
    if (PROFILE === "CMT-002") {
        // One ticket per VU
        data.ids = [];
        for (let i = 0; i < 20; i++) data.ids.push(createTicket(data.token, ticketForCustomer()).json("id"));
    }
    return data;
}

// ---------- ONE FUNCTION PER TEST CASE ----------

// PERF-TKC-003: the agent creates it, but the customer must own it
function createForCustomer(data) {
    const res = createTicket(data.token, ticketForCustomer());
    check(res, {
        "ticket created (201)": (r) => r.status === 201,
        "owner is the customer, not the agent": (r) => r.json("customerEmail") === CUSTOMER,
        "status is OPEN": (r) => r.json("status") === "OPEN",
    });
}

// PERF-TKS-001 and 002: the dashboard header cards
function stats(data) {
    const res = ticketStats(data.token);
    check(res, {
        "stats is 200": (r) => r.status === 200,
        "all counts present": (r) => ["open", "inProgress", "waitingOnCustomer", "resolved", "closed",
            "slaBreached", "unassigned"].every((k) => typeof r.json(k) === "number"),
    });
}

// PERF-TKU-001: a fresh OPEN ticket each iteration, then pick it up
function startWork(data) {
    const id = createTicket(data.token, ticketForCustomer()).json("id");
    const res = updateTicket(data.token, id, { status: "IN_PROGRESS" });
    check(res, {
        "update is 200": (r) => r.status === 200,
        "status is IN_PROGRESS": (r) => r.json("status") === "IN_PROGRESS",
        "picked up by me": (r) => r.json("assignee.id") === data.agentId,
    });
}

// PERF-TKU-002: assign + raise priority. HIGH has a shorter SLA, so the due date must move earlier.
function assignAndPrioritise(data) {
    const created = createTicket(data.token, ticketForCustomer());
    const oldDue = created.json("sla.dueAt");
    const res = updateTicket(data.token, created.json("id"), { assigneeId: data.agentId, priority: "HIGH" });
    check(res, {
        "update is 200": (r) => r.status === 200,
        "assigned to agent1": (r) => r.json("assignee.id") === data.agentId,
        "priority is HIGH": (r) => r.json("priority") === "HIGH",
        "SLA due date moved earlier": (r) => new Date(r.json("sla.dueAt")) < new Date(oldDue),
    });
}

// PERF-TKU-003: every VU changes the priority of the SAME ticket
function sameTicket(data) {
    const res = updateTicket(data.token, data.id, { priority: pick(["LOW", "MEDIUM", "HIGH", "URGENT"]) });
    conflicts.add(res.status === 409);
    check(res, { "200 or 409, never 5xx": (r) => r.status === 200 || r.status === 409 });
}

// PERF-TKU-004: CLOSED is final, so reopening must be refused
function invalidTransition(data) {
    const res = updateTicket(data.token, pick(data.ids), { status: "OPEN" });
    check(res, {
        "CLOSED -> OPEN is 409": (r) => r.status === 409,
        "message explains why": (r) => r.json("message").includes("Cannot move ticket from CLOSED"),
    });
}

// PERF-TKD-001: admin creates a ticket with 5 comments, deletes it, and checks it is gone.
// Data is made inside the iteration: LP-02 deletes ~4 000 tickets, too many to prepare in setup().
function deleteWithComments(data) {
    const id = createTicket(data.adminToken, ticketForCustomer()).json("id");
    for (let c = 0; c < 5; c++) addComment(data.adminToken, id, { body: `Comment ${c}`, internal: false });

    const res = deleteTicket(data.adminToken, id);
    check(res, { "delete is 204": (r) => r.status === 204 });
    check(getTicket(data.adminToken, id), { "deleted ticket is 404": (r) => r.status === 404 });
}

// PERF-CMT-002: public reply on even iterations, internal note on odd ones
function agentReply(data) {
    const id = data.ids[(__VU - 1) % data.ids.length];
    const internal = __ITER % 2 === 1;
    const res = addComment(data.token, id, { body: internal ? "Internal note" : "Public reply", internal });
    check(res, {
        "comment created (201)": (r) => r.status === 201,
        "internal flag saved": (r) => r.json("internal") === internal,
        "written by the agent": (r) => r.json("authorRole") === "AGENT",
    });
}

const TESTS = {
    "TKC-003": createForCustomer,
    "TKS-001": stats,
    "TKS-002": stats,
    "TKU-001": startWork,
    "TKU-002": assignAndPrioritise,
    "TKU-003": sameTicket,
    "TKU-004": invalidTransition,
    "TKD-001": deleteWithComments,
    "CMT-002": agentReply,
};

// ---------- VU CODE ----------
export default function (data) {
    TESTS[PROFILE](data);
    sleep(1);
}

// ---------- TEARDOWN ----------
export function teardown(data) {
    if (PROFILE === "TKU-003") {
        // Final state is consistent: the ticket still reads back cleanly with a valid priority
        const res = getTicket(data.token, data.id);
        check(res, {
            "ticket still readable": (r) => r.status === 200,
            "priority is a valid value": (r) => ["LOW", "MEDIUM", "HIGH", "URGENT"].includes(r.json("priority")),
        });
        console.log(`TKU-003 final state: priority=${res.json("priority")} status=${res.json("status")}`);
    }
}

// ---------- END ----------
export function handleSummary(data) {
    return {
        stdout: textSummary(data, { indent: " ", enableColors: true }),
        [`results/agent-journey-${PROFILE}.json`]: JSON.stringify(data, null, 2),
    };
}
