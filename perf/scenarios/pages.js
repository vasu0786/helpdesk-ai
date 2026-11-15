// Entry point: k6 run -e PROFILE=load scenarios/pages.js  (static HTML, JS and CSS served by Spring)
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";
import { getPage } from "../lib/pages.js";
import { PROFILE } from "../lib/env.js";

export const options = {};

export default function () {}

export function handleSummary(data) {}
