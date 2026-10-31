#pragma once
#include <Arduino.h>
#include <lvgl.h>
#include "config.h"

struct LbRow {
  char name[32];
  int rating;
  int wins;
  int losses;
};

/** Poslední známá data ze serveru. Plní je síťová úloha, UI dostává kopii. */
struct ServerData {
  bool statusOk = false;        // /status odpověděl (starší server ho nemá → 404)
  int online = 0;
  int queueNormal = 0;
  int queueSuper = 0;
  int games = 0;
  int totalPlayers = -1;

  bool lbOk[2] = {false, false};   // 0 = normal (Constructed), 1 = super_random
  int lbCount[2] = {0, 0};
  int lbTotal[2] = {0, 0};
  LbRow lb[2][LB_ROWS];

  uint32_t lastOkMs = 0;        // millis() poslední úspěšné odpovědi
  bool serverError = false;     // poslední pokus o spojení selhal
  uint32_t version = 0;         // zvýší se při každé změně
};

struct Settings {
  uint8_t brightness = 80;      // %
  bool led = true;              // LED bliká, když někdo čeká ve frontě
  bool autoPage = true;         // stránky se samy přepínají
};

extern Settings g_settings;
extern volatile uint32_t g_lastTouchMs;

// termiti_cyd.ino
void app_set_brightness(uint8_t pct);
void app_save_settings();
void app_forget_wifi();

// ui.cpp
void ui_init();
void ui_show_connecting();
void ui_show_portal();
void ui_show_main();
void ui_apply(const ServerData &d);
void ui_tick(const ServerData &d);   // jednou za sekundu
