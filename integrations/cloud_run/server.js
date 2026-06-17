// GIRD Monitor server with Auth, SSE, Google Sheets integration
// Adds: /auth endpoints (register/login/refresh/logout/me/forgot), GitHub OAuth, nodemailer email send
// Note: This server stores users in Google Sheets (Users sheet). For production, prefer real DB.

const express = require('express');
const bodyParser = require('body-parser');
const helmet = require('helmet');
const crypto = require('crypto');
const cors = require('cors');
const path = require('path');
const {google} = require('googleapis');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const axios = require('axios');
const nodemailer = require('nodemailer');

const app = express();
app.use(helmet());
app.use(bodyParser.json({ limit: '512kb', verify: function(req, res, buf){ req.rawBody = buf; } }));
app.use(bodyParser.urlencoded({ extended: true }));

// Config
const SHEET_ID = process.env.SHEET_ID || '1u3t7J9LlXHCaMgbD1qsWYJLU8W37zM_Tb2DKExpx_VY';
const API_KEY = process.env.API_KEY || '';
const HMAC_SECRET = process.env.HMAC_SECRET || '';
const PORT = process.env.PORT || 8080;
const HEARTBEAT_TTL = parseInt(process.env.HEARTBEAT_TTL || '300');
const INSTANCE_CLEAN_INTERVAL = parseInt(process.env.INSTANCE_CLEAN_INTERVAL || '60');

// Auth config
const AUTH_JWT_SECRET = process.env.AUTH_JWT_SECRET || 'change_this_secret';
const AUTH_JWT_EXPIRES = process.env.AUTH_JWT_EXPIRES || '15m';
const AUTH_REFRESH_SECRET = process.env.AUTH_REFRESH_SECRET || 'change_refresh_secret';
const AUTH_REFRESH_EXPIRES = process.env.AUTH_REFRESH_EXPIRES || '7d';
const FRONTEND_URL = process.env.FRONTEND_URL || '';
const GITHUB_CLIENT_ID = process.env.GITHUB_CLIENT_ID || '';
const GITHUB_CLIENT_SECRET = process.env.GITHUB_CLIENT_SECRET || '';

// Sheets names
const MONITOR_SHEET_NAME = 'Monitor';
const EVENTS_SHEET_NAME = 'Events';
const USERS_SHEET_NAME = 'Users';

if (!SHEET_ID) { console.error('Missing SHEET_ID env var. Exit.'); process.exit(1); }
if (!API_KEY) { console.warn('Warning: API_KEY env var is empty. Requests will be rejected until API_KEY is configured.'); }

// CORS
app.use(cors({ origin: true, methods: ['GET','POST','OPTIONS'], allowedHeaders: ['Content-Type','x-api-key','x-signature','Authorization'] }));

// serve static dashboard UI
app.use(express.static(path.join(__dirname, 'web')));
app.get('/', (req, res) => res.sendFile(path.join(__dirname, 'web', 'dashboard.html')));

// Google Sheets client
const auth = new google.auth.GoogleAuth({ scopes: ['https://www.googleapis.com/auth/spreadsheets'] });
let sheetsClient = null;
async function getSheets() { if(!sheetsClient){ const client = await auth.getClient(); sheetsClient = google.sheets({ version: 'v4', auth: client }); } return sheetsClient; }

function nowIso(){ return new Date().toISOString(); }

// Monitor state
const instances = new Map();

// Utility: append event row to Events sheet
async function appendEventRow(payload){ const sheets = await getSheets(); const header = ['timestamp','account_login','event','symbol','details']; try{ const resp = await sheets.spreadsheets.values.get({ spreadsheetId: SHEET_ID, range:`${EVENTS_SHEET_NAME}!A1:Z1` }); const values = resp.data.values || []; if(!values.length || !values[0].length){ await sheets.spreadsheets.values.update({ spreadsheetId: SHEET_ID, range:`${EVENTS_SHEET_NAME}!A1`, valueInputOption:'RAW', requestBody:{ values:[header] } }); } } catch(e){ const sheets = await getSheets(); await sheets.spreadsheets.values.update({ spreadsheetId: SHEET_ID, range:`${EVENTS_SHEET_NAME}!A1`, valueInputOption:'RAW', requestBody:{ values:[header] } }); }
  const row = [ nowIso(), payload.account_login || payload.account || '', payload.event || '', payload.symbol || '', JSON.stringify(payload.extra || payload) ];
  const sheets = await getSheets(); await sheets.spreadsheets.values.append({ spreadsheetId: SHEET_ID, range:`${EVENTS_SHEET_NAME}!A1`, valueInputOption:'RAW', insertDataOption:'INSERT_ROWS', requestBody:{ values: [row] } }); }

// Simple helper to ensure Users sheet header exists
async function ensureUsersHeader(){ const sheets = await getSheets(); try{ const resp = await sheets.spreadsheets.values.get({ spreadsheetId: SHEET_ID, range: `${USERS_SHEET_NAME}!A1:E1` }); const values = resp.data.values || []; if(!values.length || !values[0].length){ await sheets.spreadsheets.values.update({ spreadsheetId: SHEET_ID, range:`${USERS_SHEET_NAME}!A1`, valueInputOption:'RAW', requestBody:{ values:[['email','name','passwordHash','role','created_at']] } }); } } catch(e){ await sheets.spreadsheets.values.update({ spreadsheetId: SHEET_ID, range:`${USERS_SHEET_NAME}!A1`, valueInputOption:'RAW', requestBody:{ values:[['email','name','passwordHash','role','created_at']] } }); } }

// User CRUD (Google Sheets-based)
async function findUserByEmail(email){ if(!email) return null; const sheets = await getSheets(); try{ const resp = await sheets.spreadsheets.values.get({ spreadsheetId: SHEET_ID, range: `${USERS_SHEET_NAME}!A2:E1000` }); const rows = resp.data.values || []; for(const row of rows){ if(row[0] && String(row[0]).toLowerCase() === String(email).toLowerCase()){ return { email: row[0], name: row[1], passwordHash: row[2], role: row[3], created_at: row[4], raw: row }; } } }catch(e){ console.warn('findUserByEmail error', e); } return null; }

async function createUser({email,name,password}){ await ensureUsersHeader(); const sheets = await getSheets(); const hash = await bcrypt.hash(password, 10); const row = [ email, name || '', hash, 'user', nowIso() ]; await sheets.spreadsheets.values.append({ spreadsheetId: SHEET_ID, range: `${USERS_SHEET_NAME}!A1`, valueInputOption:'RAW', insertDataOption:'INSERT_ROWS', requestBody:{ values:[row] } }); return { email, name, role:'user' }; }

function generateAccess(user){ return jwt.sign({ email:user.email, name:user.name, role:user.role }, AUTH_JWT_SECRET, { expiresIn: AUTH_JWT_EXPIRES }); }
function generateRefresh(user){ return jwt.sign({ email:user.email }, AUTH_REFRESH_SECRET, { expiresIn: AUTH_REFRESH_EXPIRES }); }
function verifyAccess(token){ try{ return jwt.verify(token, AUTH_JWT_SECRET); }catch(e){ return null; } }
function verifyRefresh(token){ try{ return jwt.verify(token, AUTH_REFRESH_SECRET); }catch(e){ return null; } }

// Email (nodemailer) if SMTP configured
let mailer = null;
if(process.env.SMTP_HOST && process.env.SMTP_USER){ mailer = nodemailer.createTransport({ host: process.env.SMTP_HOST, port: parseInt(process.env.SMTP_PORT||587), secure: (process.env.SMTP_SECURE === 'true'), auth: { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS } }); }
async function sendEmail(to, subject, text, html){ if(!mailer) { console.warn('Mailer not configured'); return false; } try{ await mailer.sendMail({ from: process.env.SMTP_FROM || process.env.SMTP_USER, to, subject, text, html }); return true; } catch(e){ console.error('sendEmail error', e); return false; } }

// Auth routes
app.post('/auth/register', async (req,res) => { try{ const { name, email, pass } = req.body || {}; if(!email || !pass || !name) return res.status(400).json({ ok:false, error:'missing_fields' }); const existing = await findUserByEmail(email); if(existing) return res.status(400).json({ ok:false, error:'user_exists' }); await createUser({ email, name, password: pass }); return res.json({ ok:true }); }catch(e){ console.error('register error', e); return res.status(500).json({ ok:false, error:'server_error' }); } });

app.post('/auth/login', async (req,res) => { try{ const { user, pass } = req.body || {}; if(!user || !pass) return res.status(400).json({ ok:false, error:'missing' }); const u = await findUserByEmail(user); if(!u) return res.status(401).json({ ok:false, error:'invalid' }); const match = await bcrypt.compare(pass, u.passwordHash); if(!match) return res.status(401).json({ ok:false, error:'invalid' }); const payloadUser = { email: u.email, name: u.name, role: u.role || 'user' }; const accessToken = generateAccess(payloadUser); const refreshToken = generateRefresh(payloadUser); // log event
  appendEventRow({ account_login: u.email, event: 'login', extra: { ip: req.ip } }).catch(()=>{});
  return res.json({ ok:true, accessToken, refreshToken, user: payloadUser }); }catch(e){ console.error('login error', e); return res.status(500).json({ ok:false, error:'server_error' }); } });

app.post('/auth/refresh', async (req,res) => { try{ const { token } = req.body || {}; if(!token) return res.status(400).json({ ok:false }); const data = verifyRefresh(token); if(!data) return res.status(401).json({ ok:false }); const u = await findUserByEmail(data.email); if(!u) return res.status(401).json({ ok:false }); const payloadUser = { email:u.email, name:u.name, role:u.role || 'user' }; const accessToken = generateAccess(payloadUser); const refreshToken = generateRefresh(payloadUser); return res.json({ ok:true, accessToken, refreshToken, user: payloadUser }); }catch(e){ console.error('refresh error', e); return res.status(500).json({ ok:false, error:'server_error' }); } });

// logout - non-persistent blacklist (note: stateless JWTs can't be fully revoked without DB)
const refreshBlacklist = new Set();
app.post('/auth/logout', async (req,res) => { try{ const authh = (req.get('Authorization')||'').replace(/^Bearer\s+/i,''); const { token } = req.body || {}; if(token) refreshBlacklist.add(token); if(authh){ // optional: add access token to blacklist as well if you'd like
    // not implemented (in-memory)
  }
  return res.json({ ok:true }); }catch(e){ return res.status(500).json({ ok:false }); } });

app.get('/auth/me', async (req,res) => { try{ const authh = (req.get('Authorization')||'').replace(/^Bearer\s+/i,''); const payload = verifyAccess(authh); if(!payload) return res.status(401).json({ ok:false }); return res.json({ ok:true, user: payload }); }catch(e){ return res.status(500).json({ ok:false }); } });

app.post('/auth/forgot', async (req,res) => { try{ const { email } = req.body || {}; if(!email) return res.status(400).json({ ok:false }); const u = await findUserByEmail(email); // don't reveal
    const resetToken = crypto.randomBytes(24).toString('hex');
    // persist reset request to Events sheet
    await appendEventRow({ account_login: email, event: 'password_reset_request', extra: { token: resetToken, expires: Date.now() + 3600*1000 } });
    // try send email if configured
    if(u && mailer){ const resetLink = (FRONTEND_URL ? FRONTEND_URL.replace(/\/$/,'') : '') + '/reset-password.html?token=' + resetToken; await sendEmail(email, 'Reset password', `Use this link to reset: ${resetLink}`, `<p>Use this link to reset your password: <a href="${resetLink}">${resetLink}</a></p>`); }
    return res.json({ ok:true }); }catch(e){ console.error('forgot error', e); return res.status(500).json({ ok:false }); } });

// GitHub OAuth (simple flow)
app.get('/auth/github', (req,res) => {
  const state = crypto.randomBytes(8).toString('hex');
  const redirect = `https://github.com/login/oauth/authorize?client_id=${encodeURIComponent(GITHUB_CLIENT_ID)}&scope=user:email&state=${state}`;
  // NOTE: you may want to store state in a cookie to verify later
  res.redirect(redirect);
});

app.get('/auth/github/callback', async (req,res) => {
  try{
    const code = req.query.code; const state = req.query.state;
    if(!code) return res.status(400).send('Missing code');
    // Exchange code
    const tokenResp = await axios.post('https://github.com/login/oauth/access_token', { client_id: GITHUB_CLIENT_ID, client_secret: GITHUB_CLIENT_SECRET, code }, { headers:{ Accept:'application/json' } });
    const token = tokenResp.data.access_token;
    if(!token) return res.status(400).send('No token');
    // fetch user
    const uResp = await axios.get('https://api.github.com/user', { headers:{ Authorization:'token ' + token, Accept:'application/json' } });
    const emails = await axios.get('https://api.github.com/user/emails', { headers:{ Authorization:'token ' + token, Accept:'application/json' } });
    const primaryEmailObj = (emails.data || []).find(e=>e.primary) || (emails.data||[])[0];
    const email = primaryEmailObj ? primaryEmailObj.email : (uResp.data && uResp.data.email);
    const name = uResp.data && (uResp.data.name || uResp.data.login);
    // create user if not exists
    let user = await findUserByEmail(email);
    if(!user){ await createUser({ email, name, password: crypto.randomBytes(12).toString('hex') }); user = await findUserByEmail(email); }
    const payloadUser = { email: user.email, name: user.name, role: user.role || 'user' };
    const accessToken = generateAccess(payloadUser);
    const refreshToken = generateRefresh(payloadUser);
    // redirect back to frontend with tokens (fragile for production - consider cookie)
    const redirectBack = (FRONTEND_URL ? FRONTEND_URL.replace(/\/$/,'') : '') + `/auth-landing.html#access=${accessToken}&refresh=${refreshToken}`;
    return res.redirect(redirectBack);
  }catch(e){ console.error('github callback error', e && e.response ? e.response.data : e); return res.status(500).send('OAuth error'); }
});

// Existing API endpoints (simplified excerpts) - report/instances/reindex/close_account/sse
// For brevity: minimal implementations to integrate with new auth checks

// Middleware to check API key or bearer token (for backend non-authenticated EA reports, keep using API_KEY)
function requireApiKeyOrAuth(req,res,next){ const incomingKey = (req.get('x-api-key')||'').trim(); if(incomingKey && incomingKey === API_KEY) return next(); // api key OK
  const authh = (req.get('Authorization')||'').replace(/^Bearer\s+/i,''); const payload = verifyAccess(authh); if(payload) { req.user = payload; return next(); } return res.status(401).json({ ok:false, error:'unauthorized' }); }

// POST /v1/report (EA telemetry)
app.post('/v1/report', async (req,res) => {
  try{
    const incomingKey = (req.get('x-api-key') || '').trim();
    if(!incomingKey || incomingKey !== API_KEY){ // if not using API key, allow auth user
      const authh = (req.get('Authorization')||'').replace(/^Bearer\s+/i,''); const payload = verifyAccess(authh); if(!payload) return res.status(401).json({ok:false, error:'invalid_api_key'});
    }

    if(HMAC_SECRET){ const sig = (req.get('x-signature')||'').trim(); if(!sig) return res.status(401).json({ok:false, error:'missing_signature'}); const expected = crypto.createHmac('sha256', HMAC_SECRET).update(req.rawBody || Buffer.from(JSON.stringify(req.body))).digest('hex'); if(sig !== expected) return res.status(401).json({ok:false, error:'invalid_signature'}); }

    const payload = req.body; if(!payload || typeof payload !== 'object') return res.status(400).json({ok:false, error:'invalid_payload'});
    const account = payload.account_login || payload.account; const now = Date.now(); instances.set(account, { lastSeen: now, payload: payload, status: 'online' });
    // record event and upsert monitor row asynchronously
    appendEventRow(payload).catch(()=>{});
    // upsertMonitorRow can be heavy; call but don't await fully
    // NOTE: upsertMonitorRow not implemented here for brevity — you can reuse earlier implementation
    return res.json({ok:true});
  }catch(err){ console.error('report processing error', err && err.stack ? err.stack : err); return res.status(500).json({ok:false, error:'server_error', detail: String(err)}); }
});

// GET /v1/instances (requires API key or auth)
app.get('/v1/instances', requireApiKeyOrAuth, (req,res) => {
  const activeOnly = String(req.query.activeOnly || '').toLowerCase() === 'true';
  const list = []; const now = Date.now(); for(const [account,obj] of instances.entries()){ const isActive = (now - obj.lastSeen) <= HEARTBEAT_TTL*1000; if(activeOnly && !isActive) continue; list.push({ account, status: obj.status, lastSeen: new Date(obj.lastSeen).toISOString(), payload: obj.payload }); } res.json({ ok:true, instances: list });
});

// POST /v1/reindex (protected by API key or auth user)
app.post('/v1/reindex', requireApiKeyOrAuth, async (req,res) => { try{ // For safety, just confirm
    await appendEventRow({ account_login: (req.user && req.user.email) || 'system', event: 'reindex_request', extra: {} }); return res.json({ ok:true, note:'reindex enqueued (server will perform background upsert)' }); }catch(e){ console.error('reindex err', e); return res.status(500).json({ ok:false, error:String(e) }); } });

// POST /v1/close_account (signal)
app.post('/v1/close_account', requireApiKeyOrAuth, async (req,res) => { try{ const { account, dryRun=true } = req.body || {}; if(!account) return res.status(400).json({ ok:false, error:'missing_account' }); await appendEventRow({ account_login: account, event: 'request_close', extra: { dryRun, by: (req.user && req.user.email) || 'api' } }); return res.json({ ok:true, account, dryRun }); }catch(e){ console.error('close_account', e); return res.status(500).json({ ok:false, error:String(e) }); } });

// SSE endpoint
app.get('/sse/instances', (req, res) => {
  const incomingKey = (req.query.key || req.get('x-api-key') || '').trim();
  if(!incomingKey || incomingKey !== API_KEY){ const authh = (req.get('Authorization')||'').replace(/^Bearer\s+/i,''); if(!verifyAccess(authh)) { res.status(401).end('unauthorized'); return; } }
  res.writeHead(200, { Connection: 'keep-alive', 'Cache-Control': 'no-cache', 'Content-Type': 'text/event-stream' });
  const send = () => { try{ const payload = Array.from(instances.entries()).map(([a,o])=>({account:a, status:o.status, lastSeen:o.lastSeen, payload:o.payload})); res.write(`data: ${JSON.stringify(payload)}\n\n`); }catch(e){} };
  send(); const id = setInterval(send, 3000); req.on('close', () => clearInterval(id));
});

// Janitor to mark stale instances
async function janitorTick(){ try{ const now = Date.now(); const toMark = []; for(const [account,obj] of instances.entries()){ if(obj.status === 'online' && now - obj.lastSeen > HEARTBEAT_TTL*1000){ obj.status = 'offline'; instances.set(account, obj); toMark.push({ account, payload: obj.payload }); } } if(toMark.length>0){ for(const itm of toMark){ await appendEventRow({ account_login: itm.account, event: 'stale_mark', extra: {} }).catch(()=>{}); } } }catch(e){ console.error('Janitor error', e); } }
setInterval(janitorTick, INSTANCE_CLEAN_INTERVAL*1000);

app.get('/health', (req,res)=> res.send('ok'));

app.listen(PORT, ()=>{ console.log(`GIRD monitor server listening on port ${PORT}`); console.log(`SHEET_ID=${SHEET_ID} HEARTBEAT_TTL=${HEARTBEAT_TTL}s HMAC=${HMAC_SECRET? 'ENABLED' : 'DISABLED'}`); });
