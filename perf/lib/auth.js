// Only file that knows the auth endpoints: POST /auth/login, POST /auth/register, GET /auth/me
import http from "k6/http";
import { BASE_URL } from "./env.js";

// POST /api/auth/login with {email, password}.
// Returns the whole k6 response, so the scenario can check status, body and timing itself.
// extraTags is optional, e.g. { kind: "wrong" }, to tell request types apart in the results.
export function login(email, password, extraTags = {}) {
  const body = JSON.stringify({ email, password });
  const params = {
    headers: { "Content-Type": "application/json" },
    // Every login request is reported under this one name in the results
    tags: { name: "POST /auth/login", ...extraTags },
  };
  return http.post(`${BASE_URL}/auth/login`, body, params);
}

// POST /api/auth/register with {email, password, fullName}.
// Returns the whole k6 response, so the scenario can check status, body and timing itself.
export function register(email, password, fullName) {
  const body = JSON.stringify({ email, password, fullName });
  const params = {
    headers: { "Content-Type": "application/json" },
    // Every register request is reported under this one name in the results
    tags: { name: "POST /auth/register" },
  };
  return http.post(`${BASE_URL}/auth/register`, body, params);
}

// GET /api/auth/me with the token from login.
// Returns the whole k6 response, so the scenario can check status and body itself.
export function me(token) {
  const params = {
    headers: { Authorization: `Bearer ${token}` },
    // Every /me request is reported under this one name in the results
    tags: { name: "GET /auth/me" },
  };
  return http.get(`${BASE_URL}/auth/me`, params);
}
