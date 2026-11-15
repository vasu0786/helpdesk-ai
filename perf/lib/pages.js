// Static pages and assets served by Spring: /login.html, /dashboard.html, /js/api.js, /css/...
import http from "k6/http";
import { BASE_URL } from "./env.js";

export function getPage(path) {}
