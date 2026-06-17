# GIRD Cloud Run - PR / Merge instructions

This file contains instructions to create a pull request merging feat/cloud-run into main.

From your local clone:

# fetch branches and checkout
git fetch origin
git checkout feat/cloud-run

git pull origin feat/cloud-run

# run tests / review files locally
# if satisfied, push branch (it's already pushed by the bot)
# create PR via GitHub UI or hub/gh CLI

gh pr create --base main --head feat/cloud-run --title "feat: Cloud Run Google Sheets integration" --body "Add Cloud Run backend, Dockerfile, MQL5 helper, and deployment docs."

# or use GitHub UI: go to repo -> Pull requests -> New pull request -> select branch feat/cloud-run -> Create pull request.
