/** Same protocol in Node and the signed-in web page. Returns diagnostic metadata only. */
export async function runMemohProbe({
  origin = 'https://app.memoh.net', fetchImpl = globalThis.fetch,
  headers = {}, preferredTeam = '', ticket = false, timeoutMs = 12000,
} = {}) {
  const root = new URL(origin);
  const local = ['localhost', '127.0.0.1', '[::1]'].includes(root.hostname);
  if (!(root.origin === 'https://app.memoh.net' || (local && ['http:', 'https:'].includes(root.protocol))) ||
      root.username || root.password || root.search || root.hash || root.pathname !== '/') {
    throw new Error('Use the official Cloud origin or a loopback test server.');
  }
  const report = { version: 2, result: 'incomplete', teams: [], requests: [] };
  // A copied team header must not contaminate the unscoped discovery or negative control.
  const baseHeaders = new Headers(headers);
  baseHeaders.delete('x-team-id');
  const request = async (label, path, { method = 'GET', team = '', displayPath = path } = {}) => {
    const outgoing = new Headers(baseHeaders);
    outgoing.set('accept', 'application/json');
    if (team) outgoing.set('X-Team-ID', team);
    const row = { label, method, path: displayPath, status: null, shape: 'unknown' };
    report.requests.push(row);
    try {
      const response = await fetchImpl(new URL(path, root.origin), {
        method, headers: outgoing, credentials: 'include', redirect: 'manual',
        signal: AbortSignal.timeout(timeoutMs),
      });
      row.status = response.status;
      let body;
      try { body = await response.json(); } catch { row.shape = 'non_json'; }
      if (body !== undefined) row.shape = Array.isArray(body) ? 'array' :
        body && typeof body === 'object' ? 'object' : 'primitive';
      if (response.status >= 300) {
        const detail = typeof body?.detail === 'string' ? body.detail : '';
        row.error = response.status === 401 ? 'unauthenticated' :
          /(?:X-Team-ID|team_id).*required/i.test(detail) ? 'missing_team' :
          response.status >= 300 && response.status < 400 ? 'redirect' : 'http_error';
      }
      // Do not put raw response bodies, arbitrary server messages, or header values in the report.
      return { ok: response.ok && row.shape !== 'non_json', body, row };
    } catch (error) {
      row.error = ['TimeoutError', 'AbortError'].includes(error?.name) ? 'timeout' : 'network_error';
      return { ok: false, row };
    }
  };
  const config = await request('auth configuration', '/api/v1/auth/config');
  report.loginMethod = ['email', 'password'].includes(config.body?.login_method) ? config.body.login_method : 'unknown';
  const identity = await request('unscoped identity', '/api/v1/users/me');
  if (!identity.ok) {
    report.result = identity.row.status === 401 ? 'not_authenticated' : 'identity_failed';
    return report;
  }
  const membership = await request('unscoped team memberships', '/api/v1/teams');
  if (!membership.ok) { report.result = 'teams_failed'; return report; }
  if (!Array.isArray(membership.body?.teams)) { report.result = 'unexpected_team_shape'; return report; }
  const teams = membership.body.teams.flatMap(entry => {
    const id = entry?.team?.team_id;
    if (typeof id !== 'string' || !id.trim()) return [];
    return [{ id: id.trim(), role: entry.role }];
  }).filter((team, index, all) => all.findIndex(other => other.id === team.id) === index);
  const roles = new Set(['TEAM_ROLE_OWNER', 'TEAM_ROLE_ADMIN', 'TEAM_ROLE_MEMBER']);
  report.teams = teams.map((team, index) => ({
    label: `team-${index + 1}`, role: roles.has(team.role) ? team.role : 'other',
  }));
  if (!teams.length) { report.result = 'no_teams'; return report; }
  const selected = teams.find(team => team.id === preferredTeam) ?? teams[0];
  report.selectedTeam = report.teams[teams.indexOf(selected)].label;
  report.selection = selected.id === preferredTeam ? 'saved_membership' : 'first_membership';
  const noTeam = await request('bots without team (negative control)', '/api/memoh/bots');
  report.missingTeamReproduced = noTeam.row.error === 'missing_team';
  const scoped = await request('bots with X-Team-ID', '/api/memoh/bots', { team: selected.id });
  const queried = await request('bots with team_id query', `/api/memoh/bots?team_id=${encodeURIComponent(selected.id)}`, {
    displayPath: '/api/memoh/bots?team_id=<selected>',
  });
  for (const outcome of [scoped, queried]) {
    const bots = Array.isArray(outcome.body?.items) ? outcome.body.items :
      Array.isArray(outcome.body) ? outcome.body : null;
    if (bots) outcome.row.itemCount = bots.length;
    outcome.validBots = outcome.ok && bots !== null;
  }
  report.result = scoped.validBots ? 'scoped_business_ok' : 'scoped_business_failed';
  report.queryVariantSucceeded = queried.validBots;
  if (ticket && scoped.validBots) {
    const minted = await request('scoped socket ticket (creates temporary credential)', '/api/v1/ws-tickets', {
      method: 'POST', team: selected.id,
    });
    minted.row.ticketPresent = minted.ok && typeof minted.body?.ticket === 'string' && minted.body.ticket.length > 0;
    if (!minted.row.ticketPresent) report.result = 'ticket_failed';
  }
  report.socketHandshakeVerified = false;
  return report;
}

/** Build a standalone snippet; the browser retains HttpOnly cookies without exporting them. */
export function browserSnippet({ ticket = false } = {}) {
  return `(async () => {\nconst run = ${runMemohProbe.toString()};\n` +
    `const report = await run({ origin: location.origin, preferredTeam: localStorage.getItem('memoh.current_team_id') || '', ticket: ${Boolean(ticket)} });\n` +
    `console.log(JSON.stringify(report, null, 2));\nreturn report;\n})()`;
}
