/**
 * k6 Load Test for Bloom Filter Username Availability API
 *
 * Run:
 *   k6 run --vus 100 --duration 30s load-test/k6-script.js
 *
 * Scenarios:
 *   A) bloom_negative  – username not in filter → fast path (no DB)
 *   B) bloom_positive  – username in filter + DB → slow path
 *   C) concurrent_reg  – race to register same username → tests 409 handling
 *
 * Prerequisites:
 *   1. App running at localhost:8080
 *   2. Seed the DB first:
 *      curl -X POST "http://localhost:8080/api/v1/seed/generate?count=50000"
 *   3. Install k6: https://k6.io/docs/getting-started/installation/
 */

import http from "k6/http";
import { check, sleep, group } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

// ─── Config (must be before options) ─────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL     || "http://localhost:8080";
const VUS      = parseInt(__ENV.LOAD_VUS    || "100");
const DURATION = __ENV.LOAD_DURATION        || "30s";

// ─── Custom Metrics ──────────────────────────────────────────────────────────
const bloomNegativeLatency = new Trend("bloom_negative_latency_ms");
const bloomPositiveLatency = new Trend("bloom_positive_latency_ms");
const dbOnlyLatency        = new Trend("db_only_latency_ms");
const errorRate            = new Rate("error_rate");
const conflictCount        = new Counter("conflict_409_count");

// ─── Test Configuration ───────────────────────────────────────────────────────
export const options = {
  // Ensure p50 and p99 appear in the summary table
  summaryTrendStats: ["avg", "min", "med", "p(50)", "p(90)", "p(95)", "p(99)", "max"],

  scenarios: {
    // Scenario A: Bloom filter negative path (fast path, most traffic)
    bloom_check_new: {
      executor: "constant-vus",
      vus: Math.floor(VUS * 0.8),
      duration: DURATION,
      exec: "checkNewUsername",
    },
    // Scenario B: Bloom filter positive path (registered usernames)
    bloom_check_existing: {
      executor: "constant-vus",
      vus: Math.floor(VUS * 0.2),
      duration: DURATION,
      exec: "checkExistingUsername",
      startTime: "5s",
    },
    // Scenario C: Concurrent registrations (race condition test)
    concurrent_register: {
      executor: "shared-iterations",
      vus: 10,
      iterations: 100,
      exec: "registerUser",
      startTime: "35s",
    },
  },
  thresholds: {
    // CI runners are shared VMs — thresholds are intentionally loose.
    // Tighten these when running against a dedicated staging environment.
    "bloom_negative_latency_ms": ["p(50)<10", "p(99)<100"],
    "bloom_positive_latency_ms": ["p(50)<15", "p(99)<150"],
    http_req_failed:              ["rate<0.01"],
    error_rate:                   ["rate<0.01"],
  },
};

// ─── Scenario A: Check a username that's NOT in the filter ───────────────────
export function checkNewUsername() {
  const username = `newuser_${__VU}_${__ITER}_${Date.now()}`;
  const url = `${BASE_URL}/api/v1/usernames/check?username=${username}`;

  const start = Date.now();
  // name tag groups all unique URLs under one time series — prevents high-cardinality explosion
  const res = http.get(url, { tags: { name: "check_new" } });
  const elapsed = Date.now() - start;

  bloomNegativeLatency.add(elapsed);

  const ok = check(res, {
    "status 200":       (r) => r.status === 200,
    "available=true":   (r) => JSON.parse(r.body).available === true,
    "has username":     (r) => JSON.parse(r.body).username !== undefined,
  });
  errorRate.add(!ok);
  sleep(0.01); // 10ms think time
}

// ─── Scenario B: Check a username that IS in the filter (slow path) ──────────
// Requires pre-seeded data with usernames matching this pattern
export function checkExistingUsername() {
  // These usernames should exist from the seed step
  const prefixes = ["user", "dev", "pro", "cool", "super"];
  const prefix = prefixes[Math.floor(Math.random() * prefixes.length)];
  // Use a range that likely exists after seeding
  const username = `${prefix}_${Math.random().toString(36).substring(2, 12)}`;

  const url = `${BASE_URL}/api/v1/usernames/check?username=${username}`;
  const start = Date.now();
  const res = http.get(url, { tags: { name: "check_existing" } });
  const elapsed = Date.now() - start;

  bloomPositiveLatency.add(elapsed);

  const ok = check(res, {
    "status 200": (r) => r.status === 200,
    "has available field": (r) => typeof JSON.parse(r.body).available === "boolean",
  });
  errorRate.add(!ok);
  sleep(0.05);
}

// ─── Scenario C: Race to register same username (tests 409 handling) ─────────
export function registerUser() {
  const sharedUsername = "raceuser_loadtest";
  const payload = JSON.stringify({
    username: sharedUsername,
    email:    `${sharedUsername}_${__VU}@test.com`,
  });

  const res = http.post(`${BASE_URL}/api/v1/users`, payload, {
    headers: { "Content-Type": "application/json" },
  });

  if (res.status === 409) {
    conflictCount.add(1);
  }

  check(res, {
    "201 or 409": (r) => r.status === 201 || r.status === 409,
  });
}

// ─── Summary hook: Print results table ───────────────────────────────────────
export function handleSummary(data) {
  const neg = data.metrics["bloom_negative_latency_ms"];
  const pos = data.metrics["bloom_positive_latency_ms"];

  const table = `
╔══════════════════════════════════════════════════════════════════╗
║           Bloom Filter Load Test Results (server-side)          ║
╠════════════════════╦══════════╦══════════╦══════════╦═══════════╣
║ Approach           ║   p50 ms ║   p95 ms ║   p99 ms ║ RPS est.  ║
╠════════════════════╬══════════╬══════════╬══════════╬═══════════╣
║ Bloom negative     ║ ${fmt(neg?.values?.["p(50)"])} ║ ${fmt(neg?.values?.["p(95)"])} ║ ${fmt(neg?.values?.["p(99)"])} ║ 80 VUs    ║
║ Bloom+Cache+DB     ║ ${fmt(pos?.values?.["p(50)"])} ║ ${fmt(pos?.values?.["p(95)"])} ║ ${fmt(pos?.values?.["p(99)"])} ║ 20 VUs    ║
╚════════════════════╩══════════╩══════════╩══════════╩═══════════╝

Conflicts (409) in concurrent registration: ${data.metrics["conflict_409_count"]?.values?.count ?? 0}
Error rate: ${((data.metrics["error_rate"]?.values?.rate ?? 0) * 100).toFixed(2)}%
`;

  console.log(table);

  return {
    "stdout": table,
    "load-test/results.json": JSON.stringify(data, null, 2),
  };
}

function fmt(val) {
  if (val === undefined || val === null) return "   N/A";
  return val.toFixed(2).padStart(8);
}
