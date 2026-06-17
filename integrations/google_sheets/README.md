# Google Sheets integration for GIRD EA

This folder contains a Google Apps Script Web App and example MQL5 helper code to connect the GIRD EA to Google Sheets.

Files included:
- Code.gs: Google Apps Script that accepts POST JSON and appends rows to a spreadsheet.
- README.md: this file (instructions).
- mql/GIRD_GoogleSheets.mq5: MQL5 helper functions (send payload to Apps Script) and integration notes.

Important notes before using
1. This approach uses a Google Apps Script Web App as a simple HTTP endpoint that writes to a Google Sheet. It's the easiest method because the EA (running inside MetaTrader) cannot easily perform OAuth flows.
2. Security: the script includes a simple API key check. Use a strong random key and do NOT deploy the web app as "Anyone, even anonymous" unless you are comfortable with public access. For better security, deploy the script to run as "Me" and limit access to users in your Google Workspace, or implement additional verification.
3. MetaTrader configuration: you must add the Apps Script URL to Tools -> Options -> Expert Advisors -> Allow WebRequest for listed URL(s).

Deployment steps
1. Create a Google Sheet (or use an existing one). Note the sheet name (default: Sheet1).
2. In the Google Sheet, open Extensions -> Apps Script.
3. Replace the default Code.gs with the content of integrations/google_sheets/Code.gs in this repo.
4. Edit the constants at the top of Code.gs:
   - SHEET_NAME = 'Sheet1' (or your sheet name)
   - API_KEY = 'REPLACE_WITH_A_SECRET_KEY' (generate a random string and keep it secret)
5. Save the script. Then Deploy -> New deployment -> Select "Web app". Set:
   - Execute as: Me
   - Who has access: Anyone (or Anyone with Google account, depending on security)
6. Copy the Web App URL (looks like https://script.google.com/macros/s/XXXXX/exec).
7. In MetaTrader: Tools -> Options -> Expert Advisors -> check "Allow WebRequest for listed URL" and add the origin domain(s), e.g. https://script.google.com
   Additionally, add the full URL to the allowed list if required by your broker/MT.
8. Update the EA settings: provide the Web App URL and the API key. See example in mql/GIRD_GoogleSheets.mq5.

Troubleshooting
- If WebRequest returns an error, check that the URL is allowed in MT options and that the web app is deployed correctly. Look at the Apps Script Execution log (Apps Script editor -> Executions) for errors.
- For production use, consider deploying a small backend (e.g., Cloud Run or App Engine) that performs proper authentication and forwards to Google Sheets via the Sheets API.

