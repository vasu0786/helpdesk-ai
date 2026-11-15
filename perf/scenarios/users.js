// Entry point: k6 run -e PROFILE=smoke scenarios/users.js  (admin: list, create, activate/deactivate users)
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";
import { login } from "../lib/auth.js";
import { listUsers, createUser, updateUser } from "../lib/users.js";
import { PROFILE } from "../lib/env.js";

export const options = {};

export function setup() {}

export default function (data) {}

export function handleSummary(data) {}
