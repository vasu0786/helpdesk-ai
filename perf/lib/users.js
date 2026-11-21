// Only file that knows the user endpoints (admin): GET/POST /users, PATCH /users/{id}
import http from "k6/http";
import { BASE_URL } from "./env.js";

// Same headers for every call; the name tag groups all calls of one endpoint in the results
function params(token, name) {
  return {
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
    tags: { name },
  };
}

// GET /api/users  (admin and agent; a customer gets 403)
export function listUsers(token) {
  return http.get(`${BASE_URL}/users`, params(token, "GET /users"));
}

// POST /api/users with {email, password, fullName, role}  (admin only)
export function createUser(token, body) {
  return http.post(`${BASE_URL}/users`, JSON.stringify(body), params(token, "POST /users"));
}

// PATCH /api/users/{id} with any of {fullName, role, active}  (admin only)
export function updateUser(token, id, body) {
  return http.patch(`${BASE_URL}/users/${id}`, JSON.stringify(body), params(token, "PATCH /users/{id}"));
}
