// One-off data setup: k6 run scripts/seed.js -e COUNT=200  (registers perf users, writes data/perf-users.json)
import http from "k6/http";
import { check } from "k6";
import { BASE_URL } from "../lib/env.js";
import { register } from "../lib/auth.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.1.0/index.js";



export const options = { vus: 1, iterations: 1 };
const COUNT = Number(__ENV.COUNT || 200);

export default function () {
    for(let i=0;i<COUNT;i++)
    {
        const resp = register(`perf-${i}@helpdesk.ai`,"Test@123",`Perf-${i}`);
        check(resp,
            {
                "user created or already exists":(resp)=>resp.status === 201 || resp.status === 409
            }
        );
    }
}

export function handleSummary(data) {
    // Rebuild the same list of accounts that default() registered
    const users = [];
    for (let i = 0; i < COUNT; i++) {
        users.push({ email: `perf-${i}@helpdesk.ai`, password: "Test@123" });
    }

    return {
        // Print the normal summary so you can see the checks passed
        stdout: textSummary(data, { indent: " ", enableColors: true }),
        // Path is relative to where you run k6 (perf/)
        "data/perf-users.json": JSON.stringify(users, null, 2),
    };
}

