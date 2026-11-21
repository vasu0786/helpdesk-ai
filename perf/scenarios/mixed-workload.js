// Two kinds of user at the same time, each as its own k6 scenario. Run from inside perf/:
//   k6 run -e PROFILE=TKS-003 scenarios/mixed-workload.js
//
// PERF-TKS-003  14 customers create tickets while 6 agents read the dashboard stats (writes vs reads)
// PERF-CMT-004  10 customers + 10 agents comment on the SAME ticket; no comment may be lost
//
// Journeys (lib/journeys.js), 70 % customers / 20 % agents / 10 % logins. Seed tickets first:
//   k6 run -e COUNT=2000 scripts/seed-tickets.js
// PERF-JNY-003  Mixed load (LP-02, 20 VUs)
// PERF-JNY-004  Mixed stress (LP-03, 50 -> 100 -> 200 VUs); prints p95 per minute to find the break
// PERF-JNY-005  Mixed soak, 30 min (LP-05). Start the app with short SLAs so the SLA job has work,
//               see the README. Prints p95 drift and the p95 per 5-second slice of the minute.
// PERF-JNY-006  Breakpoint (LP-07): arrival rate 10 -> 500 journeys/s, stops at 5 % errors.
//               Run last, then straight away:
// PERF-JNY-007  Recovery smoke (LP-01) without restarting the app
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";
import { login } from "../lib/auth.js";
import { createTicket, ticketStats, listComments, addComment } from "../lib/tickets.js";
import {
    customerJourney, agentJourney, loginJourney, noThinkTime, useTimeTags, requireOpenTickets,
} from "../lib/journeys.js";
import { PROFILE } from "../lib/env.js";

// ---------- INIT ----------

// Here the profile holds whole k6 "scenarios" (one per user type), so it is the options as it is
const profile = JSON.parse(open("../config/mixed-workload.json"))[PROFILE];
if (!profile) throw new Error(`Unknown PROFILE "${PROFILE}". Use TKS-003, CMT-004 or JNY-003..007.`);

const SCENARIOS = ["customers", "agents", "logins"];
const MINUTES = { "JNY-004": 4, "JNY-005": 33 };

// k6 only reports a tagged sub-metric that has a threshold. These always pass ("p(95)>=0");
// they are here so the summary has p95 per minute / per slice for the report below.
if (MINUTES[PROFILE]) {
    useTimeTags();
    for (let m = 0; m < MINUTES[PROFILE]; m++) {
        profile.thresholds[`http_req_duration{minute:${m}}`] = ["p(95)>=0"];
        if (PROFILE === "JNY-004") {
            for (const s of SCENARIOS) profile.thresholds[`http_req_duration{scenario:${s},minute:${m}}`] = ["p(95)>=0"];
        }
    }
}
if (PROFILE === "JNY-005") {
    for (let sl = 0; sl < 12; sl++) profile.thresholds[`http_req_duration{slice:${sl}}`] = ["p(95)>=0"];
}
// Breakpoint: no think time, and count each kind of failure so the first error type can be named
if (PROFILE === "JNY-006") {
    noThinkTime();
    for (const st of [0, 401, 409, 500, 503]) profile.thresholds[`http_reqs{status:${st}}`] = ["count>=0"];
}

export const options = profile;

const samples = new SharedArray("tickets", () => JSON.parse(open("../data/tickets.json")));
const pick = (list) => list[Math.floor(Math.random() * list.length)];

// ---------- SETUP: one login per role, shared by all VUs of that role ----------
export function setup() {
    if (PROFILE.startsWith("JNY")) {
        requireOpenTickets(); // the agent journey works on OPEN tickets
        return {};
    }
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

// ---------- PERF-JNY-003..007 ----------

export function customers() { customerJourney(); sleep(1); }
export function agents() { agentJourney(); sleep(1); }
export function logins() { loginJourney(); sleep(1); }

// JNY-006: each arrival is one journey, picked 70 / 20 / 10, with no waiting
export function breakpoint() {
    const r = Math.random();
    if (r < 0.7) customerJourney();
    else if (r < 0.9) agentJourney();
    else loginJourney();
}

// JNY-007: one VU walks all three journeys in turn
export function everyone() {
    customerJourney();
    agentJourney();
    loginJourney();
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
// The numbers the JNY test cases ask for, worked out from the summary
function report(data) {
    // A tag with no requests still reports p95 = 0, so 0 means "no data" here
    const p95 = (key) => (data.metrics[key] && data.metrics[key].values["p(95)"]) || null;
    const ms = (v) => (v == null ? "-" : `${Math.round(v)} ms`);
    const lines = [];

    if (PROFILE === "JNY-004") {
        const vus = ["0 -> 50", "50 -> 100", "100 -> 200", "200 -> 0"];
        lines.push("p95 per minute    customers   agents   logins");
        let broke = null;
        for (let m = 0; m < 4; m++) {
            const v = SCENARIOS.map((s) => p95(`http_req_duration{scenario:${s},minute:${m}}`));
            lines.push(`minute ${m} (${vus[m]} VUs)  ${v.map(ms).join("   ")}`);
            if (broke === null && v.some((x) => x > 1000)) broke = m;
        }
        lines.push(broke === null ? "No scenario went over 1 s p95."
            : `First minute with a scenario over 1 s p95: minute ${broke} (${vus[broke]} VUs).`);
    }

    if (PROFILE === "JNY-005") {
        const perMin = [];
        for (let m = 0; m < 33; m++) perMin.push(p95(`http_req_duration{minute:${m}}`));
        lines.push("p95 per minute: " + perMin.map((v, m) => `${m}:${ms(v)}`).join("  "));
        // LP-05: minute 5 against the last 5 minutes of the hold (minutes 27-31)
        const early = perMin[4];
        const lateList = perMin.slice(27, 32).filter((v) => v != null);
        const late = lateList.reduce((a, b) => a + b, 0) / lateList.length;
        const drift = ((late - early) / early) * 100;
        if (!early || !lateList.length) lines.push("Drift: not enough data, the run must last the full 33 minutes.");
        else lines.push(`Drift: minute 5 = ${ms(early)}, last 5 minutes = ${ms(late)}, change ${drift.toFixed(1)} % `
            + (drift < 20 ? "(PASS, under 20 %)" : "(FAIL, 20 % or more)"));
        // The SLA job ticks once a minute, always in about the same 5-second slice
        const slices = [];
        for (let sl = 0; sl < 12; sl++) slices.push(p95(`http_req_duration{slice:${sl}}`));
        const sorted = slices.filter((v) => v != null).sort((a, b) => a - b);
        const median = sorted[Math.floor(sorted.length / 2)];
        const worst = Math.max(...sorted);
        lines.push("p95 per 5 s slice of the minute: " + slices.map((v, i) => `${i * 5}s:${ms(v)}`).join("  "));
        lines.push(`Job-tick spike: worst slice ${ms(worst)} vs median ${ms(median)} = ${(worst / median).toFixed(2)}x `
            + (worst < 2 * median ? "(PASS, under 2x)" : "(FAIL, 2x or more)"));
    }

    if (PROFILE === "JNY-006") {
        // The arrival rate climbs in a straight line from 10 to 500 journeys/s over 600 s
        const secs = data.state.testRunDurationMs / 1000;
        const rate = 10 + (490 * Math.min(secs, 600)) / 600;
        const reqs = data.metrics.http_reqs.values.count;
        const perJourney = reqs / data.metrics.iterations.values.count;
        if (secs >= 600) lines.push("Reached 500 journeys/s without passing 5 % errors: no breakpoint found.");
        lines.push(`Stopped after ${secs.toFixed(0)} s, arrival rate then about ${rate.toFixed(0)} journeys/s`
            + ` x ${perJourney.toFixed(1)} requests each = about ${(rate * perJourney).toFixed(0)} req/s.`);
        lines.push("(abortOnFail waits 10 s after the 5 % line is crossed, so the real break is a little lower.)");
        const kinds = { 0: "timeout / connection refused", 401: "401", 409: "409 conflict", 500: "500", 503: "503" };
        lines.push("Failed requests by type: " + Object.entries(kinds)
            .map(([st, label]) => `${label}=${(data.metrics[`http_reqs{status:${st}}`] || { values: { count: 0 } }).values.count}`)
            .join(", "));
    }
    return lines.length ? `\n${PROFILE} report\n${lines.join("\n")}\n` : "";
}

export function handleSummary(data) {
    return {
        stdout: textSummary(data, { indent: " ", enableColors: true }) + report(data),
        [`results/mixed-workload-${PROFILE}.json`]: JSON.stringify(data, null, 2),
    };
}
