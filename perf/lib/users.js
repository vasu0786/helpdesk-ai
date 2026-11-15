// Only file that knows the user endpoints (admin): GET/POST /users, PATCH /users/{id}
import http from "k6/http";
import { BASE_URL } from "./env.js";

export function listUsers(token) {}

export function createUser(token, body) {}

export function updateUser(token, id, body) {}
