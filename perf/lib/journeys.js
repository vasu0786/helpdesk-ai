// The three user journeys used by the PERF-JNY-* tests.
// Each step is a group(), so the results show one duration per step:
//   group_duration{group:::Login}, group_duration{group:::Create ticket} ...
import { group, check, sleep } from "k6";
import { SharedArray } from "k6/data";
import exec from "k6/execution";
import { login } from "./auth.js";
import {
    createTicket, listTickets, getTicket, listComments, addComment, ticketStats, updateTicket,
} from "./tickets.js";

const samples = new SharedArray("tickets", () => JSON.parse(open("../data/tickets.json")));
const CATEGORIES = ["BILLING", "BUG", "FEATURE_REQUEST", "ACCOUNT", "OTHER"];
const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const AGENTS = ["agent1@helpdesk.local", "agent2@helpdesk.local"];
const pick = (list) => list[Math.floor(Math.random() * list.length)];

// ---------- think time and time tags ----------

// Think time: a real user reads or types for 1-3 s. The breakpoint test turns it off.
let thinking = true;
export function noThinkTime() { thinking = false; }
function think() { if (thinking) sleep(1 + Math.random() * 2); }

// Time tags (JNY-004/005): every request is tagged with the minute of the run it was sent in,
// and with the 5-second slice of the wall-clock minute (0..11). The summary can then show
// p95 per minute (drift, which VU level broke) and p95 per slice (a spike when the SLA job ticks).
let timeTags = false;
export function useTimeTags() { timeTags = true; }

// group() with the time tags refreshed first, so they are correct for this step's requests
function step(name, fn) {
    if (timeTags) {
        exec.vu.metrics.tags.minute = String(Math.floor(exec.instance.currentTestRunDuration / 60000));
        exec.vu.metrics.tags.slice = String(Math.floor(new Date().getSeconds() / 5));
    }
    return group(name, fn);
}

// ---------- the journeys ----------

// Customer: login -> create -> list my tickets -> open the new one -> reply on it
export function customerJourney() {
    const token = step("Login", () => {
        const res = login("customer@helpdesk.local", "Customer@123");
        check(res, { "customer login 200": (r) => r.status === 200 });
        return res.json("token");
    });
    if (!token) return;

    think(); // filling in the new-ticket form

    const id = step("Create ticket", () => {
        const s = pick(samples);
        const res = createTicket(token, { subject: s.subject, description: s.description, category: pick(CATEGORIES) });
        check(res, { "ticket created (201)": (r) => r.status === 201 });
        return res.status === 201 ? res.json("id") : null;
    });
    if (!id) return;

    step("My tickets", () => {
        const res = listTickets(token, { size: 20, sort: "createdAt,desc" });
        check(res, {
            "my tickets 200": (r) => r.status === 200,
            "new ticket is on page 1": (r) => r.json("content").some((t) => t.id === id),
        });
    });

    step("Open ticket", () => {
        check(getTicket(token, id), { "ticket is 200": (r) => r.status === 200 });
        check(listComments(token, id), { "comments are 200": (r) => r.status === 200 });
    });

    think(); // typing the reply

    step("Reply", () => {
        const res = addComment(token, id, { body: "Any update on this, please?", internal: false });
        check(res, { "reply created (201)": (r) => r.status === 201 });
    });
}

// Agent: login -> dashboard stats -> filter OPEN tickets -> open one -> assign + reprioritise -> reply.
// The update keeps the ticket OPEN, so the seeded data is not used up run after run.
export function agentJourney() {
    const auth = step("Login", () => {
        const res = login(AGENTS[__VU % AGENTS.length], "Agent@123");
        check(res, { "agent login 200": (r) => r.status === 200 });
        return res.status === 200 ? { token: res.json("token"), me: res.json("user.id") } : null;
    });
    if (!auth) return;

    step("Dashboard stats", () => {
        check(ticketStats(auth.token), { "stats is 200": (r) => r.status === 200 });
    });

    // A random page of the first 5, so 20 agents rarely pick the same ticket
    const id = step("Filter tickets", () => {
        const res = listTickets(auth.token, { status: "OPEN", size: 20, page: Math.floor(Math.random() * 5) });
        check(res, {
            "filter is 200": (r) => r.status === 200,
            "page has tickets": (r) => r.json("content").length > 0,
        });
        const page = res.status === 200 ? res.json("content") : [];
        return page.length ? pick(page).id : null;
    });
    if (!id) return;

    step("Open ticket", () => {
        check(getTicket(auth.token, id), { "ticket is 200": (r) => r.status === 200 });
        check(listComments(auth.token, id), { "comments are 200": (r) => r.status === 200 });
    });

    think(); // deciding what to do with it

    step("Update ticket", () => {
        const res = updateTicket(auth.token, id, { assigneeId: auth.me, priority: pick(PRIORITIES) });
        check(res, {
            "update is 200": (r) => r.status === 200,
            "assigned to me": (r) => r.json("assignee.id") === auth.me,
        });
    });

    think(); // typing the reply

    step("Reply", () => {
        const res = addComment(auth.token, id, { body: "We are looking into this.", internal: false });
        check(res, { "agent reply created (201)": (r) => r.status === 201 });
    });
}

// Login only: the 10 % of traffic that is people signing in
export function loginJourney() {
    step("Login", () => {
        const res = login("customer@helpdesk.local", "Customer@123");
        check(res, { "login 200": (r) => r.status === 200, "has a token": (r) => !!r.json("token") });
    });
}

// The agent journey needs OPEN tickets to work on. Call from setup().
export function requireOpenTickets(minimum = 100) {
    const token = login("agent1@helpdesk.local", "Agent@123").json("token");
    const open = listTickets(token, { status: "OPEN", size: 1 }).json("page.totalElements");
    if (open < minimum) {
        throw new Error(`Only ${open} OPEN tickets. Seed first: k6 run -e COUNT=2000 scripts/seed-tickets.js`);
    }
}
