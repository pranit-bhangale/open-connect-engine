#!/bin/bash
set -e

SSL_DIR="/etc/nginx/ssl"
CERT_FILE="${SSL_DIR}/open-connect.crt"
KEY_FILE="${SSL_DIR}/open-connect.key"

mkdir -p "${SSL_DIR}"

# Check for alternative certificate naming conventions in ssl directory and standardize to open-connect
if [ ! -f "${CERT_FILE}" ] || [ ! -f "${KEY_FILE}" ]; then
    if [ -f "${SSL_DIR}/cert.pem" ] && [ -f "${SSL_DIR}/key.pem" ]; then
        echo "==> Standardizing cert.pem and key.pem to open-connect certificates"
        cp "${SSL_DIR}/cert.pem" "${CERT_FILE}"
        cp "${SSL_DIR}/key.pem" "${KEY_FILE}"
    elif [ -f "${SSL_DIR}/tls.crt" ] && [ -f "${SSL_DIR}/tls.key" ]; then
        echo "==> Standardizing tls.crt and tls.key to open-connect certificates"
        cp "${SSL_DIR}/tls.crt" "${CERT_FILE}"
        cp "${SSL_DIR}/tls.key" "${KEY_FILE}"
    elif [ -f "${SSL_DIR}/cert.crt" ] && [ -f "${SSL_DIR}/cert.key" ]; then
        echo "==> Standardizing cert.crt and cert.key to open-connect certificates"
        cp "${SSL_DIR}/cert.crt" "${CERT_FILE}"
        cp "${SSL_DIR}/cert.key" "${KEY_FILE}"
    fi
fi

# If SSL certificate or key is still missing, generate a self-signed fallback certificate
if [ ! -f "${CERT_FILE}" ] || [ ! -f "${KEY_FILE}" ]; then
    echo "=========================================================="
    echo " [Open-Connect Nginx] No SSL certificate found in ${SSL_DIR}"
    echo " Generating a self-signed fallback SSL certificate..."
    echo "=========================================================="
    openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
        -keyout "${KEY_FILE}" \
        -out "${CERT_FILE}" \
        -subj "/C=US/ST=State/L=City/O=OpenConnect/CN=localhost" \
        -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" 2>/dev/null
    echo " [Open-Connect Nginx] Fallback SSL certificate generated."
    echo "=========================================================="
else
    echo "=========================================================="
    echo " [Open-Connect Nginx] Using existing open-connect SSL certificate."
    echo "=========================================================="
fi

exec "$@"
