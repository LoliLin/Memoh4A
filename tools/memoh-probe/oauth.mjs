/** Public OAuth entry-point diagnostics. Never follows Google redirects or uses user cookies. */
export async function runOAuthProbe({ origin = 'https://app.memoh.net', fetchImpl = globalThis.fetch, timeoutMs = 12000 } = {}) {
  const root = new URL(origin);
  const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(root.hostname);
  if (!(root.origin === 'https://app.memoh.net' || (loopback && ['http:', 'https:'].includes(root.protocol))) ||
      root.username || root.password || root.search || root.hash || root.pathname !== '/') {
    throw new Error('Use the official Cloud origin or a loopback test server.');
  }
  const request = async (url, label) => {
    const row = { label, status: null, outcome: 'unknown' };
    try {
      const response = await fetchImpl(url, { method: 'GET', redirect: 'manual', credentials: 'omit', signal: AbortSignal.timeout(timeoutMs) });
      row.status = response.status;
      row.setsCookie = response.headers.has('set-cookie');
      const location = response.headers.get('location');
      if (response.status >= 300 && response.status < 400 && location) {
        const redirect = new URL(location, root.origin);
        const google = redirect.protocol === 'https:' && redirect.hostname === 'accounts.google.com';
        row.outcome = google ? 'starts_google_flow' : 'other_redirect';
        row.redirectTarget = google ? 'google' : redirect.origin === root.origin ? 'same_origin' : 'other';
        if (google) {
          row.statePresent = redirect.searchParams.has('state');
          row.pkce = redirect.searchParams.has('code_challenge')
            ? redirect.searchParams.get('code_challenge_method') === 'S256' ? 'S256' : 'other' : 'absent';
        }
      } else if (response.status === 400) row.outcome = 'request_rejected';
      else if (response.status === 401 || response.status === 403) row.outcome = 'auth_or_policy_block';
      else if (response.ok) row.outcome = 'ok_response';
      // Raw bodies, Location, cookies, state and provider URLs are deliberately not returned.
      await response.body?.cancel();
    } catch (error) {
      row.outcome = ['AbortError', 'TimeoutError'].includes(error?.name) ? 'timeout' : 'network_error';
    }
    return row;
  };
  const callbacks = [
    ['default_return', null],
    ['same_origin', root.origin + '/'],
    ['desktop_shell', root.origin + '/?desktopShell=1'],
    ['native_scheme', 'memoh://oauth/callback'],
    ['loopback', 'http://127.0.0.1:8765/callback'],
    ['foreign_control', 'https://example.com/callback'],
  ];
  const requests = await Promise.all(callbacks.map(([label, target]) => {
    const url = new URL('/api/v1/auth/oauth/google/login', root.origin);
    if (target) url.searchParams.set('return_to', target);
    return request(url, label);
  }));
  return {
    version: 1,
    authenticationCompleted: false,
    scope: 'public_oauth_start_only',
    requests,
    interpretation: 'A Google redirect proves only that OAuth started; it does not prove callback completion or native credential handoff.',
  };
}
