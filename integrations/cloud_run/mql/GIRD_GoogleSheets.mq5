//+------------------------------------------------------------------+
// GIRD_GoogleSheets.mq5 (UPDATED)
// MQL5 helper for Cloud Run / Google Sheets integration
// Place this file in Include/ or copy functions into your EA
//+------------------------------------------------------------------+

#property version "1.10"

// Configure these values (replace with your Cloud Run URL and API key)
string GGS_CLOUD_URL = "https://REPLACE_WITH_CLOUD_RUN_URL"; // e.g. https://gird-cloudrun-xxxxx-uc.a.run.app
string GGS_API_KEY   = "REPLACE_WITH_API_KEY";

// Heartbeat control
int GGS_HEARTBEAT_INTERVAL = 60; // seconds
datetime ggs_last_heartbeat = 0;

// Utility: convert payload string to uchar buffer
void StringToUcharBuffer(const string s, uchar &buf[], int &len){
   len = StringToCharArray(s, buf);
   if(len>0 && buf[len-1]==0) ArrayResize(buf, len-1);
}

bool GGS_SendPayloadCloudRun(string jsonPayload, int timeout_ms = 10000) {
   if(StringLen(GGS_CLOUD_URL) == 0) return false;
   string url = GGS_CLOUD_URL + "/v1/report";
   string headers = "Content-Type: application/json\r\n";
   headers += "x-api-key: " + GGS_API_KEY + "\r\n";

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

// convenience wrapper: send summary
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

string buildPositionsJson(uint magicNumber, string symbol) {
   string arr = "[";
   int first = 1;
   for(int i=0;i<PositionsTotal();i++){
      ulong t = PositionGetTicket(i);
      if(t>0 && PositionSelectByTicket(t)){
         if(PositionGetInteger(POSITION_MAGIC) == (long)magicNumber && PositionGetString(POSITION_SYMBOL) == symbol){
            if(!first) arr += ",";
            arr += "{";
            arr += "\"ticket\":\"" + (string)t + "\",";
            arr += "\"type\":" + IntegerToString((int)PositionGetInteger(POSITION_TYPE)) + ",";
            arr += "\"volume\":" + DoubleToString(PositionGetDouble(POSITION_VOLUME),2) + ",";
            arr += "\"profit\":" + DoubleToString(PositionGetDouble(POSITION_PROFIT),2);
            arr += "}";
            first = 0;
         }
      }
   }
   arr += "]";
   return arr;
}

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
   payload += "\"open_positions_json\":" + buildPositionsJson(MAGIC_NUMBER, symbol);
   payload += "}";

   return GGS_SendPayloadCloudRun(payload);
}

// helper to send events (start/stop/close_all)
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

// Integration note: call GGS_SendEvent("start", _Symbol) in OnInit, GGS_SendHeartbeat(MAGIC_NUMBER,_Symbol) in OnTimer or UpdateDashboardData, GGS_SendEvent("stop", _Symbol) in OnDeinit, and GGS_SendEvent("close_all", _Symbol) after ExecuteCloseAllStrategy.
