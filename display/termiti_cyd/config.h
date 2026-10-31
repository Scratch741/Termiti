#pragma once

#define FW_VERSION "1.0.0"

// ── Server ───────────────────────────────────────────────────────────────────
#define SERVER_BASE        "http://138.2.136.49:8765"
#define SERVER_LABEL       "138.2.136.49:8765"
#define STATUS_INTERVAL_MS       15000   // /status (online, fronta, hry)
#define LEADERBOARD_INTERVAL_MS  60000   // /leaderboard obou módů – server pustí 20 dotazů/min z jedné IP
#define LB_ROWS 7

// ── Chování displeje ─────────────────────────────────────────────────────────
#define AUTO_PAGE_MS        12000   // jak dlouho zůstane stránka při automatickém listování
#define AUTO_PAGE_PAUSE_MS  60000   // po dotyku se automatické listování na chvíli zastaví
#define PORTAL_SSID   "Termiti-Displej"
#define HOSTNAME      "termiti-displej"
#define TZ_INFO       "CET-1CEST,M3.5.0,M10.5.0/3"   // Europe/Prague

// ── Piny CYD (ESP32-2432S028R) ───────────────────────────────────────────────
// Displej je nastavený přes build flagy v build.ps1 (TFT_eSPI, HSPI).
#define PIN_BACKLIGHT 21
#define PIN_LED_R      4   // RGB LED je zapojená proti VCC → svítí při LOW
#define PIN_LED_G     16
#define PIN_LED_B     17

// Dotyk XPT2046 má vlastní SPI sběrnici
#define XPT_CLK   25
#define XPT_MISO  39
#define XPT_MOSI  32
#define XPT_CS    33
#define XPT_IRQ   36

// Surové hodnoty dotyku v rozích (kalibrace)
#define TOUCH_X_MIN  200
#define TOUCH_X_MAX 3700
#define TOUCH_Y_MIN  240
#define TOUCH_Y_MAX 3800

#define SCREEN_W 320
#define SCREEN_H 240
