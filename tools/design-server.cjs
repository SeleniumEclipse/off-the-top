// Local, read-only preview server. Never serves source, signing files, or game data.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.md': 'text/plain; charset=utf-8', '.ttf': 'font/ttf', '.png': 'image/png', '.svg': 'image/svg+xml' };
const server = http.createServer((req, res) => {
  if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405).end(); return; }
  let requested;
  try { requested = decodeURIComponent(new URL(req.url, 'http://localhost').pathname); } catch { res.writeHead(400).end(); return; }
  if (requested === '/') requested = '/design/theme-options.html';
  const file = path.resolve(root, '.' + requested);
  const relative = path.relative(root, file).split(path.sep).join('/');
  const allowed = relative.startsWith('design/') || /^app\/src\/main\/res\/font\/[a-z_]+\.ttf$/.test(relative);
  if (!allowed || relative.startsWith('..') || path.isAbsolute(relative) || !types[path.extname(file)]) { res.writeHead(404).end(); return; }
  fs.readFile(file, (error, contents) => {
    if (error) { res.writeHead(404).end(); return; }
    res.writeHead(200, { 'Content-Type': types[path.extname(file)], 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
    res.end(req.method === 'HEAD' ? undefined : contents);
  });
});
server.listen(4176, '127.0.0.1', () => console.log('Design previews: http://127.0.0.1:4176/design/theme-options.html'));