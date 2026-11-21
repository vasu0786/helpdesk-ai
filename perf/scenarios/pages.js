// Static page tests (HTML, JS and CSS served by Spring). Pick one with -e PROFILE=<test case id>.
// Run from inside perf/:   k6 run -e PROFILE=PG-001 scenarios/pages.js
//
// PERF-PG-001  Login page + its JS and CSS, fetched together like a browser does
// PERF-PG-002  All the other HTML pages
// PERF-PG-003  Swagger UI + the OpenAPI JSON
// PERF-PG-004  A page that does not exist: 404 must be fast
import http from "k6/http";
import { check, sleep } from "k6";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";
import { getPage, getPages } from "../lib/pages.js";
import { PROFILE } from "../lib/env.js";

// ---------- INIT ----------

const LOAD = JSON.parse(open("../config/load-profiles.json"));
const test = JSON.parse(open("../config/pages.json"))[PROFILE];
if (!test) throw new Error(`Unknown PROFILE "${PROFILE}". Use a test case id such as PG-001.`);

export const options = { stages: LOAD[test.load], thresholds: test.thresholds };

// In PG-004 every request is a 404, and that is the right answer
if (PROFILE === "PG-004") {
  http.setResponseCallback(http.expectedStatuses(404));
}

const contentType = (r) => r.headers["Content-Type"] || "";

// ---------- ONE FUNCTION PER TEST CASE ----------

// PERF-PG-001: the three files one visit to the login page needs
function loginPage() {
  const [html, js, css] = getPages(["/login.html", "/js/api.js", "/css/app.css"]);
  check(html, {
    "login.html is 200": (r) => r.status === 200,
    "login.html is HTML": (r) => contentType(r).includes("text/html"),
  });
  check(js, {
    "api.js is 200": (r) => r.status === 200,
    "api.js is JavaScript": (r) => contentType(r).includes("javascript"),
  });
  check(css, {
    "app.css is 200": (r) => r.status === 200,
    "app.css is CSS": (r) => contentType(r).includes("text/css"),
  });
}

// PERF-PG-002: one of the other pages each iteration, so every page gets its own p95
const PAGES = ["/dashboard.html", "/my-tickets.html", "/ticket.html", "/new-ticket.html", "/users.html"];
function htmlPages() {
  const path = PAGES[__ITER % PAGES.length];
  check(getPage(path), {
    "page is 200": (r) => r.status === 200,
    "page is HTML": (r) => contentType(r).includes("text/html"),
  });
}

// PERF-PG-003: Swagger UI (redirects to /swagger-ui/index.html; k6 follows it) and the generated spec
function swagger() {
  check(getPage("/swagger-ui.html"), { "swagger ui is 200": (r) => r.status === 200 });
  check(getPage("/v3/api-docs"), {
    "api-docs is 200": (r) => r.status === 200,
    "api-docs has paths": (r) => r.json("paths") !== undefined,
  });
}

// PERF-PG-004: a file that is not there
function notFound() {
  check(getPage("/does-not-exist.html"), { "status is 404": (r) => r.status === 404 });
}

const TESTS = {
  "PG-001": loginPage,
  "PG-002": htmlPages,
  "PG-003": swagger,
  "PG-004": notFound,
};

// ---------- VU CODE ----------
export default function () {
  TESTS[PROFILE]();
  sleep(1);
}

// ---------- END ----------
export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: " ", enableColors: true }),
    [`results/pages-${PROFILE}.json`]: JSON.stringify(data, null, 2),
  };
}
