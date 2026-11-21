// User management tests. Pick one with -e PROFILE=<test case id>. Run from inside perf/:
//   k6 run -e PROFILE=USR-001 scenarios/users.js
// USR-001 is more useful after scripts/seed.js has added 200+ users.
//
// PERF-USR-001  Admin lists all users under load
// PERF-USR-002  Admin creates users under load (new email every time)
// PERF-USR-003  Admin activates / deactivates users under load
// PERF-USR-004  Customer calls GET /users: 403 must be fast
//               (an agent is allowed to list users, so the "not allowed" role is the customer)
import http from "k6/http";
import { check, sleep } from "k6";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";
import { login } from "../lib/auth.js";
import { listUsers, createUser, updateUser } from "../lib/users.js";
import { PROFILE } from "../lib/env.js";

// ---------- INIT ----------

const LOAD = JSON.parse(open("../config/load-profiles.json"));
const test = JSON.parse(open("../config/users.json"))[PROFILE];
if (!test) throw new Error(`Unknown PROFILE "${PROFILE}". Use a test case id such as USR-001.`);

export const options = { stages: LOAD[test.load], thresholds: test.thresholds };

// In USR-004 a 403 is the right answer. 2xx stays expected because setup() logs in.
if (PROFILE === "USR-004") {
  http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 403));
}

// Start time of this run, so new emails never clash with an earlier run
const runId = Date.now();

// ---------- SETUP ----------
export function setup() {
  const [email, password] = PROFILE === "USR-004"
    ? ["customer@helpdesk.local", "Customer@123"]
    : ["admin@helpdesk.local", "Admin@123"];
  const res = login(email, password);
  check(res, { "login 200": (r) => r.status === 200 });
  const token = res.json("token");

  // USR-003: 20 users to toggle, one per VU, so two VUs never fight over the same user
  const userIds = [];
  if (PROFILE === "USR-003") {
    for (let i = 0; i < 20; i++) {
      const created = createUser(token, {
        email: `perf-toggle-${runId}-${i}@perf.local`,
        password: "Perf@1234",
        fullName: `Toggle User ${i}`,
        role: "CUSTOMER",
      });
      check(created, { "setup user created": (r) => r.status === 201 });
      userIds.push(created.json("id"));
    }
  }
  return { token, userIds };
}

// ---------- ONE FUNCTION PER TEST CASE ----------

// PERF-USR-001: the whole user list (this endpoint has no paging)
function listAll(data) {
  const res = listUsers(data.token);
  check(res, {
    "list is 200": (r) => r.status === 200,
    "list is an array": (r) => Array.isArray(r.json()),
  });
}

// PERF-USR-002: a new customer account each iteration (insert + bcrypt + unique-email check)
function create(data) {
  const email = `perf-usr-${runId}-${__VU}-${__ITER}@perf.local`;
  const res = createUser(data.token, { email, password: "Perf@1234", fullName: "Perf Created", role: "CUSTOMER" });
  check(res, {
    "create is 201": (r) => r.status === 201,
    "created the right email": (r) => r.json("email") === email,
  });
}

// PERF-USR-003: this VU's user is deactivated on even iterations and activated on odd ones
function toggle(data) {
  const id = data.userIds[(__VU - 1) % data.userIds.length];
  const active = __ITER % 2 === 1;
  const res = updateUser(data.token, id, { active });
  check(res, {
    "update is 200": (r) => r.status === 200,
    "active flag saved": (r) => r.json("active") === active,
  });
}

// PERF-USR-004: the customer is refused before any database work
function forbidden(data) {
  const res = listUsers(data.token);
  check(res, { "list is 403": (r) => r.status === 403 });
}

const TESTS = {
  "USR-001": listAll,
  "USR-002": create,
  "USR-003": toggle,
  "USR-004": forbidden,
};

// ---------- VU CODE ----------
export default function (data) {
  TESTS[PROFILE](data);
  sleep(1);
}

// ---------- END ----------
// USR-003: leave every toggle user active again, so the next run starts clean
export function teardown(data) {
  data.userIds.forEach((id) => updateUser(data.token, id, { active: true }));
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: " ", enableColors: true }),
    [`results/users-${PROFILE}.json`]: JSON.stringify(data, null, 2),
  };
}
