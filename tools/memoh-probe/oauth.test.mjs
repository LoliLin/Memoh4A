import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { test } from 'node:test';
import { runOAuthProbe } from './oauth.mjs';

const PRIVATE = 'fixture-oauth-secret-value';

test('OAuth start checks callback variants over HTTP without following or exposing credentials', async () => {
  const seen = [];
  const server = createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    seen.push({ method: req.method, cookie: req.headers.cookie, authorization: req.headers.authorization });
    assert.equal(url.pathname, '/api/v1/auth/oauth/google/login');
    const target = url.searchParams.get('return_to');
    if (target && !target.startsWith(origin)) {
      res.writeHead(400, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ detail: PRIVATE }));
      return;
    }
    res.writeHead(302, {
      location: `https://accounts.google.com/o/oauth2/v2/auth?state=${PRIVATE}&code_challenge=${PRIVATE}&code_challenge_method=S256`,
      'set-cookie': `pending=${PRIVATE}; Path=/; HttpOnly`,
    });
    res.end();
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  try {
    const report = await runOAuthProbe({ origin, fetchImpl: (url, options) => {
      assert.equal(options.redirect, 'manual');
      assert.equal(options.credentials, 'omit');
      return fetch(url, options);
    } });
    assert.equal(report.authenticationCompleted, false);
    assert.equal(report.scope, 'public_oauth_start_only');
    assert.deepEqual(report.requests.map(row => row.outcome), [
      'starts_google_flow', 'starts_google_flow', 'starts_google_flow',
      'request_rejected', 'request_rejected', 'request_rejected',
    ]);
    assert.equal(report.requests[0].statePresent, true);
    assert.equal(report.requests[0].pkce, 'S256');
    assert.equal(report.requests[0].setsCookie, true);
    assert.equal(seen.length, 6);
    assert.ok(seen.every(row => row.method === 'GET' && row.cookie === undefined && row.authorization === undefined));
    assert.ok(!JSON.stringify(report).includes(PRIVATE));
    assert.ok(!JSON.stringify(report).includes('accounts.google.com/'));
  } finally {
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
  }
});

test('OAuth probe sanitizes transport failures and never claims authentication completed', async () => {
  const report = await runOAuthProbe({ fetchImpl: async () => { throw new Error(PRIVATE); } });
  assert.equal(report.authenticationCompleted, false);
  assert.ok(report.requests.every(row => row.outcome === 'network_error'));
  assert.ok(!JSON.stringify(report).includes(PRIVATE));
});

test('OAuth probe refuses unrelated remote origins before sending a request', async () => {
  let calls = 0;
  await assert.rejects(runOAuthProbe({ origin: 'https://unrelated.example', fetchImpl: () => { calls++; } }));
  assert.equal(calls, 0);
});
