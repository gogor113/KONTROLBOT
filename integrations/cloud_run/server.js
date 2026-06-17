// server.js
const express = require('express');
const bodyParser = require('body-parser');
const {google} = require('googleapis');

const app = express();
app.use(bodyParser.json({limit: '128kb'}));

const SHEET_ID = process.env.SHEET_ID || 'REPLACE_WITH_SHEET_ID';
const API_KEY = process.env.API_KEY || 'REPLACE_WITH_API_KEY';
const PORT = process.env.PORT || 8080;

const auth = new google.auth.GoogleAuth({
  scopes: ['https://www.googleapis.com/auth/spreadsheets']
});

async function getSheetsClient(){
  const client = await auth.getClient();
  return google.sheets({version: 'v4', auth: client});
}

function safeString(v){ return (v === undefined || v === null) ? '' : String(v); }

async function ensureHeader(sheets){
  const readRes = await sheets.spreadsheets.values.get({ spreadsheetId: SHEET_ID, range: 'A1:Z1' });
  const values = readRes.data.values || [];
  if(values.length === 0 || values[0].length === 0){
    const header = ['timestamp','account_login','account_name','broker','server','terminal_id','symbol','balance','equity','free_margin','positions_count','open_positions_json','last_event','notes'];
    await sheets.spreadsheets.values.update({ spreadsheetId: SHEET_ID, range: 'A1', valueInputOption: 'RAW', requestBody:{ values:[header] }});
    return header;
  }
  return values[0];
}

async function upsertRow(sheets, payload){
  const readRes = await sheets.spreadsheets.values.get({ spreadsheetId: SHEET_ID, range: 'A1:Z1000' });
  const values = readRes.data.values || [];
  const header = await ensureHeader(sheets);
  const accountColIndex = header.indexOf('account_login');
  // find existing row
  let foundRow = -1;
  for(let r = 1; r < values.length; r++){
    if(values[r][accountColIndex] && String(values[r][accountColIndex]) === String(payload.account_login)){
      foundRow = r + 1;
      break;
    }
  }

  const rowObj = {
    timestamp: new Date().toISOString(),
    account_login: safeString(payload.account_login),
    account_name: safeString(payload.account_name),
    broker: safeString(payload.broker),
    server: safeString(payload.server),
    terminal_id: safeString(payload.terminal_id),
    symbol: safeString(payload.symbol),
    balance: payload.balance !== undefined ? payload.balance : '',
    equity: payload.equity !== undefined ? payload.equity : '',
    free_margin: payload.free_margin !== undefined ? payload.free_margin : '',
    positions_count: payload.positions_count !== undefined ? payload.positions_count : '',
    open_positions_json: payload.open_positions_json ? JSON.stringify(payload.open_positions_json) : '',
    last_event: safeString(payload.event),
    notes: payload.notes ? JSON.stringify(payload.notes) : ''
  };

  const out = header.map(h => rowObj[h] !== undefined ? rowObj[h] : '');
  if(foundRow > 0){
    const endCol = String.fromCharCode(65 + out.length - 1);
    const range = `A${foundRow}:${endCol}${foundRow}`;
    await sheets.spreadsheets.values.update({ spreadsheetId: SHEET_ID, range, valueInputOption: 'RAW', requestBody:{ values: [out] }});
    return {updated: true, row: foundRow};
  } else {
    await sheets.spreadsheets.values.append({ spreadsheetId: SHEET_ID, range: 'A1', valueInputOption: 'RAW', insertDataOption: 'INSERT_ROWS', requestBody:{ values:[out] }});
    return {appended: true};
  }
}

app.post('/v1/report', async (req, res) => {
  try {
    const key = (req.get('x-api-key') || '').trim();
    if(!API_KEY || key !== API_KEY){
      return res.status(401).json({ok:false, error:'invalid_api_key'});
    }
    const payload = req.body;
    if(!payload) return res.status(400).json({ok:false, error:'missing_payload'});
    const sheets = await getSheetsClient();
    // simple upsert with retry for transient rate limits
    let attempt = 0; let lastErr = null;
    while(attempt < 3){
      try {
        const result = await upsertRow(sheets, payload);
        return res.json({ok:true, result});
      } catch(e){
        lastErr = e;
        const msg = (e && e.errors && e.errors[0] && e.errors[0].reason) || e.message || String(e);
        if(msg.indexOf('rateLimitExceeded') >= 0 || msg.indexOf('userRateLimitExceeded') >= 0){
          await new Promise(r => setTimeout(r, 500 * (attempt+1)));
          attempt++;
          continue;
        }
        break;
      }
    }
    throw lastErr || new Error('failed_to_write_sheet');
  } catch(err){
    console.error('report error', err);
    return res.status(500).json({ok:false, error: (err.message||String(err))});
  }
});

app.get('/health', (req,res)=> res.send('ok'));

app.listen(PORT, ()=> console.log('Server listening on', PORT));
