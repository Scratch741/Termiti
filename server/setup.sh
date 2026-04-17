#!/bin/bash
# Termiti lobby server – instalační skript pro Ubuntu 22.04+
# Spusť jako root nebo s sudo: sudo bash setup.sh

set -e

PORT=8765
APP_DIR="/opt/termiti-server"
SERVICE="termiti-lobby"
NODE_MIN=18

echo "=== Termiti Lobby Server – Instalace ==="

# ── Node.js ───────────────────────────────────────────────────────────────────
if ! command -v node &>/dev/null || [ "$(node -e 'process.stdout.write(process.version.slice(1).split(\".\")[0])')" -lt "$NODE_MIN" ]; then
  echo "[1/5] Instaluji Node.js $NODE_MIN..."
  curl -fsSL https://deb.nodesource.com/setup_${NODE_MIN}.x | bash -
  apt-get install -y nodejs
else
  echo "[1/5] Node.js $(node --version) OK"
fi

# ── Adresář aplikace ──────────────────────────────────────────────────────────
echo "[2/5] Kopíruji soubory do $APP_DIR..."
mkdir -p "$APP_DIR"
cp "$(dirname "$0")/server.js"     "$APP_DIR/server.js"
cp "$(dirname "$0")/package.json"  "$APP_DIR/package.json"

cd "$APP_DIR"
echo "[3/5] Instaluji npm závislosti..."
npm install --omit=dev

# ── Firewall ──────────────────────────────────────────────────────────────────
echo "[4/5] Otvírám port $PORT v UFW..."
if command -v ufw &>/dev/null; then
  ufw allow $PORT/tcp
  echo "      Port $PORT otevřen."
else
  echo "      UFW nenalezeno – port otevři ručně."
fi

# ── systemd služba ────────────────────────────────────────────────────────────
echo "[5/5] Vytvářím systemd službu '$SERVICE'..."
cat > "/etc/systemd/system/${SERVICE}.service" <<EOF
[Unit]
Description=Termiti Online Lobby Server
After=network.target

[Service]
Type=simple
User=nobody
WorkingDirectory=$APP_DIR
ExecStart=$(which node) $APP_DIR/server.js
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal
SyslogIdentifier=$SERVICE
Environment=NODE_ENV=production

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable "$SERVICE"
systemctl restart "$SERVICE"

echo ""
echo "=== Hotovo! ==="
echo "Status:  systemctl status $SERVICE"
echo "Logy:    journalctl -u $SERVICE -f"
echo "Server:  ws://$(hostname -I | awk '{print $1}'):$PORT/lobby"
