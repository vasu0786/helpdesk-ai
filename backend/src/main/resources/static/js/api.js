// Shared helpers for every page: token storage, API calls, header, formatting.
const Api = (() => {
  const TOKEN_KEY = "helpdesk.token";
  const USER_KEY = "helpdesk.user";

  function store(key, value) {
    try { value == null ? localStorage.removeItem(key) : localStorage.setItem(key, value); } catch (e) { /* ignore */ }
  }
  function load(key) {
    try { return localStorage.getItem(key); } catch (e) { return null; }
  }

  function token() { return load(TOKEN_KEY); }
  function user() { try { return JSON.parse(load(USER_KEY)); } catch (e) { return null; } }
  function saveSession(res) { store(TOKEN_KEY, res.token); store(USER_KEY, JSON.stringify(res.user)); }
  function logout() { store(TOKEN_KEY, null); store(USER_KEY, null); location.href = "/login.html"; }

  /** Error thrown for non-2xx responses. body is the ErrorResponse JSON from the backend. */
  class ApiError extends Error {
    constructor(status, body) {
      super((body && body.message) || ("Request failed (" + status + ")"));
      this.status = status;
      this.body = body || {};
    }
  }

  async function request(method, path, body) {
    const headers = { "Accept": "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (token()) headers["Authorization"] = "Bearer " + token();
    const res = await fetch("/api" + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    if (res.status === 401 && token() && !path.startsWith("/auth/")) { logout(); throw new ApiError(401, {}); }
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) throw new ApiError(res.status, data);
    return data;
  }

  /** Call at the top of a page. Redirects to login if not signed in or the role isn't allowed. */
  function requireRole(...roles) {
    const u = user();
    if (!token() || !u) { location.href = "/login.html?next=" + encodeURIComponent(location.pathname + location.search); return null; }
    if (roles.length && !roles.includes(u.role)) { location.href = homeFor(u); return null; }
    renderHeader(u);
    return u;
  }

  function homeFor(u) {
    if (!u) return "/login.html";
    if (u.role === "ADMIN" || u.role === "AGENT") return "/dashboard.html";
    return "/my-tickets.html";
  }

  function isStaff(u) { return u && (u.role === "AGENT" || u.role === "ADMIN"); }

  function renderHeader(u) {
    const el = document.getElementById("app-header");
    if (!el) return;
    const links = [];
    if (isStaff(u)) links.push(["/dashboard.html", "Dashboard", "nav-dashboard"]);
    if (u.role === "CUSTOMER") links.push(["/my-tickets.html", "My tickets", "nav-my-tickets"]);
    links.push(["/new-ticket.html", "New ticket", "nav-new-ticket"]);
    if (u.role === "ADMIN") links.push(["/users.html", "Users", "nav-users"]);
    links.push(["/swagger-ui.html", "API docs", "nav-api-docs"]);
    el.className = "topbar";
    el.innerHTML =
      '<a class="brand" href="' + homeFor(u) + '" data-testid="nav-home">HelpDesk AI</a>' +
      "<nav>" + links.map(([href, text, id]) => '<a href="' + href + '" data-testid="' + id + '">' + text + "</a>").join("") + "</nav>" +
      '<span class="who" data-testid="nav-user">' + esc(u.fullName) + " · " + esc(u.role) + "</span>" +
      '<button class="secondary" data-testid="logout-button" id="logout-button">Log out</button>';
    document.getElementById("logout-button").addEventListener("click", logout);
  }

  // ---------- formatting ----------

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }
  function label(v) { return v == null ? "—" : String(v).replace(/_/g, " ").toLowerCase().replace(/^\w/, c => c.toUpperCase()); }
  function badge(v, testid) {
    if (v == null) return '<span class="muted">—</span>';
    return '<span class="badge ' + esc(v) + '"' + (testid ? ' data-testid="' + testid + '"' : "") + ">" + esc(label(v)) + "</span>";
  }
  function dateTime(iso) { return iso ? new Date(iso).toLocaleString() : "—"; }

  /** "2h 10m left", "overdue by 5m", or "Breached" for a ticket's SLA block. */
  function slaText(ticket) {
    const done = ticket.status === "RESOLVED" || ticket.status === "CLOSED";
    if (ticket.sla.breached) return { text: "Breached", cls: "breached" };
    if (done) return { text: "Met", cls: "DONE" };
    const ms = new Date(ticket.sla.dueAt) - Date.now();
    const abs = Math.abs(ms);
    const h = Math.floor(abs / 3600000), m = Math.floor((abs % 3600000) / 60000);
    const span = (h ? h + "h " : "") + m + "m";
    return ms >= 0 ? { text: span + " left", cls: ms < 3600000 ? "HIGH" : "" } : { text: "Overdue " + span, cls: "breached" };
  }
  function slaBadge(ticket, testid) {
    const s = slaText(ticket);
    return '<span class="badge ' + s.cls + '" data-testid="' + (testid || "sla") + '">' + esc(s.text) + "</span>";
  }

  function showError(el, err) {
    el.textContent = err.message;
    el.classList.remove("hidden");
  }

  /** Puts per-field validation messages from a 400 response under the matching inputs. */
  function showFieldErrors(form, err) {
    form.querySelectorAll(".field-error").forEach(e => e.remove());
    const fields = (err.body && err.body.fieldErrors) || {};
    Object.entries(fields).forEach(([name, msg]) => {
      const input = form.querySelector('[name="' + name + '"]');
      if (!input) return;
      const div = document.createElement("div");
      div.className = "field-error";
      div.dataset.testid = "error-" + name;
      div.textContent = msg;
      input.after(div);
    });
  }

  const STATUS_FLOW = {
    OPEN: ["IN_PROGRESS", "CLOSED"],
    IN_PROGRESS: ["WAITING_ON_CUSTOMER", "RESOLVED"],
    WAITING_ON_CUSTOMER: ["IN_PROGRESS", "RESOLVED"],
    RESOLVED: ["IN_PROGRESS", "CLOSED"],
    CLOSED: []
  };
  const STATUSES = Object.keys(STATUS_FLOW);
  const PRIORITIES = ["LOW", "MEDIUM", "HIGH", "URGENT"];
  const CATEGORIES = ["BILLING", "BUG", "FEATURE_REQUEST", "ACCOUNT", "OTHER"];

  return {
    get: p => request("GET", p), post: (p, b) => request("POST", p, b ?? {}), patch: (p, b) => request("PATCH", p, b),
    del: p => request("DELETE", p), token, user, saveSession, logout, requireRole, homeFor, isStaff,
    esc, label, badge, dateTime, slaBadge, showError, showFieldErrors, ApiError,
    STATUS_FLOW, STATUSES, PRIORITIES, CATEGORIES
  };
})();
