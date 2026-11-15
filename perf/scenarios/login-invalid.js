// PERF-AUTH-007  Wrong password under load: every request should get 401
// PERF-AUTH-008  Empty body under load: every request should get 400, fast (no DB, no bcrypt)
// Each iteration sends one of each. Run from inside perf/:
//   k6 run -e PROFILE=smoke scenarios/login-invalid.js
//   k6 run -e PROFILE=load  scenarios/login-invalid.js
import http from "k6/http";
import { check, sleep } from "k6";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";
import { login } from "../lib/auth.js";
import { PROFILE } from "../lib/env.js";

// ---------- INIT: runs once per VU, before any request ----------

// Load shape and pass/fail rules for the chosen profile (smoke, load)
const profiles = JSON.parse(open("../config/login-invalid.json"));
const profile = profiles[PROFILE];

export const options = {
  stages: profile.stages,
  // Separate p95 limits for the two kinds: wrong < 500 ms, empty < 50 ms
  thresholds: profile.thresholds,
};

// 400 and 401 are the right answers here, so k6 must not count them as failed.
// Anything else (200, 5xx) still counts as failed and breaks the http_req_failed threshold.
http.setResponseCallback(http.expectedStatuses(400, 401));

// ---------- VU CODE: each VU repeats this function until the stages end ----------
export default function () {
  // PERF-AUTH-007: a real account with the wrong password. The app still runs bcrypt.
  const wrong = login("customer@helpdesk.local", "Wrong@123", { kind: "wrong" });

  check(wrong, {
    "wrong password is 401": (r) => r.status === 401,
  });

  // PERF-AUTH-008: blank email and password. Validation rejects it before the DB or bcrypt.
  const empty = login("", "", { kind: "empty" });

  check(empty, {
    "empty body is 400": (r) => r.status === 400,
  });

  // Wait 1 s between attempts, like a real user would
  sleep(1);
}

// ---------- END: runs once after the test ----------
// Prints the normal summary and also saves it to results/ as JSON.
export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: " ", enableColors: true }),
    [`results/login-invalid-${PROFILE}.json`]: JSON.stringify(data, null, 2),
  };
}
