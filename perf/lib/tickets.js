// Only file that knows the ticket endpoints:
// POST/GET /tickets, GET /tickets/stats, GET/PATCH/DELETE /tickets/{id}, GET/POST /tickets/{id}/comments
import http from "k6/http";
import { BASE_URL } from "./env.js";

// Headers and tags shared by every ticket request.
// "name" groups requests in the results: GET /tickets/123, /tickets/456 ... all count as "GET /tickets/{id}".
// extraTags is optional, e.g. { kind: "deep" }, to tell request types apart in the results.
function params(token, name, extraTags = {}) {
    return {
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        tags: { name, ...extraTags },
    };
}

// POST /api/tickets with {subject, description, category, customerEmail?}
export function createTicket(token, body) {
    return http.post(`${BASE_URL}/tickets`, JSON.stringify(body), params(token, "POST /tickets"));
}

// GET /api/tickets?status=OPEN&page=0 ...  query is an object like { status: "OPEN", size: 20 }
export function listTickets(token, query = {}, extraTags = {}) {
    const qs = Object.entries(query).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join("&");
    return http.get(`${BASE_URL}/tickets?${qs}`, params(token, "GET /tickets", extraTags));
}

// GET /api/tickets/stats (agents and admins only)
export function ticketStats(token) {
    return http.get(`${BASE_URL}/tickets/stats`, params(token, "GET /tickets/stats"));
}

// GET /api/tickets/{id}
export function getTicket(token, id) {
    return http.get(`${BASE_URL}/tickets/${id}`, params(token, "GET /tickets/{id}"));
}

// PATCH /api/tickets/{id} with only the fields to change, e.g. { status: "IN_PROGRESS" }
export function updateTicket(token, id, body) {
    return http.patch(`${BASE_URL}/tickets/${id}`, JSON.stringify(body), params(token, "PATCH /tickets/{id}"));
}

// DELETE /api/tickets/{id} (admin only). The DB deletes its comments too (ON DELETE CASCADE).
export function deleteTicket(token, id) {
    return http.del(`${BASE_URL}/tickets/${id}`, null, params(token, "DELETE /tickets/{id}"));
}

// GET /api/tickets/{id}/comments
export function listComments(token, id) {
    return http.get(`${BASE_URL}/tickets/${id}/comments`, params(token, "GET /tickets/{id}/comments"));
}

// POST /api/tickets/{id}/comments with {body, internal}
export function addComment(token, id, body) {
    return http.post(`${BASE_URL}/tickets/${id}/comments`, JSON.stringify(body),
        params(token, "POST /tickets/{id}/comments"));
}
