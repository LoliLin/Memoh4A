import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { test } from 'node:test';
import vm from 'node:vm';
import { runMemohProbe, browserSnippet } from './protocol.mjs';
import { parseSessionCapture } from './memoh-probe.mjs';

const COOKIE = 'session=fixture-cookie-private';
const AUTH = 'Bearer fixture-token-private';
const TEAM = 'fixture-team-private';
const TICKET = 'fixture-ticket-private';
const EMAIL = 'fixture-email-private@example.test';

async function withServer(options, body) {
  const calls = [];
  const server = createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const path = url.pathname;
    const scopedTeam = req.headers['x-team-id'] ?? url.searchParams.get('team_id');
    calls.push({ path, method: req.method, team: scopedTeam, header: req.headers['x-team-id'] });
    res.setHeader('content-type', 'application/json');
    const send = (status, value) => { res.writeHead(status); res.end(JSON.stringify(value)); };
    if (path === '/api/v1/auth/config') return send(200, { login_method: 'email' });
    if (req.headers.cookie !== COOKIE || options.unauthenticated) {
      return send(401, { detail: 'not authenticated', token: AUTH });
    }
    if (path === '/api/v1/users/me') return send(200, { user: { user_id: 'fixture-user', email: EMAIL } });
    if (path === '/api/v1/teams') {
      if (options.teamFailure) return send(503, { detail: `server message containing ${COOKIE}` });
      return send(200, { teams: options.empty ? [] : [
        { team: { team_id: TEAM, name: EMAIL }, role: 'TEAM_ROLE_OWNER' },
        { team: { team_id: 'fixture-team-2', name: 'Second' }, role: 'TEAM_ROLE_MEMBER' },
        { team: { team_id: TEAM }, role: 'TEAM_ROLE_OWNER' },
        { team: { team_id: '' }, role: 'TEAM_ROLE_OWNER' },
      ] });
    }
    if (path === '/api/memoh/bots' || path === '/api/v1/ws-tickets') {
      if (!scopedTeam) return send(400, { detail: 'X-Team-ID header or team_id query is required' });
      if (![TEAM, 'fixture-team-2'].includes(scopedTeam)) return send(403, { detail: `denied ${AUTH}` });
      if (path.endsWith('ws-tickets')) return send(200, { ticket: TICKET });
      return send(200, { items: [{ id: 'fixture-bot', api_key: AUTH }] });
    }
    send(404, { detail: 'route not found' });
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  try { await body({ origin, calls }); }
  finally {
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
  }
}

function assertNoPrivateValues(report) {
  const output = JSON.stringify(report);
  for (const secret of [COOKIE, AUTH, TEAM, TICKET, EMAIL]) assert.ok(!output.includes(secret));
}

test('real nested memberships resolve the missing-team error for header, query and ticket', async () => {
  await withServer({}, async ({ origin, calls }) => {
    const report = await runMemohProbe({ origin, headers: { Cookie: COOKIE, 'X-Team-ID': 'stale-team' }, ticket: true });
    assert.equal(report.result, 'scoped_business_ok');
    assert.equal(report.missingTeamReproduced, true);
    assert.equal(report.queryVariantSucceeded, true);
    assert.equal(report.teams.length, 2);
    assert.equal(report.requests.filter(row => row.error === 'missing_team').length, 1);
    assert.equal(report.requests.find(row => row.label === 'bots with X-Team-ID').status, 200);
    assert.equal(report.requests.find(row => row.label.startsWith('scoped socket ticket')).ticketPresent, true);
    assert.equal(calls.find(call => call.path.endsWith('/users/me')).team, null);
    assert.equal(calls.find(call => call.path.endsWith('/teams')).team, null);
    const bots = calls.filter(call => call.path.endsWith('/bots'));
    assert.deepEqual(bots.map(call => call.team), [null, TEAM, TEAM]);
    assert.equal(bots[2].header, undefined);
    assert.equal(calls.find(call => call.path.endsWith('ws-tickets')).header, TEAM);
    assertNoPrivateValues(report);
  });
});

test('valid saved team is reused; GET-only default never mints a ticket', async () => {
  await withServer({}, async ({ origin, calls }) => {
    const report = await runMemohProbe({ origin, headers: { Cookie: COOKIE }, preferredTeam: 'fixture-team-2' });
    assert.equal(report.selection, 'saved_membership');
    assert.equal(report.selectedTeam, 'team-2');
    assert.equal(calls.filter(call => call.method !== 'GET').length, 0);
    assert.equal(calls.findLast(call => call.header).header, 'fixture-team-2');
    assertNoPrivateValues(report);
  });
});

test('401 stops before team/business requests and makes no route-existence claim', async () => {
  await withServer({ unauthenticated: true }, async ({ origin, calls }) => {
    const report = await runMemohProbe({ origin });
    assert.equal(report.result, 'not_authenticated');
    assert.equal(calls.length, 2);
    assert.deepEqual(calls.map(call => call.path), ['/api/v1/auth/config', '/api/v1/users/me']);
    assertNoPrivateValues(report);
  });
});

for (const options of [{ empty: true }, { teamFailure: true }]) {
  test(`team bootstrap ${Object.keys(options)[0]} prevents business calls`, async () => {
    await withServer(options, async ({ origin, calls }) => {
      const report = await runMemohProbe({ origin, headers: { Cookie: COOKIE }, ticket: true });
      assert.equal(report.result, options.empty ? 'no_teams' : 'teams_failed');
      assert.ok(calls.every(call => call.method === 'GET' && !call.path.includes('/api/memoh/')));
      assertNoPrivateValues(report);
    });
  });
}

test('browser snippet runs the same protocol with page cookies and emits only metadata', async () => {
  await withServer({}, async ({ origin }) => {
    const printed = [];
    const context = vm.createContext({
      URL, Headers, AbortSignal,
      location: { origin },
      localStorage: { getItem: () => TEAM },
      fetch: (url, options) => {
        assert.equal(options.credentials, 'include');
        const headers = new Headers(options.headers);
        headers.set('cookie', COOKIE);
        return fetch(url, { ...options, headers });
      },
      console: { log: text => printed.push(text) },
    });
    const report = await vm.runInContext(browserSnippet(), context);
    assert.equal(report.result, 'scoped_business_ok');
    assert.equal(report.selection, 'saved_membership');
    assertNoPrivateValues(report);
    assertNoPrivateValues(printed);
  });
});

test('cURL parser reads bash and cmd captures without running their shell code', () => {
  for (const capture of [
    `curl 'https://app.memoh.net/api/memoh/bots' -H 'Cookie: ${COOKIE}' -H 'Authorization: ${AUTH}' -H 'X-Team-ID: ${TEAM}'`,
    `curl ^"https://app.memoh.net/api/memoh/bots^" ^\r\n -b ^"${COOKIE}^" -H ^"X-Team-Id: ${TEAM}^"`,
  ]) {
    const parsed = parseSessionCapture(capture);
    assert.equal(parsed.headers.Cookie, COOKIE);
    assert.equal(parsed.preferredTeam, TEAM);
  }
  assert.throws(() => parseSessionCapture(`curl 'https://unrelated.example/' -b '${COOKIE}'`));
});

test('credentials are never forwarded to arbitrary remote origins', async () => {
  let attempted = false;
  await assert.rejects(runMemohProbe({
    origin: 'https://unrelated.example', headers: { Cookie: COOKIE },
    fetchImpl: () => { attempted = true; },
  }));
  assert.equal(attempted, false);
});
