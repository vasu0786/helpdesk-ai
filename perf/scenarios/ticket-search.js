// Ticket list and search tests. Pick one with -e PROFILE=<test case id>. Run from inside perf/:
//   k6 run -e PROFILE=TKL-001 scenarios/ticket-search.js
// Seed first:  k6 run -e COUNT=2000 scripts/seed-tickets.js   (TKL-006/007 need ~10 000 tickets)
//
// PERF-TKL-001  List smoke (agent)                     PERF-TKL-006  Search on 10 000 tickets
// PERF-TKL-002  Customer lists own tickets             PERF-TKL-007  Deep paging (page 50) vs page 0
// PERF-TKL-003  Agent lists all tickets                PERF-TKL-008  size=100 + sort (big response)
// PERF-TKL-004  Random filter combinations             PERF-TKL-009  Quick filters: unassigned / breached
// PERF-TKL-005  Free-text search (q)                   PERF-TKL-010  List stress (agent)
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";
import { login } from "../lib/auth.js";
import { listTickets } from "../lib/tickets.js";
import { PROFILE } from "../lib/env.js";

// ---------- INIT ----------

const LOAD = JSON.parse(open("../config/load-profiles.json"));
const test = JSON.parse(open("../config/ticket-search.json"))[PROFILE];
if (!test) throw new Error(`Unknown PROFILE "${PROFILE}". Use a test case id such as TKL-001.`);

export const options = { stages: LOAD[test.load], thresholds: test.thresholds };

const CUSTOMER = "customer@helpdesk.local";
// Search words come from the same file the seed script used for subjects
const samples = new SharedArray("tickets", () => JSON.parse(open("../data/tickets.json")));
const STATUSES = ["OPEN", "IN_PROGRESS", "WAITING_ON_CUSTOMER", "RESOLVED", "CLOSED"];
const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const CATEGORIES = ["BILLING", "BUG", "FEATURE_REQUEST", "ACCOUNT", "OTHER"];

// Function that return a random values from the above 3 list as the calling statment.
const pick = (list) => list[Math.floor(Math.random() * list.length)];

// Checks every list response must pass
const PAGE_OK = {
    "list is 200": (r) => r.status === 200,
    "has content[]": (r) => Array.isArray(r.json("content")),
};

// ---------- SETUP ----------
export function setup() {
    // TKL-002 is the only customer test; all the others list as an agent
    const [email, password] = PROFILE === "TKL-002"
        ? [CUSTOMER, "Customer@123"]
        : ["agent1@helpdesk.local", "Agent@123"];
    const res = login(email, password);
    check(res, { "login 200": (r) => r.status === 200 });
    return { token: res.json("token") };
}

// ---------- ONE FUNCTION PER TEST CASE ----------

// PERF-TKL-001, 003, 010: default page (20 newest tickets), no filters
function listDefault(data) {
    check(listTickets(data.token), PAGE_OK);
}

// PERF-TKL-002: the server must add "WHERE customer_email = me" on its own
function listOwn(data) {
    const res = listTickets(data.token);
    check(res, PAGE_OK);
    check(res, {
        "only my own tickets": (r) => r.json("content").every((t) => t.customerEmail === CUSTOMER),
    });
}

// PERF-TKL-004: one of three filter combinations, random values, tagged so each gets its own p95
function listFiltered(data) {
    const combos = {
        "status": () => ({ status: pick(STATUSES) }),
        "status+priority": () => ({ status: pick(STATUSES), priority: pick(PRIORITIES) }),
        "status+priority+category": () => ({ status: pick(STATUSES), priority: pick(PRIORITIES), category: pick(CATEGORIES) }),
    };
    const name = pick(Object.keys(combos));
    const query = combos[name]();
    const res = listTickets(data.token, query, { filters: name });
    check(res, PAGE_OK);
    check(res, { "every row matches the status filter": (r) => r.json("content").every((t) => t.status === query.status) });
}

// PERF-TKL-005 and 006: free-text search with a word that really is in the seeded subjects
function search(data) {
    const word = pick(samples).keyword;
    const res = listTickets(data.token, { q: word });
    check(res, PAGE_OK);
    check(res, { "search finds something": (r) => r.json("page.totalElements") > 0 });
}

// PERF-TKL-007: page 50 (OFFSET 1000) next to page 0, so the two p95s can be compared
function deepPaging(data) {
    check(listTickets(data.token, { page: 0, size: 20 }, { kind: "first" }), PAGE_OK);
    const res = listTickets(data.token, { page: 50, size: 20 }, { kind: "deep" });
    check(res, PAGE_OK);
    check(res, { "page 50 is full": (r) => r.json("content").length === 20 });
}

// PERF-TKL-008: 100 rows sorted newest first; the body must stay under 200 KB
function bigPage(data) {
    const res = listTickets(data.token, { size: 100, sort: "createdAt,desc" });
    check(res, PAGE_OK);
    check(res, {
        "100 rows returned": (r) => r.json("content").length === 100,
        "body under 200 KB": (r) => r.body.length < 200 * 1024,
    });
}

// PERF-TKL-009: the two dashboard quick filters, one of each per iteration
function quickFilters(data) {
    const unassigned = listTickets(data.token, { unassigned: true }, { kind: "unassigned" });
    check(unassigned, PAGE_OK);
    check(unassigned, { "all unassigned": (r) => r.json("content").every((t) => t.assignee === null) });

    const breached = listTickets(data.token, { breached: true }, { kind: "breached" });
    check(breached, PAGE_OK);
    check(breached, { "all breached": (r) => r.json("content").every((t) => t.sla.breached === true) });
}

const TESTS = {
    "TKL-001": listDefault,
    "TKL-002": listOwn,
    "TKL-003": listDefault,
    "TKL-004": listFiltered,
    "TKL-005": search,
    "TKL-006": search,
    "TKL-007": deepPaging,
    "TKL-008": bigPage,
    "TKL-009": quickFilters,
    "TKL-010": listDefault,
};

// ---------- VU CODE ----------
export default function (data) {
    TESTS[PROFILE](data);
    sleep(1);
}

// ---------- END ----------
export function handleSummary(data) {
    return {
        stdout: textSummary(data, { indent: " ", enableColors: true }),
        [`results/ticket-search-${PROFILE}.json`]: JSON.stringify(data, null, 2),
    };
}
