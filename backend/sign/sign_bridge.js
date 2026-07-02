// Persistent Node bridge: read one JSON request per line from stdin,
// call the vendored a_bogus generator, write one JSON response per line.
// Kept alive across many requests by the Python signer to avoid ~200ms
// node startup cost per call.
const { get_ab } = require('./dy_ab.js');

let buf = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => {
  buf += chunk;
  let idx;
  while ((idx = buf.indexOf('\n')) >= 0) {
    const line = buf.slice(0, idx);
    buf = buf.slice(idx + 1);
    handle(line);
  }
});
process.stdin.on('end', () => { if (buf.trim()) handle(buf); });

function handle(line) {
  line = line.trim();
  if (!line) return;
  try {
    const req = JSON.parse(line);
    let out;
    if (req.op === 'ab') {
      out = { ok: true, value: get_ab(req.query || '', req.data || '') };
    } else if (req.op === 'ping') {
      out = { ok: true, value: 'pong' };
    } else {
      out = { ok: false, error: 'unknown op: ' + req.op };
    }
    process.stdout.write(JSON.stringify(out) + '\n');
  } catch (e) {
    process.stdout.write(JSON.stringify({ ok: false, error: String((e && e.stack) || e) }) + '\n');
  }
}
