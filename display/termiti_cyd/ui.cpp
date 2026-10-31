// Rozhraní displeje: hlavička s hodinami, stránky Přehled / 2× žebříček / Nastavení, patička.
// Barvy odpovídají webové stránce žebříčku na serveru.
#include <WiFi.h>
#include <time.h>
#include "app.h"

extern "C" {
LV_FONT_DECLARE(font_text_12)
LV_FONT_DECLARE(font_text_14)
LV_FONT_DECLARE(font_semi_16)
LV_FONT_DECLARE(font_num_40)
LV_FONT_DECLARE(font_title_24)
}

#define C_BG      lv_color_hex(0x0d0a0e)
#define C_PANEL   lv_color_hex(0x1a1320)
#define C_BORDER  lv_color_hex(0x2a2430)
#define C_TEXT    lv_color_hex(0xede0c4)
#define C_MUTED   lv_color_hex(0x7a6e5f)
#define C_GOLD    lv_color_hex(0xd4a843)
#define C_TEAL    lv_color_hex(0x3dbfad)
#define C_RED     lv_color_hex(0xd2553f)
#define C_SILVER  lv_color_hex(0xb9b4c4)
#define C_BRONZE  lv_color_hex(0xc0804e)

#define HEADER_H 34
#define FOOTER_H 16
#define TILE_H   (SCREEN_H - HEADER_H - FOOTER_H)
#define PAGE_COUNT 4      // přehled, constructed, super náhodný, nastavení
#define PAGE_SETTINGS 3

static lv_obj_t *scrConnecting, *scrPortal, *scrMain;
static lv_obj_t *lblClock, *lblWifi, *lblFooter, *tileview;
static lv_obj_t *dots[PAGE_COUNT];
static lv_obj_t *lblInfo;
static int activePage = 0;
static uint32_t lastPageChangeMs = 0;
static int prevQueue = 0;

struct StatCard {
  lv_obj_t *card, *caption, *value, *sub;
  int shown = -1;
};
static StatCard cardOnline, cardQueue, cardGames, cardTotal;

struct LbView {
  lv_obj_t *total, *empty;
  lv_obj_t *row[LB_ROWS], *rank[LB_ROWS], *rankTxt[LB_ROWS], *name[LB_ROWS], *rating[LB_ROWS], *wl[LB_ROWS];
};
static LbView lbView[2];

// ── Pomocníci ───────────────────────────────────────────────────────────────

static lv_obj_t *mkLabel(lv_obj_t *parent, const lv_font_t *font, lv_color_t color, const char *text) {
  lv_obj_t *l = lv_label_create(parent);
  lv_obj_set_style_text_font(l, font, 0);
  lv_obj_set_style_text_color(l, color, 0);
  lv_label_set_text(l, text);
  return l;
}

static lv_obj_t *mkBox(lv_obj_t *parent) {
  lv_obj_t *o = lv_obj_create(parent);
  lv_obj_remove_style_all(o);
  lv_obj_remove_flag(o, LV_OBJ_FLAG_SCROLLABLE);
  return o;
}

static lv_obj_t *mkPanel(lv_obj_t *parent) {
  lv_obj_t *o = mkBox(parent);
  lv_obj_set_style_bg_color(o, C_PANEL, 0);
  lv_obj_set_style_bg_opa(o, LV_OPA_COVER, 0);
  lv_obj_set_style_radius(o, 10, 0);
  lv_obj_set_style_border_color(o, C_BORDER, 0);
  lv_obj_set_style_border_width(o, 1, 0);
  lv_obj_set_style_pad_all(o, 8, 0);
  return o;
}

static lv_obj_t *mkScreen() {
  lv_obj_t *s = lv_obj_create(NULL);
  lv_obj_set_style_bg_color(s, C_BG, 0);
  lv_obj_set_style_bg_opa(s, LV_OPA_COVER, 0);
  lv_obj_remove_flag(s, LV_OBJ_FLAG_SCROLLABLE);
  return s;
}

static const char *hracu(int n) {
  if (n == 1) return "hráč";
  if (n >= 2 && n <= 4) return "hráči";
  return "hráčů";
}

static bool userIdle() {
  return g_lastTouchMs == 0 || millis() - g_lastTouchMs > AUTO_PAGE_PAUSE_MS;
}

// ── Stránkování ─────────────────────────────────────────────────────────────

static void updateDots() {
  for (int i = 0; i < PAGE_COUNT; i++) {
    bool on = i == activePage;
    lv_obj_set_width(dots[i], on ? 14 : 6);
    lv_obj_set_style_bg_color(dots[i], on ? C_GOLD : C_BORDER, 0);
  }
}

static void goPage(int page) {
  lv_tileview_set_tile_by_index(tileview, page, 0, LV_ANIM_ON);
  activePage = page;
  lastPageChangeMs = millis();
  updateDots();
}

static void onTileChanged(lv_event_t *) {
  lv_obj_t *tile = lv_tileview_get_tile_active(tileview);
  activePage = (int)(intptr_t)lv_obj_get_user_data(tile);
  lastPageChangeMs = millis();
  updateDots();
}

// ── Přehled ─────────────────────────────────────────────────────────────────

static void animValueCb(void *var, int32_t v) {
  lv_label_set_text_fmt((lv_obj_t *)var, "%d", (int)v);
}

static void setStat(StatCard &sc, int v, bool known) {
  if (!known) {
    lv_anim_delete(sc.value, animValueCb);
    lv_label_set_text(sc.value, "–");
    sc.shown = -1;
    return;
  }
  if (v == sc.shown) return;
  int from = sc.shown < 0 ? 0 : sc.shown;
  sc.shown = v;
  lv_anim_t a;
  lv_anim_init(&a);
  lv_anim_set_var(&a, sc.value);
  lv_anim_set_exec_cb(&a, animValueCb);
  lv_anim_set_values(&a, from, v);
  lv_anim_set_duration(&a, 700);
  lv_anim_set_path_cb(&a, lv_anim_path_ease_out);
  lv_anim_start(&a);
}

static void mkStatCard(StatCard &sc, lv_obj_t *tile, int col, int row, const char *caption, lv_color_t color) {
  sc.card = mkPanel(tile);
  lv_obj_set_size(sc.card, 145, 84);
  lv_obj_set_pos(sc.card, 10 + col * 155, 4 + row * 92);
  sc.caption = mkLabel(sc.card, &font_text_12, C_MUTED, caption);
  lv_obj_align(sc.caption, LV_ALIGN_TOP_LEFT, 0, 0);
  sc.value = mkLabel(sc.card, &font_num_40, color, "–");
  lv_obj_align(sc.value, LV_ALIGN_BOTTOM_LEFT, 0, 8);
  sc.sub = mkLabel(sc.card, &font_text_12, C_MUTED, "");
  lv_obj_align(sc.sub, LV_ALIGN_BOTTOM_RIGHT, 0, -2);
}

static void pulseCb(void *var, int32_t v) {
  lv_obj_set_style_bg_opa((lv_obj_t *)var, (lv_opa_t)v, 0);
}

static void buildOverview(lv_obj_t *tile) {
  mkStatCard(cardOnline, tile, 0, 0, "ONLINE", C_TEAL);
  mkStatCard(cardQueue, tile, 1, 0, "VE FRONTĚ", C_GOLD);
  mkStatCard(cardGames, tile, 0, 1, "ROZEHRANÉ HRY", C_TEXT);
  mkStatCard(cardTotal, tile, 1, 1, "HRÁČŮ CELKEM", C_TEXT);

  // Blikající tečka „živě“ u počtu online
  lv_obj_t *dot = mkBox(cardOnline.card);
  lv_obj_set_size(dot, 8, 8);
  lv_obj_set_style_radius(dot, LV_RADIUS_CIRCLE, 0);
  lv_obj_set_style_bg_color(dot, C_TEAL, 0);
  lv_obj_set_style_bg_opa(dot, LV_OPA_COVER, 0);
  lv_obj_align(dot, LV_ALIGN_TOP_RIGHT, 0, 3);
  lv_anim_t a;
  lv_anim_init(&a);
  lv_anim_set_var(&a, dot);
  lv_anim_set_exec_cb(&a, pulseCb);
  lv_anim_set_values(&a, LV_OPA_20, LV_OPA_COVER);
  lv_anim_set_duration(&a, 900);
  lv_anim_set_playback_duration(&a, 900);
  lv_anim_set_repeat_count(&a, LV_ANIM_REPEAT_INFINITE);
  lv_anim_set_path_cb(&a, lv_anim_path_ease_in_out);
  lv_anim_start(&a);
}

// ── Žebříček ────────────────────────────────────────────────────────────────

static void buildLeaderboard(LbView &v, lv_obj_t *tile, const char *title) {
  lv_obj_t *t = mkLabel(tile, &font_semi_16, C_GOLD, title);
  lv_obj_set_pos(t, 10, 4);
  v.total = mkLabel(tile, &font_text_12, C_MUTED, "");
  lv_obj_align(v.total, LV_ALIGN_TOP_RIGHT, -10, 8);

  for (int i = 0; i < LB_ROWS; i++) {
    lv_obj_t *row = v.row[i] = mkBox(tile);
    lv_obj_set_size(row, 300, 22);
    lv_obj_set_pos(row, 10, 28 + i * 22);
    lv_obj_set_style_radius(row, 6, 0);
    lv_obj_set_style_bg_color(row, C_PANEL, 0);
    lv_obj_set_style_bg_opa(row, i % 2 ? LV_OPA_TRANSP : LV_OPA_70, 0);

    lv_obj_t *rank = v.rank[i] = mkBox(row);
    lv_obj_set_size(rank, 18, 18);
    lv_obj_set_style_radius(rank, LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_opa(rank, LV_OPA_COVER, 0);
    lv_obj_align(rank, LV_ALIGN_LEFT_MID, 3, 0);
    v.rankTxt[i] = mkLabel(rank, &font_text_12, C_BG, "");
    lv_obj_center(v.rankTxt[i]);

    v.name[i] = mkLabel(row, &font_text_14, C_TEXT, "");
    lv_label_set_long_mode(v.name[i], LV_LABEL_LONG_DOT);
    lv_obj_set_width(v.name[i], 168);
    lv_obj_align(v.name[i], LV_ALIGN_LEFT_MID, 28, 0);

    v.rating[i] = mkLabel(row, &font_semi_16, C_GOLD, "");
    lv_obj_align(v.rating[i], LV_ALIGN_RIGHT_MID, -54, 0);

    v.wl[i] = mkLabel(row, &font_text_12, C_MUTED, "");
    lv_obj_set_width(v.wl[i], 48);
    lv_obj_set_style_text_align(v.wl[i], LV_TEXT_ALIGN_RIGHT, 0);
    lv_obj_align(v.wl[i], LV_ALIGN_RIGHT_MID, -4, 0);
  }

  v.empty = mkLabel(tile, &font_text_14, C_MUTED, "Načítám…");
  lv_obj_align(v.empty, LV_ALIGN_CENTER, 0, 10);
}

static void applyLeaderboard(LbView &v, const ServerData &d, int mode) {
  int n = d.lbCount[mode];
  if (d.lbOk[mode]) lv_label_set_text_fmt(v.total, "%d %s", d.lbTotal[mode], hracu(d.lbTotal[mode]));

  if (n == 0) {
    lv_label_set_text(v.empty, d.lbOk[mode] ? "Zatím nikdo nehrál" : d.serverError ? "Server nedostupný" : "Načítám…");
    lv_obj_remove_flag(v.empty, LV_OBJ_FLAG_HIDDEN);
  } else {
    lv_obj_add_flag(v.empty, LV_OBJ_FLAG_HIDDEN);
  }

  for (int i = 0; i < LB_ROWS; i++) {
    if (i >= n) { lv_obj_add_flag(v.row[i], LV_OBJ_FLAG_HIDDEN); continue; }
    lv_obj_remove_flag(v.row[i], LV_OBJ_FLAG_HIDDEN);
    const LbRow &r = d.lb[mode][i];
    lv_color_t medal = i == 0 ? C_GOLD : i == 1 ? C_SILVER : i == 2 ? C_BRONZE : C_BORDER;
    lv_obj_set_style_bg_color(v.rank[i], medal, 0);
    lv_obj_set_style_text_color(v.rankTxt[i], i < 3 ? C_BG : C_MUTED, 0);
    lv_label_set_text_fmt(v.rankTxt[i], "%d", i + 1);
    lv_label_set_text(v.name[i], r.name);
    lv_label_set_text_fmt(v.rating[i], "%d", r.rating);
    lv_label_set_text_fmt(v.wl[i], "%d–%d", r.wins, r.losses);
  }
}

// ── Nastavení ───────────────────────────────────────────────────────────────

static void onBrightness(lv_event_t *e) {
  lv_obj_t *s = (lv_obj_t *)lv_event_get_target(e);
  app_set_brightness((uint8_t)lv_slider_get_value(s));
  if (lv_event_get_code(e) == LV_EVENT_RELEASED) app_save_settings();
}

static void onLedSwitch(lv_event_t *e) {
  g_settings.led = lv_obj_has_state((lv_obj_t *)lv_event_get_target(e), LV_STATE_CHECKED);
  app_save_settings();
}

static void onAutoSwitch(lv_event_t *e) {
  g_settings.autoPage = lv_obj_has_state((lv_obj_t *)lv_event_get_target(e), LV_STATE_CHECKED);
  app_save_settings();
}

static void onForgetWifi(lv_event_t *) {
  app_forget_wifi();
}

static lv_obj_t *mkSwitchRow(lv_obj_t *tile, int y, const char *text, bool on, lv_event_cb_t cb) {
  lv_obj_t *l = mkLabel(tile, &font_text_14, C_TEXT, text);
  lv_obj_set_pos(l, 10, y + 4);
  lv_obj_t *sw = lv_switch_create(tile);
  lv_obj_set_size(sw, 44, 24);
  lv_obj_align(sw, LV_ALIGN_TOP_RIGHT, -10, y);
  if (on) lv_obj_add_state(sw, LV_STATE_CHECKED);
  lv_obj_add_event_cb(sw, cb, LV_EVENT_VALUE_CHANGED, NULL);
  return sw;
}

static void buildSettings(lv_obj_t *tile) {
  lv_obj_t *t = mkLabel(tile, &font_semi_16, C_GOLD, "Nastavení");
  lv_obj_set_pos(t, 10, 4);

  lv_obj_t *l = mkLabel(tile, &font_text_14, C_TEXT, "Jas");
  lv_obj_set_pos(l, 10, 34);
  lv_obj_t *slider = lv_slider_create(tile);
  lv_obj_set_size(slider, 210, 8);
  lv_obj_align(slider, LV_ALIGN_TOP_RIGHT, -18, 40);
  lv_slider_set_range(slider, 5, 100);
  lv_slider_set_value(slider, g_settings.brightness, LV_ANIM_OFF);
  lv_obj_add_event_cb(slider, onBrightness, LV_EVENT_VALUE_CHANGED, NULL);
  lv_obj_add_event_cb(slider, onBrightness, LV_EVENT_RELEASED, NULL);

  mkSwitchRow(tile, 60, "LED, když někdo čeká", g_settings.led, onLedSwitch);
  mkSwitchRow(tile, 90, "Automatické listování", g_settings.autoPage, onAutoSwitch);

  lblInfo = mkLabel(tile, &font_text_12, C_MUTED, "");
  lv_obj_set_pos(lblInfo, 10, 120);

  lv_obj_t *btn = lv_button_create(tile);
  lv_obj_set_size(btn, 300, 28);
  lv_obj_set_pos(btn, 10, 156);
  lv_obj_set_style_bg_opa(btn, LV_OPA_TRANSP, 0);
  lv_obj_set_style_border_color(btn, C_RED, 0);
  lv_obj_set_style_border_width(btn, 1, 0);
  lv_obj_set_style_shadow_width(btn, 0, 0);
  lv_obj_set_style_radius(btn, 8, 0);
  lv_obj_t *bl = mkLabel(btn, &font_text_12, C_RED, "Podrž pro zapomenutí Wi-Fi");
  lv_obj_center(bl);
  lv_obj_add_event_cb(btn, onForgetWifi, LV_EVENT_LONG_PRESSED, NULL);
}

// ── Obrazovky ───────────────────────────────────────────────────────────────

static void buildMain() {
  scrMain = mkScreen();

  lv_obj_t *hdr = mkBox(scrMain);
  lv_obj_set_size(hdr, SCREEN_W, HEADER_H);
  lv_obj_set_style_border_side(hdr, LV_BORDER_SIDE_BOTTOM, 0);
  lv_obj_set_style_border_color(hdr, C_BORDER, 0);
  lv_obj_set_style_border_width(hdr, 1, 0);
  lv_obj_t *title = mkLabel(hdr, &font_title_24, C_GOLD, "TERMITI");
  lv_obj_align(title, LV_ALIGN_LEFT_MID, 10, 1);
  lblClock = mkLabel(hdr, &font_semi_16, C_TEXT, "--:--");
  lv_obj_set_width(lblClock, 50);
  lv_obj_set_style_text_align(lblClock, LV_TEXT_ALIGN_RIGHT, 0);
  lv_obj_align(lblClock, LV_ALIGN_RIGHT_MID, -10, 0);
  lblWifi = mkLabel(hdr, &font_text_14, C_MUTED, LV_SYMBOL_WIFI);
  lv_obj_align(lblWifi, LV_ALIGN_RIGHT_MID, -66, 0);

  tileview = lv_tileview_create(scrMain);
  lv_obj_set_size(tileview, SCREEN_W, TILE_H);
  lv_obj_set_pos(tileview, 0, HEADER_H);
  lv_obj_set_style_bg_opa(tileview, LV_OPA_TRANSP, 0);
  lv_obj_set_scrollbar_mode(tileview, LV_SCROLLBAR_MODE_OFF);
  lv_obj_t *tiles[PAGE_COUNT];
  for (int i = 0; i < PAGE_COUNT; i++) {
    lv_dir_t dir = i == 0 ? LV_DIR_RIGHT : i == PAGE_COUNT - 1 ? LV_DIR_LEFT : LV_DIR_HOR;
    tiles[i] = lv_tileview_add_tile(tileview, i, 0, dir);
    lv_obj_set_user_data(tiles[i], (void *)(intptr_t)i);
    lv_obj_set_scrollbar_mode(tiles[i], LV_SCROLLBAR_MODE_OFF);
  }
  lv_obj_add_event_cb(tileview, onTileChanged, LV_EVENT_VALUE_CHANGED, NULL);

  buildOverview(tiles[0]);
  buildLeaderboard(lbView[0], tiles[1], "Žebříček · Constructed");
  buildLeaderboard(lbView[1], tiles[2], "Žebříček · Super náhodný");
  buildSettings(tiles[3]);

  lblFooter = mkLabel(scrMain, &font_text_12, C_MUTED, "");
  lv_obj_align(lblFooter, LV_ALIGN_BOTTOM_LEFT, 10, -1);

  lv_obj_t *dotRow = mkBox(scrMain);
  lv_obj_set_size(dotRow, LV_SIZE_CONTENT, 6);
  lv_obj_set_flex_flow(dotRow, LV_FLEX_FLOW_ROW);
  lv_obj_set_style_pad_column(dotRow, 4, 0);
  lv_obj_align(dotRow, LV_ALIGN_BOTTOM_RIGHT, -10, -5);
  for (int i = 0; i < PAGE_COUNT; i++) {
    dots[i] = mkBox(dotRow);
    lv_obj_set_size(dots[i], 6, 6);
    lv_obj_set_style_radius(dots[i], LV_RADIUS_CIRCLE, 0);
    lv_obj_set_style_bg_opa(dots[i], LV_OPA_COVER, 0);
  }
  updateDots();
}

static lv_obj_t *mkCenteredColumn(lv_obj_t *scr) {
  lv_obj_t *col = mkBox(scr);
  lv_obj_set_size(col, SCREEN_W - 20, LV_SIZE_CONTENT);
  lv_obj_set_flex_flow(col, LV_FLEX_FLOW_COLUMN);
  lv_obj_set_flex_align(col, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER);
  lv_obj_set_style_pad_row(col, 6, 0);
  lv_obj_center(col);
  return col;
}

static void buildConnecting() {
  scrConnecting = mkScreen();
  lv_obj_t *col = mkCenteredColumn(scrConnecting);
  mkLabel(col, &font_title_24, C_GOLD, "TERMITI");
  mkLabel(col, &font_text_14, C_MUTED, "Připojuji k Wi-Fi…");
}

static void buildPortal() {
  scrPortal = mkScreen();
  lv_obj_t *col = mkCenteredColumn(scrPortal);
  mkLabel(col, &font_title_24, C_GOLD, "TERMITI");
  mkLabel(col, &font_semi_16, C_TEXT, "Nastavení Wi-Fi");
  mkLabel(col, &font_text_14, C_MUTED, "1. V telefonu se připoj k síti");
  lv_obj_t *ssid = mkPanel(col);
  lv_obj_set_size(ssid, LV_SIZE_CONTENT, LV_SIZE_CONTENT);
  lv_obj_set_style_pad_hor(ssid, 14, 0);
  lv_obj_set_style_pad_ver(ssid, 6, 0);
  lv_obj_set_style_border_color(ssid, C_GOLD, 0);
  mkLabel(ssid, &font_semi_16, C_GOLD, PORTAL_SSID);
  mkLabel(col, &font_text_14, C_MUTED, "2. Otevře se stránka (jinak 192.168.4.1)");
  mkLabel(col, &font_text_14, C_MUTED, "3. Vyber svou síť a zadej heslo");
}

void ui_init() {
  lv_display_t *disp = lv_display_get_default();
  lv_theme_t *th = lv_theme_default_init(disp, C_TEAL, C_GOLD, true, &font_text_14);
  lv_display_set_theme(disp, th);
  buildConnecting();
  buildPortal();
  buildMain();
}

void ui_show_connecting() { lv_screen_load(scrConnecting); }
void ui_show_portal() { lv_screen_load(scrPortal); }

void ui_show_main() {
  lv_screen_load_anim(scrMain, LV_SCR_LOAD_ANIM_FADE_IN, 400, 0, false);
  lastPageChangeMs = millis();
}

// ── Data ────────────────────────────────────────────────────────────────────

void ui_apply(const ServerData &d) {
  int queue = d.queueNormal + d.queueSuper;
  setStat(cardOnline, d.online, d.statusOk);
  setStat(cardQueue, queue, d.statusOk);
  setStat(cardGames, d.games, d.statusOk);
  int total = d.statusOk ? d.totalPlayers : (d.lbOk[0] ? d.lbTotal[0] : -1);   // /leaderboard vrací totéž
  setStat(cardTotal, total, total >= 0);

  if (d.statusOk) {
    lv_label_set_text_fmt(cardQueue.sub, "%d · %d", d.queueNormal, d.queueSuper);
    lv_label_set_text(cardOnline.sub, "");
  } else {
    lv_label_set_text(cardQueue.sub, "");
    lv_label_set_text(cardOnline.sub, d.lbOk[0] && !d.serverError ? "server bez /status" : "");
  }

  bool waiting = d.statusOk && queue > 0;
  lv_label_set_text(cardQueue.caption, waiting ? "ČEKÁ NA SOUPEŘE" : "VE FRONTĚ");
  lv_obj_set_style_text_color(cardQueue.caption, waiting ? C_GOLD : C_MUTED, 0);
  lv_obj_set_style_border_color(cardQueue.card, waiting ? C_GOLD : C_BORDER, 0);
  lv_obj_set_style_border_width(cardQueue.card, waiting ? 2 : 1, 0);

  // Někdo si právě stoupl do fronty → ukaž přehled, pokud na displej nikdo nesahá
  if (waiting && prevQueue == 0 && activePage != 0 && userIdle()) goPage(0);
  prevQueue = d.statusOk ? queue : 0;

  applyLeaderboard(lbView[0], d, 0);
  applyLeaderboard(lbView[1], d, 1);
}

void ui_tick(const ServerData &d) {
  uint32_t now = millis();
  bool wifi = WiFi.status() == WL_CONNECTED;

  time_t t = time(nullptr);
  if (t > 1700000000) {
    struct tm tmv;
    localtime_r(&t, &tmv);
    lv_label_set_text_fmt(lblClock, "%02d:%02d", tmv.tm_hour, tmv.tm_min);
  }

  bool stale = d.lastOkMs == 0 || now - d.lastOkMs > 3 * LEADERBOARD_INTERVAL_MS;
  lv_color_t wifiColor = !wifi ? C_RED : (d.serverError || stale) ? C_GOLD : C_TEAL;
  lv_obj_set_style_text_color(lblWifi, wifiColor, 0);

  lv_color_t footColor = C_MUTED;
  if (!wifi) {
    lv_label_set_text(lblFooter, "Wi-Fi odpojena, zkouším znovu…");
    footColor = C_RED;
  } else if (d.lastOkMs == 0) {
    lv_label_set_text(lblFooter, d.serverError ? "Server nedostupný" : "Načítám…");
    if (d.serverError) footColor = C_RED;
  } else {
    uint32_t age = (now - d.lastOkMs) / 1000;
    const char *prefix = d.serverError ? "Server neodpovídá · data z doby před" : "Aktualizováno před";
    if (age < 60) lv_label_set_text_fmt(lblFooter, "%s %u s", prefix, (unsigned)age);
    else lv_label_set_text_fmt(lblFooter, "%s %u min", prefix, (unsigned)(age / 60));
    if (d.serverError) footColor = C_GOLD;
  }
  lv_obj_set_style_text_color(lblFooter, footColor, 0);

  if (activePage == PAGE_SETTINGS) {
    if (wifi) {
      lv_label_set_text_fmt(lblInfo, "Wi-Fi: %s · %d dBm · %s\nServer: %s · verze %s",
                            WiFi.SSID().c_str(), (int)WiFi.RSSI(), WiFi.localIP().toString().c_str(),
                            SERVER_LABEL, FW_VERSION);
    } else {
      lv_label_set_text_fmt(lblInfo, "Wi-Fi: odpojeno\nServer: %s · verze %s", SERVER_LABEL, FW_VERSION);
    }
  }

  // Automatické listování přehled → žebříčky; na nastavení zůstane, dokud se nepřestane sahat
  if (g_settings.autoPage && userIdle() && now - lastPageChangeMs > AUTO_PAGE_MS) {
    goPage(activePage >= PAGE_SETTINGS - 1 ? 0 : activePage + 1);
  }
}
