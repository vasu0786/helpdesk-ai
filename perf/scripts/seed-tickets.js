// One-off data setup for the list/search/stats tests (TKL-*, TKS-*):
//   k6 run -e COUNT=2000 scripts/seed-tickets.js      (TKL-001..005, 008..010, TKS-001)
//   k6 run -e COUNT=8000 scripts/seed-tickets.js      (adds more, for TKL-006/007 and TKS-002 which need ~10 000)
// agent1 creates each ticket on behalf of a customer, then moves it to a random status and priority,
// so the table has a realistic mix for the filters.
import { check } from "k6";
import { SharedArray } from "k6/data";
import exec from "k6/execution";
import { login } from "../lib/auth.js";
import { createTicket, updateTicket } from "../lib/tickets.js";

const COUNT = Number(__ENV.COUNT || 2000);

// 10 VUs share the COUNT iterations, so seeding is 10x faster than one loop
export const options = {
    scenarios: {
        seed: { executor: "shared-iterations", vus: 10, iterations: COUNT, maxDuration: "30m" },
    },
};

const samples = new SharedArray("tickets", () => JSON.parse(open("../data/tickets.json")));
const CATEGORIES = ["BILLING", "BUG", "FEATURE_REQUEST", "ACCOUNT", "OTHER"];
const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"];
// Status mix and the PATCH steps to reach each one (OPEN needs none; CLOSED is allowed straight from OPEN)
const STATUS_PATHS = [
    [], [], [], [], [], [], [], [],                   // 40 % OPEN
    ["IN_PROGRESS"], ["IN_PROGRESS"], ["IN_PROGRESS"], ["IN_PROGRESS"], ["IN_PROGRESS"], // 25 %
    ["IN_PROGRESS", "WAITING_ON_CUSTOMER"], ["IN_PROGRESS", "WAITING_ON_CUSTOMER"],     // 10 %
    ["IN_PROGRESS", "RESOLVED"], ["IN_PROGRESS", "RESOLVED"], ["IN_PROGRESS", "RESOLVED"], // 15 %
    ["CLOSED"], ["CLOSED"],                           // 10 %
];
const pick = (list) => list[Math.floor(Math.random() * list.length)];

export function setup() {
    const res = login("agent1@helpdesk.local", "Agent@123");
    check(res, { "agent login 200": (r) => r.status === 200 });
    return { token: res.json("token") };
}

export default function (data) {
    const i = exec.scenario.iterationInTest;
    const sample = samples[i % samples.length];

    // Every 10th ticket belongs to the demo customer (so it has 20+ tickets for TKL-002),
    // the rest are spread over 200 perf customers.
    const owner = i % 10 === 0 ? "customer@helpdesk.local" : `perf-${i % 200}@helpdesk.ai`;

    const created = createTicket(data.token, {
        subject: `${sample.subject} #${i}`,
        description: sample.description,
        category: pick(CATEGORIES),
        customerEmail: owner,
    });
    check(created, { "ticket created (201)": (r) => r.status === 201 });
    if (created.status !== 201) return;
    const id = created.json("id");

    // The first PATCH also sets the priority; the next steps only move the status
    const steps = pick(STATUS_PATHS);
    const priority = pick(PRIORITIES);
    if (steps.length === 0 && priority !== "MEDIUM") {
        const res = updateTicket(data.token, id, { priority });
        check(res, { "ticket updated (200)": (r) => r.status === 200 });
    }
    steps.forEach((status, n) => {
        const res = updateTicket(data.token, id, n === 0 ? { status, priority } : { status });
        check(res, { "ticket updated (200)": (r) => r.status === 200 });
    });
}
