// Screenshots the pages diagrams.py writes into build/ at the device-pixel ratio each
// job asks for, and drops the PNGs beside this file. Chromium comes from CHROME, else from
// PLAYWRIGHT_BROWSERS_PATH, else from the usual system locations.
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const PORT = 9412;
const BUILD = path.join(__dirname, 'build');
const sleep = ms => new Promise(r => setTimeout(r, ms));

function findChrome() {
  const candidates = [process.env.CHROME];
  const pw = process.env.PLAYWRIGHT_BROWSERS_PATH;
  if (pw && fs.existsSync(pw)) {
    for (const d of fs.readdirSync(pw).filter(d => d.startsWith('chromium'))) {
      candidates.push(path.join(pw, d, 'chrome-linux', 'chrome'));
    }
    candidates.push(path.join(pw, 'chromium'));
  }
  candidates.push('/usr/bin/chromium', '/usr/bin/google-chrome',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome');
  const found = candidates.find(c => c && fs.existsSync(c));
  if (!found) throw new Error('No Chromium found. Set CHROME=/path/to/chrome');
  return found;
}

async function main() {
  const jobs = JSON.parse(fs.readFileSync(path.join(BUILD, 'jobs.json'), 'utf8'));
  const chrome = spawn(findChrome(), ['--headless', '--no-sandbox', '--disable-gpu',
    `--remote-debugging-port=${PORT}`, '--hide-scrollbars', 'about:blank'], { stdio: 'ignore' });
  await sleep(2000);
  for (const job of jobs) {
    const url = `file://${path.join(BUILD, job.html)}`;
    const target = await (await fetch(`http://127.0.0.1:${PORT}/json/new?${url}`,
      { method: 'PUT' })).json();
    const ws = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise(r => ws.addEventListener('open', r));
    let id = 0;
    const pending = new Map();
    ws.addEventListener('message', e => {
      const m = JSON.parse(e.data);
      if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id); }
    });
    const send = (method, params = {}) => new Promise(res => {
      const i = ++id; pending.set(i, res);
      ws.send(JSON.stringify({ id: i, method, params }));
    });
    await send('Page.enable');
    await send('Emulation.setDeviceMetricsOverride',
      { width: job.w, height: job.h, deviceScaleFactor: 1, mobile: false });
    await sleep(900);
    const shot = await send('Page.captureScreenshot', {
      format: 'png',
      captureBeyondViewport: true,
      clip: { x: 0, y: 0, width: job.w, height: job.h, scale: job.scale },
    });
    fs.writeFileSync(path.join(__dirname, job.png), Buffer.from(shot.data, 'base64'));
    console.log(job.png, `${job.w}x${job.h} @${job.scale}x`);
    ws.close();
    await fetch(`http://127.0.0.1:${PORT}/json/close/${target.id}`);
  }
  chrome.kill();
}

main().catch(e => { console.error(e); process.exit(1); });
