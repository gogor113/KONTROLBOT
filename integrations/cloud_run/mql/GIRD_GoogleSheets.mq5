//+------------------------------------------------------------------+
// GIRD_GoogleSheets.mq5 (UPDATED headers)
// MQL5 helper for Cloud Run / Google Sheets integration
// This version sends x-api-key header and supports placing x-signature (HMAC) if you compute it externally.
// Note: MQL5 doesn't have built-in HMAC-SHA256 utility in a standard lib — compute signature externally or ask me to implement a pure-MQL HMAC routine.
//+------------------------------------------------------------------+

#property version "1.11"

// Configure these values (replace with your Cloud Run URL or Apps Script URL and API key)
string GGS_CLOUD_URL = "https://script.google.com/macros/s/AKfycbwp_1VTNmJFdXmPrENXL8AC2zijTJbIvSCmubeRBQjHQj1lFd_xsidD6v8Okm89SD_ODg/exec"; // default points to your Apps Script
string GGS_API_KEY   = "REPLACE_WITH_API_KEY";
string GGS_SIGNATURE = ""; // optional: precomputed HMAC-SHA256 hex signature of the payload if server expects x-signature

// Heartbeat control
int GGS_HEARTBEAT_INTERVAL = 60; // seconds
datetime ggs_last_heartbeat = 0;

void StringToUcharBuffer(const string s, uchar &buf[], int &len){
   len = StringToCharArray(s, buf);
   if(len>0 && buf[len-1]==0) ArrayResize(buf, len-1);
}

bool GGS_SendPayloadCloudRun(string jsonPayload, int timeout_ms = 10000) {
   if(StringLen(GGS_CLOUD_URL) == 0) return false;
   string url = GGS_CLOUD_URL + "/v1/report";
   string headers = "Content-Type: application/json\r\n";
   headers += "x-api-key: " + GGS_API_KEY + "\r\n";
   if(StringLen(GGS_SIGNATURE) > 0) headers += "x-signature: " + GGS_SIGNATURE + "\r\n"; // optional

   uchar postData[]; int len = 0;
   StringToUcharBuffer(jsonPayload, postData, len);

   uchar result[];
   string result_headers;
   int res = WebRequest("POST", url, headers, timeout_ms, postData, ArraySize(postData), result, result_headers);
   if(res == -1) {
      PrintFormat("GGS: WebRequest failed. Error=%d", GetLastError());
      ResetLastError();
      return false;
   }
   string resp = "";
   if(ArraySize(result) > 0) {
      for(int i=0;i<ArraySize(result);i++) resp += (char)result[i];
      PrintFormat("GGS: Response (%d): %s", res, resp);
   }
   return (res >= 200 && res < 300);
}

bool GGS_SendSummaryCloud(string eventType, string symbol, double balance, double equity) {
   string json = "{";
   json += "\"event\":\"" + eventType + "\",";
   json += "\"account_login\":\"" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + "\",";
   json += "\"account_name\":\"" + AccountInfoString(ACCOUNT_NAME) + "\",";
   json += "\"broker\":\"" + AccountInfoString(ACCOUNT_COMPANY) + "\",";
   json += "\"server\":\"" + AccountInfoString(ACCOUNT_SERVER) + "\",";
   json += "\"terminal_id\":\"" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + "@" + AccountInfoString(ACCOUNT_SERVER) + "\",";
   json += "\"symbol\":\"" + symbol + "\",";
   json += "\"balance\":" + DoubleToString(balance,2) + ",";
   json += "\"equity\":" + DoubleToString(equity,2) + ",";
   json += "\"positions_count\":" + IntegerToString(PositionsTotal());
   json += "}";
   return GGS_SendPayloadCloudRun(json);
}

// If you want to include positions array, implement a serializer and set open_positions_json field in JSON payload

bool GGS_SendHeartbeat(uint magicNumber, string symbol) {
   datetime now = TimeCurrent();
   if((int)(now - ggs_last_heartbeat) < GGS_HEARTBEAT_INTERVAL) return true; // throttle
   ggs_last_heartbeat = now;

   string payload = "{";
   payload += "\"event\":\"heartbeat\",";
   payload += "\"account_login\":\"" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + "\",";
   payload += "\"account_name\":\"" + AccountInfoString(ACCOUNT_NAME) + "\",";
   payload += "\"broker\":\"" + AccountInfoString(ACCOUNT_COMPANY) + "\",";
   payload += "\"server\":\"" + AccountInfoString(ACCOUNT_SERVER) + "\",";
   payload += "\"terminal_id\":\"" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + "@" + AccountInfoString(ACCOUNT_SERVER) + "\",";
   payload += "\"symbol\":\"" + symbol + "\",";
   payload += "\"balance\":" + DoubleToString(AccountInfoDouble(ACCOUNT_BALANCE),2) + ",";
   payload += "\"equity\":" + DoubleToString(AccountInfoDouble(ACCOUNT_EQUITY),2) + ",";
   payload += "\"free_margin\":" + DoubleToString(AccountInfoDouble(ACCOUNT_MARGIN_FREE),2) + ",";
   payload += "\"positions_count\":" + IntegerToString(CountPositions()) + ",";
   payload += "\"open_positions_json\":[]"; // implement positions JSON if desired
   payload += "}";

   return GGS_SendPayloadCloudRun(payload);
}

bool GGS_SendEvent(string eventType, string symbol) {
   string json = "{";
   json += "\"event\":\"" + eventType + "\",";
   json += "\"account_login\":\"" + IntegerToString(AccountInfoInteger(ACCOUNT_LOGIN)) + "\",";
   json += "\"symbol\":\"" + symbol + "\",";
   json += "\"balance\":" + DoubleToString(AccountInfoDouble(ACCOUNT_BALANCE),2) + ",";
   json += "\"equity\":" + DoubleToString(AccountInfoDouble(ACCOUNT_EQUITY),2);
   json += "}";
   return GGS_SendPayloadCloudRun(json);
}

// Example: compute HMAC signature in external script (Python) and paste into GGS_SIGNATURE before calling
// Python example:
// import hmac, hashlib
// def hmac_hex(secret, payload_bytes):
//     return hmac.new(secret.encode(), payload_bytes, hashlib.sha256).hexdigest()

// Integration note: call GGS_SendEvent("start", _Symbol) in OnInit, GGS_SendHeartbeat(MAGIC_NUMBER,_Symbol) in OnTimer or UpdateDashboardData, GGS_SendEvent("stop", _Symbol) in OnDeinit, and GGS_SendEvent("close_all", _Symbol) after ExecuteCloseAllStrategy.
