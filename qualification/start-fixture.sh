#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p qualification/fixtures/tls
if [ ! -f qualification/fixtures/tls/server.crt ]; then
  openssl req -x509 -newkey rsa:2048 -nodes -days 7 -keyout qualification/fixtures/tls/server.key -out qualification/fixtures/tls/server.crt -subj '/CN=VIPTV local Android fixture' -addext 'subjectAltName=IP:10.0.2.2,IP:127.0.0.1,DNS:localhost' > qualification/fixtures/tls/generate.log 2>&1
  chmod 600 qualification/fixtures/tls/server.key
fi
exec node qualification/fixture-server.mjs "$@"
