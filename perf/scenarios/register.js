// PERF-REG-001..003  Register smoke / load / stress with a new email every iteration
// PERF-REG-004       Duplicate email under load: every request should get 409 (PROFILE=duplicate)
// Run from inside perf/:   k6 run -e PROFILE=smoke scenarios/register.js
import http from "k6/http";
import { check, sleep } from "k6";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";
import { register } from "../lib/auth.js";
import { PROFILE } from "../lib/env.js";

// ---------- INIT: runs once per VU, before any request ----------

// Load shape and pass/fail rules for the chosen profile (smoke, load, stress, duplicate)
const profiles = JSON.parse(open("../config/register.json"));
const profile = profiles[PROFILE];

const duplicate = PROFILE === "duplicate";

// Time this VU started, so emails from this run never match emails from an earlier run
const runId = Date.now();

export const options = {
  stages: profile.stages,
  thresholds: profile.thresholds,
};

// In the duplicate run 409 is the right answer, so k6 must not count it as a failed request
if (duplicate) {
  http.setResponseCallback(http.expectedStatuses(409));
}

// ---------- VU CODE: each VU repeats this function until the stages end ----------
export default function () {
  // New run: perf-<run>-<VU>-<iteration>@perf.local is different every time.
  // Duplicate run: everyone uses the demo customer, which already exists.
  const email = duplicate
    ? "customer@helpdesk.local"
    : `perf-${runId}-${__VU}-${__ITER}@perf.local`;

  const res = register(email, "Perf@1234", "Perf User");

  if (duplicate) {
    check(res, {
      "status is 409": (r) => r.status === 409,
    });
  } else {
    check(res, {
      "status is 201": (r) => r.status === 201,
      "body has a token": (r) => typeof r.json("token") === "string" && r.json("token").length > 0,
      "body has the new user": (r) => r.json("user.email") === email,
    });
  }

  // Wait 1 s between sign-ups, like a real user would
  sleep(1);
}

// ---------- END: runs once after the test ----------
// Prints the normal summary and also saves it to results/ as JSON.
export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: " ", enableColors: true }),
    [`results/register-${PROFILE}.json`]: JSON.stringify(data, null, 2),
  };
}
