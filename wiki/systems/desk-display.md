# Desk display (CYD)

> ESP32 "Cheap Yellow Display" firmware that shows live server stats (online, queue, running games) and both leaderboards on the desk.

## Content

Firmware lives in `display/termiti_cyd/` (Arduino sketch, LVGL 9.2). Target board: ESP32-2432S028R (2.8" 320×240 ILI9341, XPT2046 resistive touch, RGB LED, CH340 USB).

### Data sources

| Endpoint | Interval | Used for |
|----------|----------|----------|
| `GET /status` | 15 s | `online`, `queueNormal`, `queueSuper`, `games`, `totalPlayers` |
| `GET /leaderboard?mode=normal\|super_random&limit=7` | 60 s | two leaderboard pages |

`/status` (added 2026-10-31 in `server.js`) only reads in-memory counters (`players.size`, `queue.length`, `superQueue.length`, `games.size`, `ratingSystem.getTotalPlayers()`), so it has no rate limit, same as `/time`. A server without `/status` answers 404: the display then shows "–" in the live cards ("server bez /status") but still shows the leaderboards and takes "HRÁČŮ CELKEM" from `/leaderboard`'s `total`. 2 leaderboard requests per minute stay well under the 20/min per-IP limit of `/leaderboard`.

### UI

- Header: "TERMITI" (Cinzel), Wi-Fi icon (teal = OK, gold = server not answering / stale, red = no Wi-Fi), clock (NTP, Europe/Prague).
- Swipeable tiles: **Přehled** (4 cards: online with pulsing dot, queue, running games, total players), **Žebříček · Constructed**, **Žebříček · Super náhodný** (top 7, gold/silver/bronze rank badges, rating, W–L), **Nastavení** (brightness, LED switch, auto paging, Wi-Fi/IP/RSSI info, long-press "forget Wi-Fi").
- Footer: "Aktualizováno před N s" and page dots.
- Auto paging every 12 s between the first three tiles; paused for 60 s after any touch. When someone enters the queue (0 → >0) the display jumps to Přehled (if nobody is touching it) and the queue card gets a gold border ("ČEKÁ NA SOUPEŘE").
- RGB LED: green breathing while someone waits in the queue; short red blink every 3 s when the server does not answer. Settings are stored in NVS (`Preferences`, namespace `termiti`).

### Wi-Fi setup

WiFiManager (non-blocking). Without saved credentials the board opens AP `Termiti-Displej`; the phone shows a captive portal (else `192.168.4.1`) to pick the network. Credentials never live in the source.

### Build

```powershell
display\termiti_cyd\build.ps1            # compile + upload to COM3
display\termiti_cyd\build.ps1 -Port COM5
display\termiti_cyd\build.ps1 -NoUpload
```

- Board `esp32:esp32:esp32` (core 3.3.x), `PartitionScheme=huge_app`.
- TFT_eSPI is configured by `-D` build flags in `build.ps1` (`USER_SETUP_LOADED`, `ILI9341_2_DRIVER`, HSPI pins 12/13/14/15, DC 2), never in the library's `User_Setup.h` — that file is global for all sketches.
- `lv_conf.h` is next to the sketch (`LV_CONF_INCLUDE_SIMPLE` + `-I<sketch>`).
- Libraries: `lvgl@9.2.2`, `TFT_eSPI`, `XPT2046_Touchscreen`, `WiFiManager`, `ArduinoJson` 7.
- Networking runs in a FreeRTOS task on core 0; LVGL on the Arduino loop (core 1). Data are copied under a mutex (`ServerData.version` signals a change).

### Gotchas

- **Fonts:** LVGL's built-in Montserrat has no Czech diacritics. `tools/ttf2lvgl.py` (Pillow) converts TTF into `lv_font_fmt_txt` C sources (4 bpp, no kerning); `tools/gen_fonts.sh <dir with Montserrat TTF>` regenerates `src/fonts/*.c`. Text fonts fall back to built-in Montserrat for `LV_SYMBOL_*` icons. Font declarations in C++ must be inside `extern "C"`.
- **Board revisions:** some CYDs use ST7789 instead of ILI9341 or need colour inversion. The user's board needs `TFT_INVERSION_ON` (without it the dark UI showed a white background), now set in `build.ps1`. If colours are wrong / image mirrored on another board, change the driver flag (`ST7789_DRIVER`, `TFT_INVERSION_ON` / `TFT_INVERSION_OFF`) or the rotation in `setup()`.
- Changing a build flag rebuilds all libraries incl. LVGL — the first build after it takes ~10+ min.
- **Touch calibration:** raw corner values `TOUCH_X_MIN/MAX`, `TOUCH_Y_MIN/MAX` in `config.h`.

## Related pages
- [[systems/online]] — server, `/leaderboard` rate limit, `/status`

## Changelog
- 2026-10-31: Page created — CYD firmware v1.0.0, `/status` endpoint
- 2026-10-31: `TFT_INVERSION_ON` — the user's panel showed inverted colours
