// Two kinds of user at the same time, each as its own k6 scenario. Run from inside perf/:
//   k6 run -e PROFILE=TKS-003 scenarios/mixed-workload.js
//
// PERF-TKS-003  14 customers create tickets while 6 agents read the dashboard stats (writes vs reads)
// PERF-CMT-004  10 customers + 10 agents comment on the SAME ticket; no comment may be lost
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";
import { login } from "../lib/auth.js";
import { createTicket, ticketStats, listComments, addComment } from "../lib/tickets.js";
import { PROFILE } from "../lib/env.js";

// ---------- INIT ----------

// Here the profile holds whole k6 "scenarios" (one per user type), so it is the options as it is
const profile = JSON.parse(open("../config/mixed-workload.json"))[PROFILE];
if (!profile) throw new Error(`Unknown PROFILE "${PROFILE}". Use TKS-003 or CMT-004.`);

export const options = profile;

const samples = new SharedArray("tickets", () => JSON.parse(open("../data/tickets.json")));
const pick = (list) => list[Math.floor(Math.random() * list.length)];

// ---------- SETUP: one login per role, shared by all VUs of that role ----------
export function setup() {
    const customer = login("customer@helpdesk.local", "Customer@123");
    const agent = login("agent1@helpdesk.local", "Agent@123");
    check(customer, { "customer login 200": (r) => r.status === 200 });
    check(agent, { "agent login 200": (r) => r.status === 200 });
    const data = { customerToken: customer.json("token"), agentToken: agent.json("token") };

    if (PROFILE === "CMT-004") {
        // The one ticket everybody comments on
        const s = pick(samples);
        data.id = createTicket(data.customerToken, { subject: s.subject, description: s.description }).json("id");
    }
    return data;
}

// ---------- PERF-TKS-003 ----------

// Writer: 70 % of the users
export function customerCreates(data) {
    const s = pick(samples);
    const res = createTicket(data.customerToken, { subject: s.subject, description: s.description });
    check(res, { "ticket created (201)": (r) => r.status === 201 });
    sleep(1);
}

// Reader: 30 % of the users. Stats counts the whole table while the writers keep adding rows.
export function agentStats(data) {
    const res = ticketStats(data.agentToken);
    check(res, { "stats is 200": (r) => r.status === 200 });
    sleep(1);
}

// ---------- PERF-CMT-004 ----------

export function customerComments(data) {
    const res = addComment(data.customerToken, data.id, { body: `Customer VU ${__VU} iteration ${__ITER}`, internal: false });
    check(res, { "comment created (201)": (r) => r.status === 201 });
    sleep(1);
}

export function agentComments(data) {
    const res = addComment(data.agentToken, data.id, { body: `Agent VU ${__VU} iteration ${__ITER}`, internal: false });
    check(res, { "comment created (201)": (r) => r.status === 201 });
    sleep(1);
}

// ---------- TEARDOWN ----------
export function teardown(data) {
    if (PROFILE === "CMT-004") {
        // No lost comments: this number must equal the passes of "comment created (201)"
        const count = listComments(data.agentToken, data.id).json().length;
        console.log(`CMT-004 comments stored on ticket ${data.id}: ${count}`);
    }
}

// ---------- END ----------
export function handleSummary(data) {
    return {
        stdout: textSummary(data, { indent: " ", enableColors: true }),
        [`results/mixed-workload-${PROFILE}.json`]: JSON.stringify(data, null, 2),
    };
}
