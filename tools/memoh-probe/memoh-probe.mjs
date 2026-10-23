#!/usr/bin/env node
/**
 * Memoh API probe — local, read-only, uses YOUR browser session.
 *
 * It answers the questions the Android client cannot answer without a live session:
 *   1. Where does the team list come from, and what does a team object look like?
 *   2. Does /api/memoh need X-Team-ID, team_id, or neither?
 *   3. Does /api/v1/ws-tickets work, and what does the ticket look like?
 *
 * Nothing is written to disk and nothing is mutated: every request is GET or the
 * ticket POST (which only mints a short-lived socket credential).
 *
 * HOW TO RUN
 *   1. Open https://app.memoh.net in Chrome while signed in.
 *   2. F12 -> Network -> any request -> right-click -> Copy -> Copy as cURL.
 *      Paste it into `curl.txt` next to this script.
 *   3. node memoh-probe.mjs
 *
 * You can also skip cURL entirely:
 *      node memoh-probe.mjs --cookie "name=value; name2=value2"
 */

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const ORIGIN = 'https://app.memoh.net';
const AUTH = `${ORIGIN}/api/v1`;
const API = `${ORIGIN}/api/memoh`;

function parseCurl(text) {
  const cookie = /(?:-H|--header)\s+['"]cookie:\s*([^'"]+)['"]/i.exec(text)?.[1]
    ?? /(?:-b|--cookie)\s+['"]([^'"]+)['"]/i.exec(text)?.[1]
    ?? '';
  const auth = /(?:-H|--header)\s+['"]authorization:\s*([^'"]+)['"]/i.exec(text)?.[1] ?? '';
  const team = /(?:-H|--header)\s+['"]x-team-i[ds]:\s*([^'"]+)['"]/i.exec(text)?.[1] ?? '';
  return { cookie: cookie.trim(), authorization: auth.trim(), team: team.trim() };
}

function loadSession() {
  const argCookie = process.argv.find((a, i) => i > 1 && process.argv[i - 1] === '--cookie');
  if (argCookie) return { cookie: argCookie, authorization: '', team: '' };
  for (const name of ['curl.txt', 'curl.sh', 'curl.bat']) {
    try {
      const parsed = parseCurl(readFileSync(join(HERE, name), 'utf8'));
      if (parsed.cookie || parsed.authorization) return parsed;
    } catch { /* try the next one */ }
  }
  return null;
}

const session = loadSession();
if (!session) {
  console.error([
    'No session found.',
    '',
    'Put a "Copy as cURL" of any app.memoh.net request into curl.txt next to this script,',
    'or pass --cookie "name=value".',
  ].join('\n'));
  process.exit(2);
}

const baseHeaders = {
  accept: 'application/json',
  ...(session.cookie ? { cookie: session.cookie } : {}),
  ...(session.authorization ? { authorization: session.authorization } : {}),
};

async function call(method, url, { headers = {}, body } = {}) {
  const res = await fetch(url, {
    method,
    headers: { ...baseHeaders, ...headers, ...(body ? { 'content-type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
    redirect: 'manual',
  });
  const text = await res.text().catch(() => '');
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* plain text body */ }
  return { status: res.status, json, text, setCookie: res.headers.getSetCookie?.() ?? [] };
}

const results = [];
function record(name, outcome) {
  results.push({ name, ...outcome });
}

function short(value, max = 600) {
  const text = typeof value === 'string' ? value : JSON.stringify(value);
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

/** 1. Identify the caller and discover the team list. */
async function probeIdentity() {
  const candidates = [
    ['GET', `${AUTH}/users/me`, 'auth users/me'],
    ['GET', `${AUTH}/teams`, 'auth teams'],
    ['GET', `${API}/teams`, 'business teams'],
    ['GET', `${AUTH}/platform/profile`, 'auth platform profile'],
  ];
  for (const [method, url, label] of candidates) {
    const outcome = await call(method, url);
    record(label, outcome);
    const keys = outcome.json && typeof outcome.json === 'object' ? Object.keys(outcome.json) : [];
    console.log(`\n[${outcome.status}] ${label}  ${url}`);
    if (keys.length) console.log(`  keys: ${keys.join(', ')}`);
    console.log(`  ${short(outcome.json ?? outcome.text)}`);
  }
}

/** 2. Pull team ids out of whatever the previous step found. */
function discoverTeams() {
  const fromAuth = results.find((r) => r.name === 'auth teams')?.json;
  const fromBusiness = results.find((r) => r.name === 'business teams')?.json;
  const container = fromAuth ?? fromBusiness;
  const list = Array.isArray(container) ? container
    : Array.isArray(container?.teams) ? container.teams
    : Array.isArray(container?.items) ? container.items
    : [];
  return list.filter((t) => t && typeof t === 'object');
}

/** 3. Does the business API want X-Team-ID, team_id, or nothing? */
async function probeTeamScoping(teams) {
  const first = teams[0]?.team_id ?? teams[0]?.id ?? '';
  const variants = [
    ['no team header', {}],
    ['X-Team-ID header', { 'x-team-id': first }],
    ['X-Team-Id header', { 'x-team-id': first }],
  ];
  for (const [label, headers] of variants) {
    if (label.includes('Team') && !first) continue;
    const outcome = await call('GET', `${API}/bots`, { headers });
    record(`bots ${label}`, outcome);
    const count = Array.isArray(outcome.json?.items) ? outcome.json.items.length
      : Array.isArray(outcome.json) ? outcome.json.length : '?';
    console.log(`\n[${outcome.status}] GET /api/memoh/bots — ${label}  (items: ${count})`);
    if (outcome.status >= 400) console.log(`  ${short(outcome.json ?? outcome.text, 300)}`);
  }
  if (first) {
    const byQuery = await call('GET', `${API}/bots?team_id=${encodeURIComponent(first)}`);
    record('bots team_id query', byQuery);
    console.log(`\n[${byQuery.status}] GET /api/memoh/bots?team_id=…  (items: ${
      Array.isArray(byQuery.json?.items) ? byQuery.json.items.length : '?'})`);
  }
}

/** 4. Socket credentials. */
async function probeTicket() {
  const outcome = await call('POST', `${AUTH}/ws-tickets`, { body: {} });
  record('ws ticket', outcome);
  const ticket = outcome.json?.ticket ?? '';
  console.log(`\n[${outcome.status}] POST /api/v1/ws-tickets  ticket length: ${ticket.length}`);
  if (outcome.status >= 400) console.log(`  ${short(outcome.json ?? outcome.text, 300)}`);
}

;(async () => {
  await probeIdentity();
  const found = discoverTeams();
  console.log(`\n=== teams discovered: ${found.length}`);
  for (const team of found.slice(0, 5)) {
    console.log(`  ${team.team_id ?? team.id ?? '?'}  ${team.name ?? team.display_name ?? ''}  ${team.role ?? ''}`);
  }
  await probeTeamScoping(found);
  await probeTicket();

  const failed = results.filter((r) => r.status >= 400);
  console.log(`\n=== summary: ${results.length} requests, ${failed.length} non-2xx`);
  for (const failure of failed) {
    console.log(`  ${failure.status}  ${failure.name}`);
  }
  console.log('\nCopy this whole output back to Alma and she will wire the client to match.');
})();
