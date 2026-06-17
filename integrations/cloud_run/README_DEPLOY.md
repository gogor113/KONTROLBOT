# GIRD Cloud Run Deployment Steps (ready-to-run commands)

This script contains the commands you can run in your terminal to deploy the
Cloud Run service. Replace placeholders (PROJECT_ID, REGION, SHEET_ID, API_KEY)
with real values.

Set project and region:
  gcloud config set project PROJECT_ID
  gcloud config set run/region REGION

Enable APIs:
  gcloud services enable run.googleapis.com cloudbuild.googleapis.com secretmanager.googleapis.com sheets.googleapis.com iam.googleapis.com

Create service account:
  gcloud iam service-accounts create gird-svc --display-name="GIRD Sheets Writer"
  SA_EMAIL="gird-svc@PROJECT_ID.iam.gserviceaccount.com"

Build and push container:
  IMAGE="gcr.io/${PROJECT_ID}/gird-cloudrun"
  gcloud builds submit --tag ${IMAGE} .

Deploy Cloud Run (initial):
  gcloud run deploy gird-cloudrun \
    --image ${IMAGE} \
    --platform managed \
    --region ${REGION} \
    --service-account ${SA_EMAIL} \
    --allow-unauthenticated \
    --set-env-vars "SHEET_ID=REPLACE_WITH_SHEET_ID,API_KEY=REPLACE_WITH_API_KEY"

Share spreadsheet: open Google Sheet -> Share -> add ${SA_EMAIL} as Editor.

Test with curl:
  SERVICE_URL=$(gcloud run services describe gird-cloudrun --region ${REGION} --format="value(status.url)")
  curl -i -X POST "${SERVICE_URL}/v1/report" \
    -H "Content-Type: application/json" \
    -H "x-api-key: REPLACE_WITH_API_KEY" \
    -d '{"event":"test","account_login":"12345","balance":1000.5,"equity":995.0,"symbol":"EURUSD"}'

Notes:
- For production, store API_KEY in Secret Manager and update Cloud Run to use secret.
- Share the spreadsheet with the service account to allow writes.
