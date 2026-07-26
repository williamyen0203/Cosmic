#!/usr/bin/env bash
#
# Pull the latest code and rebuild the server image.
#
# Runs `git pull` in this repo, then `docker compose build maplestory`.
# The maplestory image bakes the compiled jar in, so a rebuild is what
# picks up Java code changes (config.yaml / scripts / wz are bind-mounted
# and don't need a rebuild). After this finishes, run `docker compose up`
# to start the freshly built server.

set -euo pipefail

# Run from the repo root regardless of where the script is invoked from.
cd "$(dirname "$0")"

echo "==> Pulling latest changes..."
git pull

echo "==> Building the 'maplestory' image..."
docker compose build maplestory

echo "==> Done."
