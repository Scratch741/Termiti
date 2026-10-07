# Roadmap: accounts, cloud save, paid purchases

> Future plan (not scheduled): Google Play identity, server-side progress and economy, then selling packs / gold for real money — in that order.

**Status:** plan · agreed with the user 2026-10-06 · nothing implemented yet

## Why this order

Today the player's identity online is `name + deviceId` sent by the client, and the whole economy (gold, gems, dust, card collection, decks) lives in local storage on the phone. The profile also has a DEBUG tab (+500 gold, +50 gems, +500 dust, +100 XP, unlock all cards) that is **intentionally visible to everyone during the testing phase**. Selling anything is pointless until the purchased goods cannot be forged and survive a reinstall, so paid purchases come last.

## Step 1 — Google Play identity

- Release on Google Play; sign in with Play Games in the background at start.
- Online: the client asks Google for a short-lived auth code, sends it with `JOIN`, the **server verifies it with Google** and uses the Play Games player id as the account key (replaces `deviceId` in `RatingSystem`). An id that the server merely receives from the client would change nothing.
- **Offline play must stay available without sign-in and without internet** (quick game, campaign, roguelike, arena, deck builder). A failed or declined sign-in only disables online.

Open decisions:
- Players without Play Games: no online at all, or a guest mode (today's name + deviceId) without rating?
- Migration of existing ratings keyed by `deviceId`.

## Step 2 — Progress and economy on the server (cloud save)

- Balance (gold, gems, dust), collection and decks stored per account on the server; the phone keeps a local copy for offline play and syncs when connected.
- The server can then verify card ownership of a deck sent in `QUEUE_JOIN` (today it cannot — it does not know collections).
- Restores progress on a new phone.

Open decisions:
- Conflict rule when two devices progressed offline (higher progress wins / ask the player).
- How much offline-earned progress the server accepts (rewards are computed on the phone and can be forged on a rooted device).
- Play Games Saved Games vs. our own server storage — purchases need our server anyway.

## Step 3 — Remove test shortcuts from the release build

- DEBUG profile tab only in debug builds (`BuildConfig.DEBUG`), `allCardsUnlocked` not reachable in release.

## Step 4 — Paid purchases

- Google Play Billing is the only allowed payment method for digital goods on Play. Gold and packs are **consumable** products.
- Flow: purchase on the phone → purchase token sent to our server → server verifies it with Google → server credits the goods → purchase consumed. Never credit on the client alone.
- Buying needs internet only at the moment of payment; purchased cards work offline.

Outside the code:
- Google's service fee: 15 % of the first USD 1M per year, 30 % above.
- Random packs are loot boxes: Google requires the **rarity odds to be disclosed** before purchase. Some countries restrict paid loot boxes (Belgium, partly the Netherlands) — usually handled by not selling packs for money there, only currency.
- Content rating and the "in-app purchases" label in Play Console.
- A Google payments (merchant) profile; income is taxable — EU VAT is collected by Google. To be confirmed with an accountant.

## Known server gaps to close along the way

From the review on 2026-10-06 (see [[log]]), not fixed yet:
- ~~Another device joining with the same name during the reconnect grace period cancels the disconnected player's return and the forfeit timer~~ — fixed 2026-10-07 (name reserved for the original device during the grace period).
- ~~`/crash-report` accepts unlimited unauthenticated uploads~~ — fixed 2026-10-07 (per-address rate limit, size limit, 500-file cap). `/crash-logs` and `/replays` are still public.
- Cleartext `ws://` / `http://` (`usesCleartextTraffic`) — needs TLS before any account or purchase data is sent.
- Rating can be farmed by two devices playing each other; the WIN_ONLINE daily quest also pays gems.

## Related pages
- [[systems/online]] — current JOIN / reconnect / rating flow that step 1 replaces
- [[proposals]] — game-design ideas (this page is the technical / business roadmap)

## Changelog
- 2026-10-07: Two server gaps closed (reconnect grace, crash-report limits).
- 2026-10-06: Page created from the discussion after the server bug/exploit review.
