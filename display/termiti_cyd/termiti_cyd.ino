/*
 * Termiti – stolní displej pro CYD (ESP32-2432S028R).
 * Ukazuje, kolik lidí je online, kdo čeká ve frontě a žebříčky obou módů.
 *
 * Wi-Fi se nastavuje přes telefon: deska bez uložené sítě vytvoří AP „Termiti-Displej“.
 * Sestavení a nahrání: build.ps1, popis ve wiki/systems/desk-display.md.
 */
#include <SPI.h>
#include <TFT_eSPI.h>
#include <XPT2046_Touchscreen.h>
#include <WiFi.h>
#include <WiFiManager.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include "app.h"

static TFT_eSPI tft;
static SPIClass touchSpi(VSPI);
static XPT2046_Touchscreen ts(XPT_CS, XPT_IRQ);
static WiFiManager wm;
static Preferences prefs;

Settings g_settings;
volatile uint32_t g_lastTouchMs = 0;

static SemaphoreHandle_t dataMutex;
static ServerData shared;   // píše síťová úloha (jádro 0)
static ServerData shown;    // kopie pro UI (jádro 1)
static bool wifiReady = false;

// ── LVGL ↔ displej a dotyk ──────────────────────────────────────────────────

// Buffery jdou z heapu – statická DRAM se s Wi-Fi nevejde
static const size_t DRAW_BUF_BYTES = SCREEN_W * 24 * 2;

static void flushCb(lv_display_t *disp, const lv_area_t *area, uint8_t *px) {
  uint32_t w = lv_area_get_width(area);
  uint32_t h = lv_area_get_height(area);
  tft.startWrite();
  tft.setAddrWindow(area->x1, area->y1, w, h);
  tft.pushColors((uint16_t *)px, w * h, true);
  tft.endWrite();
  lv_display_flush_ready(disp);
}

static void touchCb(lv_indev_t *, lv_indev_data_t *data) {
  if (ts.tirqTouched() && ts.touched()) {
    TS_Point p = ts.getPoint();
    data->point.x = constrain(map(p.x, TOUCH_X_MIN, TOUCH_X_MAX, 0, SCREEN_W - 1), 0, SCREEN_W - 1);
    data->point.y = constrain(map(p.y, TOUCH_Y_MIN, TOUCH_Y_MAX, 0, SCREEN_H - 1), 0, SCREEN_H - 1);
    data->state = LV_INDEV_STATE_PRESSED;
    g_lastTouchMs = millis();
  } else {
    data->state = LV_INDEV_STATE_RELEASED;
  }
}

// ── Nastavení, podsvícení, LED ──────────────────────────────────────────────

void app_set_brightness(uint8_t pct) {
  g_settings.brightness = constrain(pct, 5, 100);
  ledcWrite(PIN_BACKLIGHT, g_settings.brightness * 255 / 100);
}

void app_save_settings() {
  prefs.putUChar("bright", g_settings.brightness);
  prefs.putBool("led", g_settings.led);
  prefs.putBool("auto", g_settings.autoPage);
}

void app_forget_wifi() {
  wm.resetSettings();
  delay(200);
  ESP.restart();
}

static void ledWrite(uint8_t r, uint8_t g, uint8_t b) {
  ledcWrite(PIN_LED_R, 255 - r);
  ledcWrite(PIN_LED_G, 255 - g);
  ledcWrite(PIN_LED_B, 255 - b);
}

/** Zelené „dýchání“, když někdo čeká na soupeře; krátké červené bliknutí, když server mlčí. */
static void ledUpdate() {
  uint32_t now = millis();
  if (!g_settings.led || !wifiReady) { ledWrite(0, 0, 0); return; }
  int queue = shown.queueNormal + shown.queueSuper;
  if (shown.statusOk && queue > 0) {
    float s = (sinf(now / 1000.0f * 2.0f * PI / 2.0f) + 1.0f) / 2.0f;   // perioda 2 s
    ledWrite(0, 4 + (uint8_t)(s * 60), 0);
  } else if (shown.serverError && now % 3000 < 80) {
    ledWrite(40, 0, 0);
  } else {
    ledWrite(0, 0, 0);
  }
}

// ── Síť ─────────────────────────────────────────────────────────────────────

static bool httpGetJson(const char *path, JsonDocument &doc, int &code) {
  HTTPClient http;
  http.setConnectTimeout(4000);
  http.setTimeout(5000);
  if (!http.begin(String(SERVER_BASE) + path)) { code = -1; return false; }
  code = http.GET();
  bool ok = code == 200 && !deserializeJson(doc, http.getString());
  http.end();
  return ok;
}

/** Zkopíruje jméno a případně ho ořízne na hranici UTF-8 znaku. */
static void copyName(char *dst, size_t size, const char *src) {
  size_t len = strlen(src);
  if (len >= size) {
    len = size - 1;
    while (len > 0 && (src[len] & 0xC0) == 0x80) len--;
  }
  memcpy(dst, src, len);
  dst[len] = '\0';
}

static void fetchStatus() {
  JsonDocument doc;
  int code;
  bool ok = httpGetJson("/status", doc, code);
  xSemaphoreTake(dataMutex, portMAX_DELAY);
  shared.statusOk = ok;
  if (ok) {
    shared.online       = doc["online"] | 0;
    shared.queueNormal  = doc["queueNormal"] | 0;
    shared.queueSuper   = doc["queueSuper"] | 0;
    shared.games        = doc["games"] | 0;
    shared.totalPlayers = doc["totalPlayers"] | -1;
    shared.lastOkMs = millis();
    shared.serverError = false;
  } else if (code != 404) {   // 404 = server ještě bez /status, jinak běží
    shared.serverError = true;
  }
  shared.version++;
  xSemaphoreGive(dataMutex);
}

static void fetchLeaderboard(int mode) {
  char path[64];
  snprintf(path, sizeof path, "/leaderboard?mode=%s&limit=%d", mode ? "super_random" : "normal", LB_ROWS);
  JsonDocument doc;
  int code;
  bool ok = httpGetJson(path, doc, code);
  xSemaphoreTake(dataMutex, portMAX_DELAY);
  if (ok) {
    JsonArray players = doc["players"];
    int n = 0;
    for (JsonObject p : players) {
      if (n >= LB_ROWS) break;
      LbRow &row = shared.lb[mode][n++];
      copyName(row.name, sizeof row.name, p["name"] | "?");
      row.rating = p["rating"] | 0;
      row.wins   = p["wins"] | 0;
      row.losses = p["losses"] | 0;
    }
    shared.lbCount[mode] = n;
    shared.lbTotal[mode] = doc["total"] | 0;
    shared.lbOk[mode] = true;
    shared.lastOkMs = millis();
    shared.serverError = false;
  } else {
    shared.serverError = true;   // stará data zůstanou na displeji
  }
  shared.version++;
  xSemaphoreGive(dataMutex);
}

static void netTask(void *) {
  uint32_t nextStatus = 0, nextLb = 0;
  for (;;) {
    if (WiFi.status() == WL_CONNECTED) {
      uint32_t now = millis();
      if ((int32_t)(now - nextStatus) >= 0) {
        nextStatus = now + STATUS_INTERVAL_MS;
        fetchStatus();
      }
      if ((int32_t)(now - nextLb) >= 0) {
        nextLb = now + LEADERBOARD_INTERVAL_MS;
        fetchLeaderboard(0);
        fetchLeaderboard(1);
      }
    }
    vTaskDelay(pdMS_TO_TICKS(250));
  }
}

static void onWifiConnected() {
  wifiReady = true;
  configTzTime(TZ_INFO, "pool.ntp.org", "time.google.com");
  Serial.printf("[wifi] připojeno: %s, IP %s\n", WiFi.SSID().c_str(), WiFi.localIP().toString().c_str());
  ui_show_main();
}

// ── Arduino ─────────────────────────────────────────────────────────────────

void setup() {
  Serial.begin(115200);
  Serial.println("\n[termiti] displej " FW_VERSION);

  prefs.begin("termiti", false);
  g_settings.brightness = prefs.getUChar("bright", g_settings.brightness);
  g_settings.led        = prefs.getBool("led", g_settings.led);
  g_settings.autoPage   = prefs.getBool("auto", g_settings.autoPage);

  ledcAttach(PIN_LED_R, 5000, 8);
  ledcAttach(PIN_LED_G, 5000, 8);
  ledcAttach(PIN_LED_B, 5000, 8);
  ledWrite(0, 0, 0);

  tft.init();
  tft.setRotation(1);
  tft.fillScreen(TFT_BLACK);
  ledcAttach(PIN_BACKLIGHT, 5000, 8);
  app_set_brightness(g_settings.brightness);

  touchSpi.begin(XPT_CLK, XPT_MISO, XPT_MOSI, XPT_CS);
  ts.begin(touchSpi);
  ts.setRotation(1);

  lv_init();
  lv_tick_set_cb([]() -> uint32_t { return millis(); });
  lv_display_t *disp = lv_display_create(SCREEN_W, SCREEN_H);
  lv_display_set_flush_cb(disp, flushCb);
  void *buf1 = heap_caps_malloc(DRAW_BUF_BYTES, MALLOC_CAP_DMA | MALLOC_CAP_INTERNAL);
  void *buf2 = heap_caps_malloc(DRAW_BUF_BYTES, MALLOC_CAP_DMA | MALLOC_CAP_INTERNAL);
  lv_display_set_buffers(disp, buf1, buf2, DRAW_BUF_BYTES, LV_DISPLAY_RENDER_MODE_PARTIAL);
  lv_indev_t *indev = lv_indev_create();
  lv_indev_set_type(indev, LV_INDEV_TYPE_POINTER);
  lv_indev_set_read_cb(indev, touchCb);

  ui_init();
  ui_show_connecting();
  for (int i = 0; i < 10; i++) { lv_timer_handler(); delay(5); }

  dataMutex = xSemaphoreCreateMutex();
  xTaskCreatePinnedToCore(netTask, "net", 8192, nullptr, 1, nullptr, 0);

  WiFi.mode(WIFI_STA);
  wm.setConfigPortalBlocking(false);
  wm.setConnectTimeout(20);
  wm.setHostname(HOSTNAME);
  wm.setTitle("Termiti displej");
  wm.setDarkMode(true);
  if (wm.autoConnect(PORTAL_SSID)) {
    onWifiConnected();
  } else {
    Serial.println("[wifi] čekám na nastavení přes AP " PORTAL_SSID);
    ui_show_portal();
  }
}

void loop() {
  static uint32_t lastTick = 0, lastLed = 0;
  uint32_t now = millis();

  if (!wifiReady && wm.process()) onWifiConnected();

  bool changed = false;
  if (xSemaphoreTake(dataMutex, 0) == pdTRUE) {
    if (shared.version != shown.version) { shown = shared; changed = true; }
    xSemaphoreGive(dataMutex);
  }
  if (changed) ui_apply(shown);

  if (now - lastTick >= 1000) { lastTick = now; ui_tick(shown); }
  if (now - lastLed >= 20) { lastLed = now; ledUpdate(); }

  lv_timer_handler();
  delay(5);
}
