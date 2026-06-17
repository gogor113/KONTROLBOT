/**
 * Google Apps Script Web App Receiver
 * Deploy this script as a Web App (Execute as: Me, Who has access: Anyone)
 * It accepts POST requests with JSON payload and an optional apiKey parameter
 * and writes rows to a Google Sheet.
 *
 * Usage: POST JSON to the deployed Web App URL. Example payload:
 * {"event":"update","symbol":"EURUSD","balance":1000.5,"equity":995.0}
 *
 * For basic protection supply a query parameter `?key=YOUR_API_KEY` when calling.
 */

const SHEET_NAME = 'Sheet1'; // change to your sheet name if needed
const API_KEY = 'REPLACE_WITH_A_SECRET_KEY'; // change to a random secret key

function doPost(e) {
  try {
    // check for api key (either query param or header X-API-KEY)
    var key = '';
    if (e.parameter && e.parameter.key) key = e.parameter.key;
    var headers = e && e.postData && e.postData.type && e.postData.type.toLowerCase() === 'application/json' ? null : null;
    if (!key && e && e.postData && e.postData.contents) {
      // optionally the client can include {"key":"...", ...} inside JSON
      try {
        var tmp = JSON.parse(e.postData.contents);
        if (tmp && tmp.key) key = tmp.key;
      } catch (err) {}
    }

    if (API_KEY && API_KEY !== '' && key !== API_KEY) {
      return ContentService
        .createTextOutput(JSON.stringify({ok:false, error:'invalid_api_key'}))
        .setMimeType(ContentService.MimeType.JSON);
    }

    var payload = {};
    if (e.postData && e.postData.contents) {
      payload = JSON.parse(e.postData.contents);
    } else {
      return ContentService
        .createTextOutput(JSON.stringify({ok:false, error:'no_payload'}))
        .setMimeType(ContentService.MimeType.JSON);
    }

    var ss = SpreadsheetApp.getActiveSpreadsheet();
    if (!ss) throw 'No active spreadsheet. Make sure you linked the script to the spreadsheet.';
    var sheet = ss.getSheetByName(SHEET_NAME) || ss.getSheets()[0];

    // Build a row: timestamp + all keys of payload in stable order
    var ts = new Date();
    var keys = Object.keys(payload);
    // Ensure consistent column order: timestamp, event, symbol, balance, equity, extra_json
    var row = [];
    row.push(ts.toISOString());
    row.push(payload.event || '');
    row.push(payload.symbol || '');
    row.push(payload.balance !== undefined ? payload.balance : '');
    row.push(payload.equity !== undefined ? payload.equity : '');

    // Put any remaining data as JSON in the last column
    var extra = {};
    for (var i = 0; i < keys.length; i++) {
      var k = keys[i];
      if (['event','symbol','balance','equity','key'].indexOf(k) === -1) {
        extra[k] = payload[k];
      }
    }
    row.push(JSON.stringify(extra));

    sheet.appendRow(row);

    return ContentService
      .createTextOutput(JSON.stringify({ok:true}))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService
      .createTextOutput(JSON.stringify({ok:false, error: err.toString()}))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
