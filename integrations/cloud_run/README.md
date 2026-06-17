Cloud Run backend for GIRD EA -> Google Sheets

This folder contains a small Node/Express service that accepts POST /v1/report
and upserts a row in a Google Spreadsheet (by account_login). The service
is designed to run on Cloud Run and use the service account identity (recommended):
- Cloud Run service runs as a service account that has edit access to the sheet.
- API key is used to authenticate EA requests (x-api-key header).

Files:
- server.js        : Express server that writes to Sheets API
- package.json
- Dockerfile

Quick deploy (after you set PROJECT_ID and REGION):
1) Build & push image
   gcloud builds submit --tag gcr.io/PROJECT_ID/gird-cloudrun

2) Deploy Cloud Run (replace SHEET_ID & API_KEY placeholders after deploy or use --set-env-vars)
   gcloud run deploy gird-cloudrun \
     --image gcr.io/PROJECT_ID/gird-cloudrun \
     --platform managed \
     --region REGION \
     --service-account gird-svc@PROJECT_ID.iam.gserviceaccount.com \
     --allow-unauthenticated \
     --set-env-vars "SHEET_ID=REPLACE_WITH_SHEET_ID,API_KEY=REPLACE_WITH_API_KEY"

3) Give the service account Editor access to the Spreadsheet (Share the sheet with gird-svc@PROJECT_ID.iam.gserviceaccount.com)

4) Recommended: store API_KEY in Secret Manager and update Cloud Run to use secret instead of plaintext env var.

See repository root README or integrations/cloud_run/README_DEPLOY.md for full step-by-step commands.
