#!/bin/bash
# Termiti lobby server – instalační skript pro Ubuntu 22.04+
# Spusť z adresáře s git repem: sudo bash server/setup.sh
# nebo přímo ze server/ složky:  sudo bash setup.sh

set -e

PORT=8765
APP_DIR="/opt/termiti-server"
SERVICE="termiti-lobby"
NODE_MIN=18

# Adresář, kde leží tento skript (= zdrojové soubory)
SRC="$(cd "$(dirname "$0")" && pwd)"

echo "=== Termiti Lobby Server – Instalace ==="
echo "    Zdroj: $SRC"
echo "    Cíl:   $APP_DIR"

# ── Node.js ───────────────────────────────────────────────────────────────────
NODE_VER=$(node --version 2>/dev/null | tr -d 'v' | cut -d. -f1 || echo 0)
if ! command -v node &>/dev/null || [ "$NODE_VER" -lt "$NODE_MIN" ]; then
  echo "[1/5] Instaluji Node.js $NODE_MIN..."
  curl -fsSL https://deb.nodesource.com/setup_${NODE_MIN}.x | bash -
  apt-get install -y nodejs
else
  echo "[1/5] Node.js $(node --version) OK"
fi

# ── Adresář aplikace ──────────────────────────────────────────────────────────
echo "[2/5] Kopíruji soubory do $APP_DIR..."
mkdir -p "$APP_DIR/game"
mkdir -p "$APP_DIR/data"
mkdir -p "$APP_DIR/logs"
mkdir -p "$APP_DIR/logs/crash_logs"
mkdir -p "$APP_DIR/art"

# Kopíruj jen pokud zdroj != cíl (ochrana před spuštěním přímo z APP_DIR)
copy_if_different() {
  local src="$1" dst="$2"
  if [ "$(realpath "$src" 2>/dev/null)" != "$(realpath "$dst" 2>/dev/null)" ]; then
    cp "$src" "$dst"
  else
    echo "      (přeskakuji $dst – stejný soubor)"
  fi
}

copy_if_different "$SRC/server.js"            "$APP_DIR/server.js"
copy_if_different "$SRC/package.json"         "$APP_DIR/package.json"
copy_if_different "$SRC/game/cards.js"        "$APP_DIR/game/cards.js"
copy_if_different "$SRC/game/engine.js"       "$APP_DIR/game/engine.js"
copy_if_different "$SRC/game/GameSession.js"  "$APP_DIR/game/GameSession.js"
copy_if_different "$SRC/game/RatingSystem.js" "$APP_DIR/game/RatingSystem.js"
copy_if_different "$SRC/game/GameLogger.js"   "$APP_DIR/game/GameLogger.js"
copy_if_different "$SRC/game/ReplayViewer.js" "$APP_DIR/game/ReplayViewer.js"
copy_if_different "$SRC/game/card_data.json"  "$APP_DIR/game/card_data.json"

# Art thumbnails (WebP, 80×112px)
if [ -d "$SRC/art" ]; then
  cp "$SRC/art/"*.webp "$APP_DIR/art/" 2>/dev/null || true
  echo "      Zkopírováno $(ls "$APP_DIR/art/"*.webp 2>/dev/null | wc -l) art souborů."
fi

# Oprávnění pro data/ a logs/ – nobody musí moci zapisovat
chown -R nobody:nogroup "$APP_DIR/data"
chmod 755 "$APP_DIR/data"
chown -R nobody:nogroup "$APP_DIR/logs"
chmod -R 755 "$APP_DIR/logs"

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
SupplementaryGroups=nogroup
ReadWritePaths=$APP_DIR/data $APP_DIR/logs
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
