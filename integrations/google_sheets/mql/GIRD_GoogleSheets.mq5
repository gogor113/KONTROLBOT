//+------------------------------------------------------------------+
// GIRD_GoogleSheets.mq5
// Helper functions to send JSON payloads from the EA to a Google Apps Script Web App
// Place this file in your MQL5 include folder or copy the helper functions into your EA
//+------------------------------------------------------------------+

#property version   "1.00"

// Configure these values (replace with your deployed web app URL and API key)
string GGS_SCRIPT_URL = "https://script.google.com/macros/s/YOUR_DEPLOY_ID/exec";
string GGS_API_KEY   = "REPLACE_WITH_A_SECRET_KEY";

// Send a generic JSON payload to Google Sheets web app via POST
bool GGS_SendPayload(string jsonPayload, int timeout_ms = 10000) {
   // Ensure the script URL is set
   if(StringLen(GGS_SCRIPT_URL) == 0) return false;

   // Attach API key inside JSON or as query param. We'll send as JSON field for now
   string fullPayload = jsonPayload;
   // If developer wants to ensure key is query param, use: string url = GGS_SCRIPT_URL + "?key=" + GGS_API_KEY;
   
   // Add key into payload if not present
   if(StringFind(jsonPayload, "\"key\"") < 0) {
      if(StringTrimLeft(jsonPayload, StringLen(jsonPayload)) == "") fullPayload = "{\"key\":\"" + GGS_API_KEY + "\"}";
      else {
         // Insert key into the object
         if(StringGetCharacter(jsonPayload,0) == '{') {
            // jsonPayload like {"a":1}
            fullPayload = "{\"key\":\"" + GGS_API_KEY + "\"," + StringSubstr(jsonPayload,1);
         }
      }
   }

   // Convert string to uchar buffer
   uchar postData[];
   int len = StringToCharArray(fullPayload, postData);
   // Remove trailing 0 if present
   if(len>0 && postData[len-1]==0) {
      ArrayResize(postData, len-1);
   }

   string headers = "Content-Type: application/json\r\n";
   uchar result[];
   string result_headers;

   int res = WebRequest("POST", GGS_SCRIPT_URL, headers, timeout_ms, postData, ArraySize(postData), result, result_headers);

   if(res == -1) {
      PrintFormat("GGS: WebRequest failed. Error = %d", GetLastError());
      ResetLastError();
      return false;
   }

   string resp = "";
   if(ArraySize(result) > 0) {
      // Convert result bytes to string
      for(int i=0;i<ArraySize(result);i++) resp += (char)result[i];
      PrintFormat("GGS: Response = %s", resp);
   }

   return (res >= 200 && res < 300);
}

// Example convenience wrapper: send summary row
bool GGS_SendSummary(string eventType, string symbol, double balance, double equity) {
   string json = "{";
   json += "\"event\":\"" + eventType + "\",";
   json += "\"symbol\":\"" + symbol + "\",";
   json += "\"balance\":" + DoubleToString(balance,2) + ",";
   json += "\"equity\":" + DoubleToString(equity,2);
   json += "}";
   return GGS_SendPayload(json);
}

// Integration note:
// - Add the web app URL to Tools->Options->Expert Advisors -> Allow WebRequest for listed URL(s)
// - Call GGS_SendSummary("update", _Symbol, AccountInfoDouble(ACCOUNT_BALANCE), AccountInfoDouble(ACCOUNT_EQUITY));
//   at points you want to log data (e.g., in UpdateDashboardData(), ExecuteCloseAllStrategy(), OnDeinit(), etc.)

