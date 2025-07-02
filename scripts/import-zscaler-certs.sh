#!/usr/bin/env sh
set -e
echo "importing zscaler certs";
# This script imports Zscaler root and intermediate certificates into the system's trust store.

echo | \
    openssl s_client -connect zscaler.com:443 -showcerts 2>/dev/null | \
    awk -v folder="${CERTS_FOLDER}" '/-----BEGIN CERTIFICATE-----/ {in_cert=1; i++; fname=sprintf("%s/zscaler%d.crt", folder, i)} in_cert {print > fname} /-----END CERTIFICATE-----/ {in_cert=0}'

for cert in "$CERTS_FOLDER"/zscaler*.crt; do
  alias="zscaler-cert-$(basename "$cert" .crt)";
  keytool -importcert -noprompt -trustcacerts -alias "$alias" -file "$cert" -keystore "$CACERTS_PATH" -storepass "$CERT_PASS";
done
