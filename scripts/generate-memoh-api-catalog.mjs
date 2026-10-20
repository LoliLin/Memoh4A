import { readFile, writeFile } from 'node:fs/promises';

const [input, output, sourceCommit] = process.argv.slice(2);
if (!input || !output || !sourceCommit) {
  console.error('Usage: node generate-memoh-api-catalog.mjs <swagger.json> <output.md> <source-commit>');
  process.exit(2);
}

const spec = JSON.parse(await readFile(input, 'utf8'));
const methods = ['get', 'post', 'put', 'patch', 'delete', 'head', 'options'];
const operations = [];
for (const [path, pathItem] of Object.entries(spec.paths ?? {})) {
  for (const method of methods) {
    const op = pathItem[method];
    if (!op) continue;
    const parameters = [...(pathItem.parameters ?? []), ...(op.parameters ?? [])]
      .map((p) => `${p.name ?? '-'} (${p.in ?? '-'}${p.required ? ', required' : ''})${schema(p.schema ?? p)}`)
      .join('<br>') || '—';
    const body = (op.parameters ?? []).find((p) => p.in === 'body');
    const request = body ? schema(body.schema) : (op.consumes?.length ? op.consumes.join(', ') : '—');
    const responses = Object.entries(op.responses ?? {})
      .map(([status, response]) => `${status}${response.description ? ` ${response.description}` : ''}${schema(response.schema)}`)
      .join('<br>') || '—';
    const security = op.security ?? spec.security ?? [];
    const auth = security.length ? security.map((s) => Object.keys(s).join('+') || 'optional').join('<br>') : 'none declared';
    operations.push({
      path, method: method.toUpperCase(), tag: (op.tags ?? []).join(', ') || '—',
      summary: op.summary ?? op.operationId ?? '—', operationId: op.operationId ?? '—',
      auth, parameters, request, responses,
    });
  }
}
operations.sort((a, b) => a.path.localeCompare(b.path) || a.method.localeCompare(b.method));
function schema(value) {
  if (!value) return '';
  if (value.$ref) return ` → \`${value.$ref.replace('#/definitions/', '')}\``;
  if (value.type) return ` → \`${value.type}${value.format ? `/${value.format}` : ''}\``;
  if (value.items) return ` → array${schema(value.items)}`;
  return '';
}
function describe(value) {
  if (!value) return 'any';
  if (value.$ref) return value.$ref.replace('#/definitions/', '');
  let result = value.type ?? (value.properties ? 'object' : 'any');
  if (value.format) result += `/${value.format}`;
  if (value.items) result += `[] of ${describe(value.items)}`;
  if (value.additionalProperties) result += ` map of ${describe(value.additionalProperties)}`;
  if (value.enum) result += ` enum(${value.enum.join(', ')})`;
  if (value.allOf) result += ` & ${value.allOf.map(describe).join(' & ')}`;
  return result;
}
function cell(value) { return String(value).replaceAll('|', '\\|').replaceAll('\n', ' '); }

const definitionLines = ['## Schema definitions', ''];
for (const [name, definition] of Object.entries(spec.definitions ?? {}).sort(([a], [b]) => a.localeCompare(b))) {
  definitionLines.push(`### \`${name}\``);
  const required = new Set(definition.required ?? []);
  if (definition.properties && Object.keys(definition.properties).length) {
    definitionLines.push('', '| Field | Type | Required | Description |', '| --- | --- | --- | --- |');
    for (const [field, property] of Object.entries(definition.properties)) {
      definitionLines.push(`| \`${cell(field)}\` | \`${cell(describe(property))}\` | ${required.has(field) ? 'yes' : 'no'} | ${cell(property.description ?? '—')} |`);
    }
  } else {
    definitionLines.push('', `Schema: \`${cell(describe(definition))}\``);
  }
  if (definition.additionalProperties && !definition.properties) {
    definitionLines.push('', `Additional properties: \`${cell(describe(definition.additionalProperties))}\``);
  }
  definitionLines.push('');
}

const lines = [
  '# Memoh API catalog', '',
  `- Source: \`felinics/Memoh\` Swagger ${spec.swagger ?? 'unknown'} spec at commit \`${sourceCommit}\``,
  `- API base path: \`${spec.basePath ?? '/'}\``,
  `- Paths: ${Object.keys(spec.paths ?? {}).length}; operations: ${operations.length}; definitions: ${Object.keys(spec.definitions ?? {}).length}.`,
  '- This is the backend contract inventory, not a claim that every administrative or internal endpoint belongs in the Android UI.',
  '- Authentication is reported only as declared by the spec; runtime middleware may add constraints.',
  '- Production routes absent from Swagger are listed in [the route registration appendix](MEMOH_UNDOCUMENTED_ROUTES.md).', '',
  '| Method | Path | Tag | Operation | Auth | Parameters | Request | Responses |',
  '| --- | --- | --- | --- | --- | --- | --- | --- |',
  ...operations.map((op) => `| ${op.method} | \`${cell(op.path)}\` | ${cell(op.tag)} | ${cell(op.summary)}<br><code>${cell(op.operationId)}</code> | ${cell(op.auth)} | ${cell(op.parameters)} | ${cell(op.request)} | ${cell(op.responses)} |`),
  '',
  ...definitionLines,
];
await writeFile(output, lines.join('\n'), 'utf8');
console.log(`Wrote ${operations.length} operations from ${Object.keys(spec.paths ?? {}).length} paths.`);
