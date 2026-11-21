// PERF-AUTH-001..005  Login smoke / load / stress / spike / soak (pick one with -e PROFILE=...)
// PERF-AUTH-010       Every iteration also calls /auth/me with the new token
// Run from inside perf/:   k6 run -e PROFILE=smoke scenarios/login.js
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";
import { login, me } from "../lib/auth.js";
import { PROFILE } from "../lib/env.js";


import exec from "k6/execution";


// ---------- INIT: runs once per VU, before any request ----------

// Load shape and pass/fail rules for the chosen profile (smoke, load, ...)
const profiles = JSON.parse(open("../config/login.json"));
const profile = profiles[PROFILE];

// The accounts to log in with. SharedArray reads the file once for all VUs.
// PERF-AUTH-009: -e USERS_FILE=../data/perf-users.json swaps the 4 demo accounts for the 200 seeded ones.
const USERS_FILE = __ENV.USERS_FILE;
const users = new SharedArray("users", () => JSON.parse(open(USERS_FILE || "../data/users.json")));

export const options = profile;


// ---------- VU CODE: each VU repeats this function until the stages end ----------
export default function () {
  // The smoke run has only VU 1, so it always uses the first account (admin).
  // With more VUs, each VU takes the next account in the list.
  // PERF-AUTH-009: with the 200 seeded accounts, count iterations across the whole test instead,
  // so all 200 accounts get used (20 VUs would only ever touch 20 of them).
  const index = USERS_FILE ? exec.scenario.iterationInTest : __VU - 1;
  const user = users[index % users.length];

  const res = login(user.email, user.password);

  // check() records pass/fail and keeps going. The "checks" threshold uses the results.
  check(res, {
    "status is 200": (r) => r.status === 200,
    "body has a token": (r) => typeof r.json("token") === "string" && r.json("token").length > 0,
    "body has the right user": (r) => r.json("user.email") === user.email,
  });

  // PERF-AUTH-010: use the token we just got, to prove it really works
  const meRes = me(res.json("token"));

  check(meRes, {
    "/me status is 200": (r) => r.status === 200,
    "/me returns the same user": (r) => r.json("email") === user.email,
  });

  // Wait 1 s between logins, like a real user would
  if (PROFILE !== "throughput") sleep(1);

}

// ---------- END: runs once after the test ----------
// Prints the normal summary and also saves it to results/ as JSON.
export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: " ", enableColors: true }),
    // The AUTH-009 run gets its own file, so it does not overwrite the normal load result
    [`results/login-${PROFILE}${USERS_FILE ? "-200-users" : ""}.json`]: JSON.stringify(data, null, 2),
  };
}
