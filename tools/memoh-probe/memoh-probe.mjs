#!/usr/bin/env node
/** Node 20+ entry point. Never executes a copied cURL command or prints its credentials. */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';
import { browserSnippet, runMemohProbe } from './protocol.mjs';
import { runOAuthProbe } from './oauth.mjs';

export function parseSessionCapture(text) {
  // Accept Chrome's bash and cmd quoting. This is text extraction, not shell execution.
  const input = text.replace(/\^\r?\n|\\\r?\n/g, ' ').replace(/\^"/g, '"');
  const target = /https?:\/\/[^\s'"<>]+/i.exec(input)?.[0];
  if (!target || new URL(target).origin !== 'https://app.memoh.net') {
    throw new Error('The capture must target https://app.memoh.net.');
  }
  const quoted = "(?:'([^']*)'|\"([^\"]*)\")";
  const readOption = pattern => {
    const match = new RegExp(pattern + '\\s+' + quoted, 'i').exec(input);
    return (match?.[1] ?? match?.[2] ?? '').trim();
  };
  const readHeader = name => {
    const expression = new RegExp('(?:-H|--header)\\s+' + quoted, 'gi');
    for (const match of input.matchAll(expression)) {
      const header = match[1] ?? match[2] ?? '';
      const colon = header.indexOf(':');
      if (header.slice(0, colon).trim().toLowerCase() === name) return header.slice(colon + 1).trim();
    }
    return '';
  };
  const cookie = readHeader('cookie') || readOption('(?:-b|--cookie)');
  const authorization = readHeader('authorization');
  if (!cookie && !authorization) throw new Error('The capture has no Cookie or Authorization header.');
  if ([cookie, authorization].some(value => /[\r\n]/.test(value))) throw new Error('Invalid captured header.');
  return {
    headers: { ...(cookie ? { Cookie: cookie } : {}), ...(authorization ? { Authorization: authorization } : {}) },
    preferredTeam: readHeader('x-team-id'),
  };
}

async function main(args) {
  const flags = new Set(['--help', '--anonymous', '--browser', '--ticket', '--session-file', '--oauth']);
  let sessionFile;
  for (let index = 0; index < args.length; index++) {
    if (!flags.has(args[index])) throw new Error('Unknown option. Use --help for supported options.');
    if (args[index] === '--session-file') {
      sessionFile = args[++index];
      if (!sessionFile || sessionFile.startsWith('--')) throw new Error('--session-file requires a path.');
    }
  }
  if (args.includes('--help')) {
    console.log([
      'Memoh protocol probe (Node 20+)',
      '  --anonymous               Verify unauthenticated behavior; no credentials needed',
      '  --oauth                   Check public Google OAuth starts; never follows redirects',
      '  --browser                 Print a snippet to run in the signed-in Cloud page',
      '  --session-file <path>     Read a saved Copy-as-cURL capture without executing it',
      '  --ticket                  Also mint a scoped temporary WebSocket credential',
      'Default: read curl.txt beside this script. Output contains diagnostic metadata only.',
    ].join('\n'));
    return;
  }
  if (args.includes('--oauth')) {
    if (sessionFile || args.includes('--browser') || args.includes('--ticket')) throw new Error('Use --oauth by itself.');
    const report = await runOAuthProbe();
    console.log(JSON.stringify(report, null, 2));
    if (report.requests.some(row => row.status === null)) process.exitCode = 1;
    return;
  }
  if (args.includes('--browser')) {
    console.log(browserSnippet({ ticket: args.includes('--ticket') }));
    return;
  }
  let session = { headers: {}, preferredTeam: '' };
  if (!args.includes('--anonymous')) {
    const path = sessionFile ? resolve(sessionFile) : join(dirname(fileURLToPath(import.meta.url)), 'curl.txt');
    let capture;
    try { capture = readFileSync(path, 'utf8'); }
    catch { throw new Error('No session capture. Use --browser or --session-file; see README.md.'); }
    session = parseSessionCapture(capture);
  }
  const report = await runMemohProbe({ ...session, ticket: args.includes('--ticket') });
  console.log(JSON.stringify(report, null, 2));
  if (report.result === 'not_authenticated') process.exitCode = 2;
  else if (report.result !== 'scoped_business_ok') process.exitCode = 1;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).catch(() => {
    // Exception messages can contain copied credentials or server bodies; never echo them.
    console.error('Probe could not start. Check Node version, options and session capture; use --help.');
    process.exitCode = 1;
  });
}
