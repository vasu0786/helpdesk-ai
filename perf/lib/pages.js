// Static pages and assets served by Spring: /login.html, /dashboard.html, /js/api.js, /css/...
import http from "k6/http";
import { BASE_URL } from "./env.js";

// Pages live at the site root, not under /api, so drop the /api part of BASE_URL
export const ROOT = BASE_URL.replace(/\/api\/?$/, "");

// GET one file. The path itself is the name tag, so every file gets its own p95.
export function getPage(path) {
  return http.get(`${ROOT}${path}`, { tags: { name: `GET ${path}` } });
}

// GET several files at the same time, the way a browser loads a page and its assets.
// Returns the responses in the same order as the paths.
export function getPages(paths) {
  return http.batch(paths.map((path) => ["GET", `${ROOT}${path}`, null, { tags: { name: `GET ${path}` } }]));
}
