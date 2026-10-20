import { readdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

const [repo, swaggerPath, output] = process.argv.slice(2);
if (!repo || !swaggerPath || !output) {
  console.error('Usage: node audit-memoh-routes.mjs <memoh-repo> <swagger.json> <output.md>');
  process.exit(2);
}
const spec = JSON.parse(await readFile(swaggerPath, 'utf8'));
const documented = new Set();
for (const [url, item] of Object.entries(spec.paths ?? {})) {
  for (const method of ['get', 'post', 'put', 'patch', 'delete', 'head', 'options']) {
    if (item[method]) documented.add(`${method.toUpperCase()} ${matchPath(url)}`);
  }
}
const found = [];
async function visit(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const file = path.join(dir, entry.name);
    if (entry.isDirectory()) await visit(file);
    else if (entry.isFile() && entry.name.endsWith('.go') && !entry.name.endsWith('_test.go')) await scan(file);
  }
}
async function scan(file) {
  const lines = (await readFile(file, 'utf8')).split(/\r?\n/);
  const groups = new Map([['e', ['']]]);
  const stringLists = new Map();
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const listStart = line.match(/for\s+_,\s*(\w+)\s*:=\s*range\s+\[\]string\s*\{/);
    if (listStart) {
      const values = [];
      for (i++; i < lines.length && !/^\s*}\s*(?:\{|$)/.test(lines[i]); i++) {
        const value = lines[i].match(/"([^"]+)"/);
        if (value) values.push(value[1]);
      }
      stringLists.set(listStart[1], values);
      continue;
    }
    const group = line.match(/^\s*(\w+)\s*:?=\s*(\w+)\.Group\(\s*(?:"([^"]*)"|(\w+))/);
    if (group) {
      const [, name, parent, literal, variable] = group;
      const suffixes = literal !== undefined
        ? [literal]
        : stringLists.get(variable) ?? (file.endsWith(`${path.sep}local_channel.go`) ? ['/bots/:bot_id/web'] : ['']);
      groups.set(name, suffixes.map((suffix) => join((groups.get(parent) ?? [''])[0], suffix)));
    }
    const route = line.match(/^\s*(\w+)\.(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\(\s*"([^"]*)"/);
    if (!route) continue;
    const [, receiver, method, routePath] = route;
    for (const prefix of groups.get(receiver) ?? ['']) {
      const fullPath = normalizePath(join(prefix, routePath));
      if (!documented.has(`${method} ${matchPath(fullPath)}`)) {
        found.push({ method, fullPath, file: path.relative(repo, file).replaceAll('\\', '/'), line: i + 1 });
      }
    }
  }
}
function join(prefix, suffix) {
  if (!prefix) return suffix || '';
  if (!suffix || suffix === '/') return prefix;
  return `${prefix.replace(/\/$/, '')}/${suffix.replace(/^\//, '')}`;
}
function normalizePath(value) {
  return value.startsWith('/') ? value : `/${value}`;
}
function matchPath(value) {
  return normalizePath(value).replace(/:[A-Za-z0-9_]+|\{[^}]+\}/g, '{}');
}
for (const root of ['internal', 'cmd']) await visit(path.join(repo, root));
const unique = [...new Map(found.map((item) => [`${item.method} ${item.fullPath} ${item.file}:${item.line}`, item])).values()]
  .sort((a, b) => a.fullPath.localeCompare(b.fullPath) || a.method.localeCompare(b.method));
const lines = [
  '# Registered Memoh routes missing from Swagger', '',
  `- Compared production Echo route registrations under \`internal/\` and \`cmd/\` with the pinned Swagger spec.`,
  `- Undocumented registration count: ${unique.length}.`,
  '- These entries have no OpenAPI operation schema; route-level authentication is determined by server middleware and handler checks.', '',
  '| Method | Full path | Source |', '| --- | --- | --- |',
  ...unique.map((item) => `| ${item.method} | \`${item.fullPath}\` | \`${item.file}:${item.line}\` |`), '',
];
await writeFile(output, lines.join('\n'), 'utf8');
console.log(`Found ${unique.length} registered routes absent from Swagger.`);
