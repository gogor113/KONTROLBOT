/*
Enhanced server.js for GIRD Cloud Run
- Supports optional HMAC verification via env HMAC_SECRET
- Verifies x-api-key and (if HMAC_SECRET set) x-signature (hex HMAC-SHA256 of raw body)
- Writes to Google Sheets (Monitor and Events)
- Admin endpoints: /v1/instances, /v1/reindex
- Janitor marks stale instances offline
*/

const express = require('express');
const bodyParser = require('body-parser');
const helmet = require('helmet');
const crypto = require('crypto');
const {google} = require('googleapis');

const app = express();
app.use(helmet());
// capture raw body for HMAC verification
app.use(bodyParser.json({ limit: '128kb', verify: function(req, res, buf, encoding){ req.rawBody = buf; } }));

// Config (SHEET_ID default set to provided spreadsheet)
const SHEET_ID = process.env.SHEET_ID || '1u3t7J9LlXHCaMgbD1qsWYJLU8W37zM_Tb2DKExpx_VY';
const API_KEY = process.env.API_KEY || ''; // Recommended: load from Secret Manager
const HMAC_SECRET = process.env.HMAC_SECRET || ''; // optional: if set, server enforces x-signature
const PORT = process.env.PORT || 8080;
const HEARTBEAT_TTL = parseInt(process.env.HEARTBEAT_TTL || '300'); // seconds
const RATE_LIMIT_PER_MIN = parseInt(process.env.RATE_LIMIT_PER_MIN || '30');
const INSTANCE_CLEAN_INTERVAL = parseInt(process.env.INSTANCE_CLEAN_INTERVAL || '60'); // seconds

if (!SHEET_ID) { console.error('Missing SHEET_ID env var. Exit.'); process.exit(1); }
if (!API_KEY) { console.warn('Warning: API_KEY env var is empty. Requests will be rejected until API_KEY is configured.'); }

const auth = new google.auth.GoogleAuth({ scopes: ['https://www.googleapis.com/auth/spreadsheets'] });
let sheetsClient = null;
async function getSheets() { if(!sheetsClient){ const client = await auth.getClient(); sheetsClient = google.sheets({version:'v4', auth: client}); } return sheetsClient; }

const MONITOR_SHEET_NAME = 'Monitor';
const EVENTS_SHEET_NAME = 'Events';
const MONITOR_HEADERS = ['timestamp','account_login','account_name','broker','server','terminal_id','symbol','balance','equity','free_margin','positions_count','open_positions_json','last_event','notes','status'];

function nowIso(){ return new Date().toISOString(); }
function nowMs(){ return Date.now(); }

const instances = new Map();
const rateLimiter = new Map();

function isRateLimited(account){ if(!account) return false; const key = account; const now = Date.now(); const windowMs = 60*1000; const limit = RATE_LIMIT_PER_MIN; let data = rateLimiter.get(key); if(!data || now - data.windowStart > windowMs){ rateLimiter.set(key, {count:1, windowStart: now}); return false; } else { data.count++; if(data.count > limit) return true; rateLimiter.set(key, data); return false; } }

async function ensureHeaders(){ const sheets = await getSheets(); const range = `${MONITOR_SHEET_NAME}!A1:Z1`; try{ const resp = await sheets.spreadsheets.values.get({ spreadsheetId: SHEET_ID, range }); const values = resp.data.values || []; if(!values.length || !values[0].length){ await sheets.spreadsheets.values.update({ spreadsheetId: SHEET_ID, range:`${MONITOR_SHEET_NAME}!A1`, valueInputOption:'RAW', requestBody:{ values:[MONITOR_HEADERS] } }); console.log('Monitor header created'); return MONITOR_HEADERS; } return values[0]; } catch(err){ // try create header
    const sheets = await getSheets();
    await sheets.spreadsheets.values.update({ spreadsheetId: SHEET_ID, range:`${MONITOR_SHEET_NAME}!A1`, valueInputOption:'RAW', requestBody:{ values:[MONITOR_HEADERS] } });
    return MONITOR_HEADERS;
  }
}

function buildMonitorRowObject(payload, status='online'){ return {
  timestamp: nowIso(),
  account_login: payload.account_login || payload.account || '',
  account_name: payload.account_name || '',
  broker: payload.broker || '',
  server: payload.server || '',
  terminal_id: payload.terminal_id || '',
  symbol: payload.symbol || '',
  balance: payload.balance !== undefined ? payload.balance : '',
  equity: payload.equity !== undefined ? payload.equity : '',
  free_margin: payload.free_margin !== undefined ? payload.free_margin : '',
  positions_count: payload.positions_count !== undefined ? payload.positions_count : '',
  open_positions_json: payload.open_positions_json ? JSON.stringify(payload.open_positions_json) : '',
  last_event: payload.event || '',
  notes: payload.notes ? JSON.stringify(payload.notes) : '',
  status
}; }

async function appendEventRow(payload){ const sheets = await getSheets(); const header = ['timestamp','account_login','event','symbol','details']; try{ const resp = await sheets.spreadsheets.values.get({ spreadsheetId: SHEET_ID, range:`${EVENTS_SHEET_NAME}!A1:Z1` }); const values = resp.data.values || []; if(!values.length || !values[0].length){ await sheets.spreadsheets.values.update({ spreadsheetId: SHEET_ID, range:`${EVENTS_SHEET_NAME}!A1`, valueInputOption:'RAW', requestBody:{ values:[header] } }); } } catch(e){ const sheets = await getSheets(); await sheets.spreadsheets.values.update({ spreadsheetId: SHEET_ID, range:`${EVENTS_SHEET_NAME}!A1`, valueInputOption:'RAW', requestBody:{ values:[header] } }); }
  const row = [ nowIso(), payload.account_login || payload.account || '', payload.event || '', payload.symbol || '', JSON.stringify(payload.extra || payload) ];
  const sheets = await getSheets();
  await sheets.spreadsheets.values.append({ spreadsheetId: SHEET_ID, range:`${EVENTS_SHEET_NAME}!A1`, valueInputOption:'RAW', insertDataOption:'INSERT_ROWS', requestBody:{ values: [row] } });
}

async function upsertMonitorRow(payload){ const sheets = await getSheets(); const header = await ensureHeaders(); const account = payload.account_login || payload.account; if(!account){ const rowObj = buildMonitorRowObject(payload, 'unknown'); const values = header.map(h => rowObj[h] !== undefined ? rowObj[h] : ''); await sheets.spreadsheets.values.append({ spreadsheetId: SHEET_ID, range:`${MONITOR_SHEET_NAME}!A1`, valueInputOption:'RAW', insertDataOption:'INSERT_ROWS', requestBody:{ values: [values] } }); return {appended:true}; }
  const readRange = `${MONITOR_SHEET_NAME}!A2:Z10000`;
  const resp = await sheets.spreadsheets.values.get({ spreadsheetId: SHEET_ID, range: readRange });
  const values = resp.data.values || [];
  const accountColIndex = header.indexOf('account_login');
  let foundRowIndex = -1;
  for(let r=0;r<values.length;r++){ const row = values[r]; if(accountColIndex>=0 && row[accountColIndex] && String(row[accountColIndex])===String(account)){ foundRowIndex = 2 + r; break; } }
  const rowObj = buildMonitorRowObject(payload, 'online'); const out = header.map(h => rowObj[h] !== undefined ? rowObj[h] : '');
  if(foundRowIndex > 0){ const endCol = String.fromCharCode(65 + out.length -1); const range = `${MONITOR_SHEET_NAME}!A${foundRowIndex}:${endCol}${foundRowIndex}`; await sheets.spreadsheets.values.update({ spreadsheetId: SHEET_ID, range, valueInputOption:'RAW', requestBody:{ values:[out] } }); return {updated:true, row: foundRowIndex}; } else { await sheets.spreadsheets.values.append({ spreadsheetId: SHEET_ID, range: `${MONITOR_SHEET_NAME}!A1`, valueInputOption:'RAW', insertDataOption:'INSERT_ROWS', requestBody:{ values:[out] } }); return {appended:true}; }
}

async function withRetries(fn, attempts=3){ let lastErr=null; for(let i=0;i<attempts;i++){ try{ return await fn(); } catch(err){ lastErr = err; const reason = (err && err.errors && err.errors[0] && err.errors[0].reason) || (err && err.code) || err.message || String(err); console.warn(`Sheets op failed (attempt ${i+1}/${attempts}):`, reason); if(String(reason).includes('rateLimitExceeded') || String(reason).includes('userRateLimitExceeded')){ await new Promise(r=>setTimeout(r, 200*(i+1))); continue; } else break; } } throw lastErr; }

// helper to compute HMAC hex
function computeHmacHex(secret, buf){ return crypto.createHmac('sha256', secret).update(buf).digest('hex'); }

app.post('/v1/report', async (req, res) => {
  try{
    const incomingKey = (req.get('x-api-key') || '').trim();
    if(!incomingKey || incomingKey !== API_KEY) return res.status(401).json({ok:false, error:'invalid_api_key'});

    // if HMAC_SECRET set, enforce x-signature
    if(HMAC_SECRET){ const sig = (req.get('x-signature')||'').trim(); if(!sig){ return res.status(401).json({ok:false, error:'missing_signature'}); } const expected = computeHmacHex(HMAC_SECRET, req.rawBody || Buffer.from(JSON.stringify(req.body))); if(sig !== expected){ return res.status(401).json({ok:false, error:'invalid_signature'}); } }

    const payload = req.body; if(!payload || typeof payload !== 'object') return res.status(400).json({ok:false, error:'invalid_payload'});
    const account = payload.account_login || payload.account; if(account && isRateLimited(account)) return res.status(429).json({ok:false, error:'rate_limited'});

    const now = nowMs(); const inst = instances.get(account) || { firstSeen: now, payload: {} }; inst.lastSeen = now; inst.payload = { ...inst.payload, ...payload }; inst.status = 'online'; instances.set(account, inst);

    const eventPromise = withRetries(()=>appendEventRow(payload)).catch(e=>{ console.error('Failed to append event to sheet:', e && e.message ? e.message : e); });
    const upsertPromise = withRetries(()=>upsertMonitorRow(payload)).catch(e=>{ console.error('Failed to upsert monitor row:', e && e.message ? e.message : e); });
    await Promise.all([eventPromise, upsertPromise]);
    return res.json({ok:true});
  }catch(err){ console.error('report processing error', err && err.stack ? err.stack : err); return res.status(500).json({ok:false, error:'server_error', detail: String(err)}); }
});

app.get('/v1/instances', (req, res) => { const activeOnly = String(req.query.activeOnly||'').toLowerCase() === 'true'; const list = []; const now = nowMs(); for(const [account,obj] of instances.entries()){ const isActive = (now - obj.lastSeen) <= HEARTBEAT_TTL*1000; if(activeOnly && !isActive) continue; list.push({ account, status: obj.status, lastSeen: new Date(obj.lastSeen).toISOString(), payload: obj.payload }); } res.json({ ok:true, instances: list }); });

app.post('/v1/reindex', async (req, res) => { try{ const incomingKey = (req.get('x-api-key')||'').trim(); if(!incomingKey || incomingKey !== API_KEY) return res.status(401).json({ok:false, error:'invalid_api_key'}); const sheets = await getSheets(); await sheets.spreadsheets.values.update({ spreadsheetId: SHEET_ID, range: `${MONITOR_SHEET_NAME}!A1`, valueInputOption: 'RAW', requestBody: { values: [MONITOR_HEADERS] } }); const rows = []; for(const [account,obj] of instances.entries()){ const payload = obj.payload || {}; const rowObj = buildMonitorRowObject(payload, obj.status || 'offline'); const out = MONITOR_HEADERS.map(h => (rowObj[h] !== undefined ? rowObj[h] : '')); rows.push(out); } if(rows.length > 0){ await sheets.spreadsheets.values.update({ spreadsheetId: SHEET_ID, range: `${MONITOR_SHEET_NAME}!A2`, valueInputOption: 'RAW', requestBody: { values: rows } }); } return res.json({ ok:true, count: rows.length }); }catch(err){ console.error('Reindex error', err); return res.status(500).json({ ok:false, error: String(err) }); } });

app.get('/health', (req,res)=> res.send('ok'));

async function janitorTick(){ try{ const now = nowMs(); const toMark = []; for(const [account,obj] of instances.entries()){ if(obj.status === 'online' && now - obj.lastSeen > HEARTBEAT_TTL*1000){ obj.status = 'offline'; instances.set(account, obj); toMark.push({ account, payload: obj.payload }); } } if(toMark.length > 0){ for(const itm of toMark){ try{ await withRetries(()=>upsertMonitorRow({ ...itm.payload, event: 'stale' })); }catch(e){ console.error('Janitor: failed to mark stale instance in sheet', itm.account, e && e.message ? e.message : e); } } } }catch(e){ console.error('Janitor error', e); } }
setInterval(janitorTick, INSTANCE_CLEAN_INTERVAL*1000);

app.listen(PORT, ()=>{ console.log(`GIRD monitor server listening on port ${PORT}`); console.log(`SHEET_ID=${SHEET_ID} HEARTBEAT_TTL=${HEARTBEAT_TTL}s HMAC=${HMAC_SECRET? 'ENABLED' : 'DISABLED'}`); });
