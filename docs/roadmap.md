# Tamil Scripture Android App — Roadmap

| | |
|---|---|
| Version | 0.1 draft, 3 October 2026 |
| Requirements | [requirements.md](requirements.md) |
| Design | [design.md](design.md) |

Ship a fast offline reader first, then add study tools, audio and sync. Every milestone ends with a build on a Play testing track that is usable on its own.

Estimates assume **one full-time Android developer**, with server work (Rust packs, Supabase migrations) done by the same person in the website repository. They are effort in weeks, not calendar dates. Work in the website repository is marked **[web]**.

## Overview

Status 3 Oct 2026: tasks marked [x] are built (some partly — see design §18 for interim choices).

| Milestone | Theme | Estimate | Play track | Depends on |
|---|---|---|---|---|
| M0 | Foundations | 2 weeks | — | — |
| M1 | Offline and online reader | 6 weeks | Internal | M0 |
| M2 | Every screen size | 3 weeks | Closed testing | M1 |
| M3 | Search and study | 4 weeks | Closed testing | M1 |
| M4 | Commentaries | 2 weeks | Closed testing | M3 |
| M5 | Audio Bible | 3 weeks | Open testing | M1 |
| M6 | Accounts and sync (app + website parity) | 5 weeks | Open testing | M1 |
| M7 | Reading plans and widget | 2 weeks | Production 1.0 | M6 |
| M8 | Study data, atlas, community, present mode | 6 weeks | 1.x updates | M3, M6 |
| | **Total to 1.0 (M0–M7)** | **~27 weeks** | | |

Moving the website's own content to R2 is deferred (owner, 3 Oct 2026). Two small preparations in the website repo keep that a configuration change later: `PUBLIC_CONTENT_BASE` in `contentUrl()` and an origin-agnostic service-worker match ([design §7.11.5](design.md#7115-website-side-prepared-not-migrated)). They can be done at any point and are not on the critical path.

Stats collection is not a separate milestone. It starts in M1 with `view` and `read` events, and each later milestone adds its own events.

M2 to M6 can overlap when there is more than one developer. With two developers, a practical split is: one on M2 + M5, the other on M3 + M4, joining for M6.

---

## M0 · Foundations (2 weeks)

**Goal:** an empty app that builds, tests and ships to the internal track, with the Rust bridge and the pack pipeline proven end to end on one tiny pack.

### Tasks

**Project setup**
- [x] M0-1 Create the Gradle project: version catalog, Kotlin DSL, `build-logic` convention plugins, module skeletons from [design §3.2](design.md#32-modules).
- [x] M0-2 Hilt, Navigation Compose with typed routes, `MainActivity` with edge-to-edge, `TsApplication`.
- [x] M0-3 Flavors `dev`, `staging`, `prod`; `BuildConfig` for Supabase URL, anon key and the two bootstrap addresses (one per provider). No content host is compiled in ([design §7.11](design.md#711-changing-hosts-r2--vercel)).
- [x] M0-4 GitHub Actions `android.yml`: Rust job building `ts-mobile` from the pinned website commit, then lint, detekt, unit tests and a debug APK artifact on every PR ([design §16.3](design.md#163-build-workflow-githubworkflowsandroidyml)).
- [x] M0-4a `rust/website.ref` pin, read-only deploy key `WEBSITE_REPO_KEY`, and `scripts/build-rust.sh` for local builds.
- [x] M0-4b `release.yml`: on a `v*` tag, signed universal and per-ABI APKs on a GitHub Release and the AAB on the Play internal track ([design §16.4](design.md#164-release-workflow-githubworkflowsreleaseyml)); upload keystore and Play service account in repository secrets.
- [ ] M0-5 Play Console app, package name, Play App Signing, internal track; upload a first build from CI.

**Rust bridge**
- [x] M0-6 **[web]** Create `crates/ts-mobile` with UniFFI exports for `bible-ref` and `tamil-norm` ([design §9](design.md#9-shared-rust-code)).
- [x] M0-7 `:core:rust`: `cargo-ndk` Gradle task for arm64-v8a, armeabi-v7a, x86_64; generated Kotlin bindings; size check < 1 MB per ABI.
- [x] M0-8 The website's reference fixture list (`data/fixtures/references.tsv`, 190 cases) through the app's binding of the Rust parser: `ReferenceFixturesTest`, on the JVM with the host build of ts-mobile rather than on a device (same Rust code; the Android build of it is exercised by the app on devices).

**Pack pipeline (thin slice)**
- [x] M0-9 **[web]** Create `crates/pack-build`; build a Bible pack for one book with the schema in [design §7.3](design.md#73-bible-pack-schema), including `verse_fts` and `verse_tri`.
- [x] M0-10 **[web]** zstd compression, SHA-256, `catalogue.json` with Ed25519 signature; determinism test.
- [x] M0-11 Local pack server for `dev` (static file server) and a script to publish to it.
- [x] M0-12 `:core:data`: `BundledSQLiteDriver`, `PackRegistry` opening a pack read-only, a smoke test that reads John 3 and runs an FTS5 query.

**Design system base**
- [x] M0-13 `:core:design`: colour tokens light/dark, bundled subset fonts, typography scale, shapes ([design §5](design.md#5-design-system)).

**Test infrastructure** ([design §15](design.md#15-testing))
- [x] M0-14 `:core:testing` (kept in core:data's tests for now): `FixturePacks` writes Bible and cross-reference packs with pack-build's schema from real chapters under test resources (John 3, Psalm 23, Genesis 1 in IRVTAM; John 3 in BSB); `FakeCdn` on the JDK's HTTP server with Range support, a request log and fault injection (status codes, dropped connections). JVM tests use the desktop bundled SQLite and a host build of ts-mobile.
- [ ] M0-15 Roborazzi screenshot setup with the width-class × theme × language × font-scale matrix. Started: Roborazzi + Robolectric in :core:designsystem (maps, light/dark, Tamil/English); baselines in src/test/screenshots.
- [ ] M0-16 Gradle Managed Devices (API 26 low-RAM, API 35) and a `nightly.yml` workflow for instrumented tests.
- [x] M0-17 Network guard (`Http.guard`, `Supabase.guard`) for tests and StrictMode (logging) in debug builds.

### Exit criteria
- Every PR produces a debug APK artifact; a test tag produces a signed APK on a GitHub Release and an AAB on the internal track.
- A device test reads a verse from a downloaded pack and finds it with a Tamil FTS5 query normalised by Rust.
- Reference fixtures pass on Android.

---

## M1 · Offline reader (5 weeks)

**Goal:** a user installs the app and reads any chapter at once, online or from downloaded packs, fast, on a phone. Downloads and updates happen automatically in the background. Stats for chapters viewed and verses read reach Supabase with city-level location.

**Requirements:** A-1.1–A-1.4, A-2.1–A-2.8, A-3.1 (Reader and Standard), DL-1–DL-10, ON-1–ON-8, ST-1–ST-9, NF-1–NF-3, NF-5–NF-7, NF-9, NF-10, NF-13.

### Tasks

**Packs and catalogue**
- [x] M1-1 **[web]** `pack-build` for all Bible versions (IRVTAM, TCV, TOV, BSB, WEB, KJV) and the cross-reference pack.
- [x] M1-2 **[web]** CI workflow `packs.yml`: build, sign, upload to R2 under immutable paths, catalogue last. Done in the website's deploy.yml ("Build and publish app packs").
- [x] M1-3 **[web]** R2 bucket and custom domain for packs; CORS not needed (no browser access). Bucket ts-packs behind packs.tamilaudiobible.com.
- [x] M1-4 `CatalogueRepository`: fetch, verify signature, cache; remote config values; all locations as paths.
- [x] M1-4a `OriginResolver`: signed `bootstrap.json` from two providers, last-good copy in DataStore, built-in fallback, ordered origins with one-retry failover and 10-minute down marking.
- [ ] M1-4b **[web]** Publish scripts with targets `vercel`, `r2` or both, writing identical path layouts; `bootstrap.json` published to both providers.
- [x] M1-4c `scripts/check-origin.sh` conformance test (Range, cache headers, content types, SHA-256), run nightly against every configured origin (`origins.yml`). Both origins pass (5 Oct 2026); chapters on Vercel are edge-cached (HIT) though sent with `max-age=0`.
- [x] M1-5 `PackDownloadWorker`: foreground `dataSync` service, progress notification, Range resume, Wi-Fi-only option, pause/cancel ([design §7.7](design.md#77-download-and-install)).
- [x] M1-6 Verify → decompress → integrity check → atomic install; `installed_pack` table; space check.
- [x] M1-7 Automatic pack updates: `CatalogueRefreshWorker` (daily and on app start), updates on unmetered networks by default, setting for mobile data or off, side-by-side install and atomic swap, "Recently updated" list ([design §7.10](design.md#710-keeping-content-up-to-date)).
- [x] M1-8 Downloads screen: catalogue list, sizes, install state, update badges, delete, storage total.
- [x] M1-9 Onboarding: interface language, starter set, reader opens at once using online reading and switches to the pack when installed; "Skip downloads"; offline first-run retry screen.

**Online reading** ([design §7.9](design.md#79-online-reading))
- [x] M1-9a `ContentSource`: pack → online cache → network resolution for chapters, cross-references and audio timings.
- [x] M1-9b Content manifest client (`/content/manifest.json`) and chapter fetcher with timeouts, retry and request coalescing.
- [x] M1-9c Online cache: `online_cache` table keyed by content path (not URL), LRU over 50 MB (keep at least 200 chapters), size setting, clear action.
- [x] M1-9d Stale-while-revalidate when the content build changes; in-place UI update.
- [x] M1-9e Prefetch of neighbouring chapters (±1, next 3 on Wi-Fi); data-saver setting for mobile data.
- [x] M1-9f "Download this Bible" suggestion after 10 online chapters; offline-and-uncached state with a download action. Built (banner, "Not now" remembered per version, download button on the offline error); checked on the Pixel 9 (offer after ten online chapters, prefetched chapters counted).
- [x] M1-9g Downloads screen shows packs and the online cache separately (its own row, size, Clear).

**Reader**
- [x] M1-10 `ChapterRepository`: load chapter JSON, parse, LRU cache, neighbour prefetch.
- [x] M1-11 `ChapterUi` builder: blocks → `AnnotatedString`s with verse links, footnote callers, `wj` spans, headings, poetry indents.
- [x] M1-12 Reader screen: `LazyColumn` of blocks, `HorizontalPager` across all 1,189 chapters, prev/next buttons.
- [x] M1-13 Reader and Standard formats; paratext toggles (intros, headings, footnotes, cross-reference markers).
- [x] M1-14 Footnote popover/sheet; licence footer per version; About screen with all licences.
- [x] M1-15 Book/chapter/verse picker with Tamil and English names; current position marked.
- [x] M1-16 Reference box using `bible-ref` with suggestions; Tamil numerals.
- [x] M1-17 Verse selection and action bar: copy, share (text + website link); multi-verse selection.
- [x] M1-18 Position anchor, back stack with restored scroll, predictive back; continue reading on launch.
- [x] M1-19 Reader settings sheet: font size steps, line height, Tamil typeface, theme; DataStore persistence.
- [x] M1-20 Interface strings in Tamil and English; per-app language switch.

**Stats**
- [x] M1-21 **[web]** Migration: `source`, `event_id`, `app_version`, `window_class`, `offline` columns; widen `kind`; `track_app_batch(jsonb, text, text, text)`, anon-executable and validating like `track()`; pgTAP tests ([design §12.4](design.md#124-server-side-website-repo-supabasemigrations20261004100000_app_analyticssql)). Merged (samueldotj/tamilscripture.com#3) and live on 4 Oct 2026; the Pixel 9 uploaded its queue.
- [x] M1-21a **[web]** `/api/t/app` Vercel route: size limits, Vercel geo headers, per-address rate limit, one `track_app_batch` call per batch, `{accepted:[ids]}` ([design §12.3](design.md#123-collector-why-through-the-websites-vercel-function)). JWT verification moves to M6.
- [x] M1-22 `StatsRecorder` channel → Room `pending_event`; retention cap; opt-out setting.
- [x] M1-23 `StatsSyncWorker`: batching, back-off, delete on confirmation, expedited run on background.
- [x] M1-24 `VisibleVerseTracker` for `read` events (60% visible for 2 s, confirmed) ([design §6.6](design.md#66-verse-read-tracking-for-stats)); `view` events with `source` pack/cache/online.
- [x] M1-25 Debug stats screen.

**Performance**
- [x] M1-26 `:benchmark` module: cold-start and chapter-swipe Macrobenchmarks and the Baseline Profile generator (`./gradlew :benchmark:connectedBenchmarkAndroidTest`), against the app's `benchmark` build (release code, names kept). Profile in app/src/main/baseline-prof.txt with ProfileInstaller. Galaxy Tab S11, 4 Oct 2026: cold start to first frame, median 212 ms without the profile, 189 ms with it; swiping five chapters, frame CPU time P50 4.4 ms, P90 16.1 ms. Generating the profile in CI needs a device and is open.
- [x] M1-27 Airplane-mode integration test (fails on any network call while reading downloaded content): `AirplaneModeTest` reads a chapter, its cross-references and searches from fixture packs with every request failing and counted.
- [x] M1-28 Online-reading tests (`OnlineReadingTest`): no packs installed, cache hit with the server stopped, revalidation on a new build ([design §15.4](design.md#154-what-specific-requirements-need)).
- [x] M1-29 Download fault tests (`PackDownloadTest`, the real worker against `FakeCdn`): a dropped connection resumes by Range, a wrong checksum retries then fails without installing, an install cut short leaves the old version readable, automatic updates wait for Wi-Fi and storage.

### Exit criteria
- Cold start to the last chapter < 1.0 s at p90 on a Pixel 6a; chapter change < 100 ms.
- With the network off, every downloaded chapter opens, and the airplane-mode test passes.
- With nothing downloaded, any chapter opens online in < 1 s on 4G, and opens again offline from the cache.
- A pack published with a new version updates itself on Wi-Fi with no user action.
- A killed download resumes and never leaves a broken pack.
- `view` and `read` events from a test device appear in `analytics_events` with `source = 'android'` and a city, once each.

---

## M2 · Every screen size (3 weeks)

**Goal:** the reader is excellent on foldables, tablets and ALOS, with keyboard and mouse, and passes Play's large-screen quality checks.

**Requirements:** FF-1–FF-10, A-2.10, NF-8.

### Tasks
- [ ] M2-1 `NavigationSuiteScaffold` with top-level destinations (Read, Search, Plans, Library, Settings).
- [ ] M2-2 Reader in `SupportingPaneScaffold`; book rail list pane on large windows; max text width. Built: on expanded windows one study pane beside the text with Commentary, People and places, and Original words tabs (verse actions open them there instead of sheets).
- [ ] M2-3 `PaneDivider`: drag, keyboard adjust, snap points, hinge snapping. Built: drag, arrow keys when focused, snap to a third and a half, kept between a quarter and two thirds; hinge snapping not yet.
- [ ] M2-4 Fold postures: tabletop and book layouts via `WindowInfoTracker`. Built for tabletop: text above the hinge, player or chapter controls below (WindowInfoTracker); book posture not yet. Untested on a foldable.
- [ ] M2-5 `ShortcutRegistry`: all shortcuts in FF-5; `onProvideKeyboardShortcuts`; Ctrl+/ overlay. Reader keys listed in the system helper (Meta + /); a Ctrl+/ overlay of our own not yet.
- [ ] M2-6 Focus order and arrow-key verse navigation; visible focus indicators. Arrow keys move between verses and scroll them into view; visible focus on the pane divider.
- [ ] M2-7 Right-click context menu on verses; hover states; Ctrl+scroll font size.
- [ ] M2-8 Drag selected verses out as text.
- [x] M2-9 Open in new window (multi-instance); per-window state. `ReaderWindowActivity` (Ctrl+N or the verse menu) opens as its own task next to the current window; verified in split screen on the Pixel 9.
- [~] M2-10 State survives rotation, fold, resize and window moves (tests for each). Started: every route round-trips through its saved form (`RoutesSaveTest`), so a back stack restored after process death cannot fail; links survive process death (checked on the Galaxy Tab). Device runs for rotation and fold are open.
- [ ] M2-11 **[web]** `/.well-known/assetlinks.json`; App Links for chapter, verse and shorthand URLs.
- [ ] M2-12 Accessibility pass: TalkBack verse labels, Tamil/English locale spans, 200% font scale, contrast on highlight colours, Accessibility Scanner clean. Started: scripture text carries its version's language (TalkBack reads Tamil in a Tamil voice, both columns in dual view); verse card labels wrap or shrink instead of clipping at 200% (screenshot test). Accessibility Scanner run still to do on a device. Paragraph formats: each verse is a link, so TalkBack can step to and select a verse (`ParagraphLinksTest`).
- [~] M2-13 Screenshot tests at compact, medium, expanded, large; light/dark; Tamil/English. Started: `ReaderMatrixScreenshotTest` renders the reader frame (top bar, Study Bible text with cross-references, chapter buttons) in all 16 combinations. The wide reader layout and other screens are open (they need app services faked).
- [ ] M2-14 Device runs: Pixel Fold, Pixel Tablet, an ALOS/ChromeOS device with keyboard and mouse, a 2 GB Android 8 phone.

### Exit criteria
- Play large-screen quality checklist Tier 2 passes on tablet and ALOS.
- Every FF requirement demonstrated on a real device.
- No layout breaks between 320 dp and 2,560 dp width while resizing live.

---

## M3 · Search and study (4 weeks)

**Goal:** fast offline search in Tamil and English, cross-references, dual view and the Study Bible format.

**Requirements:** A-3.1 (Study Bible), A-3.3–A-3.5, A-4.1–A-4.5, A-4.7, A-4.8, A-5.1, NF-4.

### Tasks
- [x] M3-1 Romanised-Tamil search ("anbu" → அன்பு): the website's converter (`lib/search/romanised.ts`, TypeScript, not Rust) ported to Kotlin as `Romanised`, with the same rule: the Tamil reading is used when the words as typed find nothing, otherwise offered as a chip. Website `/search?q=` and `/irvtam+kjv/…` dual links open in the app.
- [x] M3-2 `SearchRepository`: reference detection, phrase/term parsing, normalisation, FTS5 `MATCH`, BM25 order, paging ([design §8.3](design.md#83-query-pipeline)).
- [x] M3-3 Fewer than 5 hits on the device: the website's search (trigram similarity) adds near spellings after them when online. Packs carry no trigram index (design §8.3).
- [x] M3-4 Match highlighting by token normalisation.
- [x] M3-5 Results grouped by book with counts; testament/book filters; multi-version search in parallel. Scope row done (whole Bible, testaments, books with counts on device; testaments online via `bmin`/`bmax`); "All versions" searches every version in parallel and merges in canonical order, applying the romanised-Tamil rule across the versions ("love" found in BSB is not read as லோவெ in TCV; `MergeVersionsTest`). Checked on the Pixel 9 (5 Oct 2026): "love" 478 on device, Old Testament 241 + New Testament 237, a book filter, All versions 1,398.
- [ ] M3-5a Search polish from the Pixel 9 check: a book chip's device count (Exodus · 2) differs from the results header once near spellings are added online (5 · online); the scope row scrolls back to the start after a book is chosen, hiding the selected chip; the loading spinner pushes the scope row down while results load. Consider not offering the Tamil reading for an English word found as typed (the "Search “லொவெ” instead" chip).
- [x] M3-6 Autocomplete (book names, recent searches); common searches from the website's `/api/common-searches` (once per session).
- [x] M3-6a Online search through the website's `/api/search` for versions that are not downloaded, mapped to the same result model with an "online results" label.
- [x] M3-7 `search` stats events; queries also feed `search_log` server-side.
- [x] M3-8 Cross-references: markers, pane/sheet list with verse text, top 10 + expand, back entry on follow, hover preview on large windows.
- [x] M3-9 Study Bible format: one verse per item, inline cross-references.
- [x] M3-10 Dual view: alignment by verse number (as the website), two-column rows with a pinned version header (600 dp+), stacked cards (compact), shared selection, missing-verse cells. A versification table is still to come (design §6.5).
- [x] M3-11 Version switcher and "compare with" control (study settings → Translation, or V on a keyboard).
- [x] M3-12 Search benchmark in `:benchmark` (`SearchBenchmark`, the `pack-search` trace section): Pixel 9, IRV pack, 5 Oct 2026, median 4.7 ms for a Tamil word, 2.6 ms for a phrase, 3.7 ms for romanised input (two queries); design target 50 ms.

### Exit criteria
- Search first results < 300 ms on a Pixel 6a for the 50 most common queries.
- Tamil search results equal the website's for a fixture set of 100 queries (same verse set, order may differ).
- Dual view scrolls in step at 60 fps.

---

## M4 · Commentaries (2 weeks)

**Goal:** five commentaries (Henry, Calvin, Geneva, Poole, Trapp) readable online or downloaded, and shown beside or under the text. The Early Church Fathers are left out for now.

**Requirements:** A-5.2–A-5.4.

### Tasks
- [x] M4-1 **[web]** `pack-build` commentary packs from `bible-commentaries/dist/commentary/{version}` for the five commentaries, with Tamil drafts and provenance. Live since 4 Oct 2026 (Henry 22.8 MB, Calvin 10.8, Trapp 6.7, Poole 6.2, Geneva 2.1); the deploy fetches a commentary version only when `latest.json` changes. Geneva read offline on the Pixel 9.
- [x] M4-2 `CommentaryRepository` through `ContentSource`: pack, then cache, then the existing commentary CDN (`latest.json`); Tamil when available and the UI is Tamil, else English with the "translation coming" label.
- [x] M4-3 Commentary focus pane (expanded+) with source tabs and "also in" previews from other installed commentaries.
- [x] M4-4 Inline commentary cards under verses (compact/medium); setting on/off and default source.
- [x] M4-5 Commentary text renderer: anchors, verse labels, footnotes, references as links.
- [x] M4-6 Attribution and licence strip per commentary (under each chapter, with a note that the Tamil is a draft).
- [x] M4-7 `commentary` stats events.

### Exit criteria
- Matthew Henry on John 3 opens offline in < 150 ms, and online in < 1 s on 4G when not downloaded.
- Pane and inline layouts switch correctly on resize.

---

## M5 · Audio Bible (3 weeks)

**Goal:** listen to any chapter, in the background, streamed or offline, with the verse being read highlighted.

**Requirements:** A-6.1–A-6.9.

### Tasks
- [ ] M5-1 **[web]** Audio index packs per version/recording from `chapters.tsv` and `timings/*.tsv`.
- [x] M5-2 `PlaybackService` (`MediaLibraryService`), ExoPlayer, media notification, lock screen, headphone and Bluetooth controls.
- [x] M5-3 Queue per book with auto-continue across chapters and books.
- [x] M5-4 URI resolution local → `audio` origin; `SimpleCache` with a path-only `CacheKeyFactory`; verse timings through `ContentSource` when the audio index is not downloaded.
- [x] M5-5 Player bar (compact) and rail-footer player (medium+); tabletop posture controls.
- [x] M5-6 Verse sync: highlight current verse, follow-audio scrolling, "play from here".
- [x] M5-7 Speed control, sleep timer, "Listen in IRV" for versions without audio.
- [x] M5-8 Audio downloads per book and per version; storage display; delete. Built: Downloads › Audio Bible, a book at a time (MP3s at their CDN paths plus verse timings), played from the device when present; checked on the Pixel 9 (a book downloaded, played in airplane mode).
- [ ] M5-9 Android Auto browse tree (versions → books → chapters). Built: PlaybackService is a MediaLibraryService with that tree, a chosen chapter queueing its book; not yet tried in a car or the Desktop Head Unit.
- [x] M5-10 `audio` stats events with seconds listened and offline flag. Done with the player (play, jump, next, end, time).

### Exit criteria
- One hour of background playback with the screen off, across chapter boundaries, without stalls.
- Offline playback works in airplane mode.
- Listening minutes from a test device appear correctly in `/mod/traffic`.

---

## M6 · Accounts and sync, one account with the website (5 weeks)

**Goal:** signed-in users get highlights, notes, bookmarks, history and settings on every device and on the website.

**Requirements:** A-2.11, A-7.1–A-7.8, X-1–X-9, NF-12.

### Tasks
- [x] M6-1 **[web]** Migrations: `bookmarks` (one per verse), `deleted_rows` with triggers, `export_my_data()` with bookmarks; pgTAP tests (website PR #8, 4 Oct 2026).
- [x] M6-2 **[web]** App sign-in callback: the website's `/auth/callback?app=1` hands the PKCE code to `tamilscripture://auth` (website PR #7), for magic links and Google alike.
- [ ] M6-2a Google Cloud: Android OAuth client in the website's existing project (package name, SHA-1 of the Play app-signing and debug keys); confirm Supabase automatic identity linking by verified email ([design §13.1](design.md#131-sign-in)).
- [x] M6-2b **[web]** Bookmarks on the website: verse-action-bar action (☆/★) and `/me/bookmarks`; bookmarks in `export_my_data()` (website PRs #8, #10).
- [~] M6-2c **[web]** Website refetches personal data on tab focus and every 60 s while visible; "Continue reading" uses the account's latest history when signed in; "Open in app" banner on Android ([design §13.5](design.md#135-app-and-website-parity)). Built: refetch on tab focus and every 60 s (website PR #9); "Continue reading" already uses the account's history. The "Open in app" banner waits for the Play listing.
- [x] M6-3 Sign-in in the browser with PKCE (email link or Google, the website's own providers), code exchanged in the app; no Android OAuth client needed. Credential Manager can follow once M6-2a exists.
- [x] M6-4 Local store for highlights, notes, bookmarks, history with dirty/deleted sets (one JSON file, `UserDataRepository`; small enough that Room is not needed). Plan progress still in plans.
- [~] M6-5 Highlights: whole-verse, four colours, change/remove (splitting rows like the website); list by colour. Word ranges from the website drawn on their words in their version (snapped to whole letters); word ranges made in the app with the stylus (M8-9). Word ranges by finger, and in the paragraph formats, are open.
- [x] M6-6 Notes: editor sheet (kept when the sheet is closed), notes under their verse on phones and in the margin on wide windows, notes list with search.
- [x] M6-7 Bookmarks (synced with `public.bookmarks`, one per verse) and history screens; pause and clear history.
- [x] M6-8 `SyncWorker`: push dirty rows (upsert; bookmarks by verse), deletes, visits via `record_visit`; then pull rows changed since the cursor and the `deleted_rows` since it, with a full pull on first sync or after 80 days ([design §13.3](design.md#133-sync-protocol)). Runs soon after a change, on sign-in and foreground, every 12 h.
- [x] M6-9 Settings sync through `profiles.settings` with the website's keys (language, version, Tamil font, headings, footnotes, xrefs, heat, commentary and its source); appearance and text size stay per device. Three-way: a local change since the last send goes up, otherwise the account's values come down. The website itself does not read them yet.
- [~] M6-9a Supabase Realtime subscription while in the foreground, 60 s pull fallback, pull on foreground and on reconnect. Built: a pull on coming to the foreground and every 60 s while there (cursor pulls, so a few small requests). Realtime is open.
- [x] M6-9b Sign-out sends what is pending, then wipes the account's local data; a dead session (refresh refused) signs the app out.
- [x] M6-9c "Open on website" action on the verse card, always in the browser (never back into the app).
- [x] M6-10 Export my data (`export_my_data`, saved as a JSON file) and delete account (`delete_my_account`).
- [~] M6-11 Sync tests: two devices + website, offline edits for 7 days, conflicting edits, deletes, clear history, both sign-in orders reaching one account, export from either side. Started: `SyncTest` runs two devices and the website against `FakeSupabase` (offline edits, deletes both ways, website edits and word-range rows, signing in after working signed out, clearing history, uniform upsert batches). A run against the real project with two devices is open.

### Exit criteria
- Signing in with Google in the app reaches the same account and data as signing in on the website, and so does an email link.
- A highlight, note, bookmark, history entry or plan tick made in either place appears in the other within 1 minute while both are open.
- Offline edits made over 7 days merge correctly.
- RLS tests prove one user cannot read another's rows through the API.

---

## M7 · Reading plans and widget → 1.0 (2 weeks)

**Goal:** reading plans at parity with the website; release 1.0 to production.

**Requirements:** A-8.1–A-8.3, NF-11.

### Tasks
- [x] M7-1 Port `schedule.ts` (built-in plans, rest days, Psalm 119 stanzas) to Kotlin; test against the website's outputs for every day of every plan.
- [x] M7-2 Plans Today, Browse and Stats (streak, calendar, list) screens.
- [x] M7-3 Community plans from `reading_plans` (published only), cached offline. Anon PostgREST as the website does; `fromRow` ported with tests.
- [x] M7-4 Progress local when signed out; joined with the account on first sign-in (earlier start, readings from both), then synced with `plan_progress`.
- [x] M7-5 Daily reminder notification with a chosen time (exact alarms not needed; inexact is fine). Built (Settings › Daily reminder; today's plan passages or the verse of the day); checked on the Pixel 9.
- [x] M7-6 Home-screen widget (Glance): today's passages with one-tap open. Built: verse of the day plus "Continue reading", refreshed when the app goes to the background; checked on the Pixel 9 and the Galaxy Tab S11.
- [x] M7-7 `plan` stats events. Done (`plan` events on ticking a passage).
- [ ] M7-8 Release prep: store listing in Tamil and English, screenshots for phone, tablet and ALOS, Data safety form, privacy policy update on the website.
- [ ] M7-9 Staged production rollout 10% → 50% → 100% with crash-free ≥ 99.5%.

### Exit criteria
- Plan schedules match the website exactly.
- 1.0 live on Play at 100% rollout.

---

## M8 · Study data, atlas, community and present mode (6 weeks, after 1.0)

**Goal:** the rest of the website's study features.

**Requirements:** A-2.9, A-3.2, A-4.6, A-5.5–A-5.7, A-8.4, A-8.5, FF-7.

### Tasks
- [ ] M8-0 Atlas renderer test (about 2 days): build option A (Compose) and option B (MapLibre + Android-drawn label images) behind `AtlasRenderer` with real map data; measure against the pass marks in [design §11.5](design.md#115-rendering-decided-by-a-test-in-m8-adr-12); record the result in ADR-12. Decided for option A (Compose): the native map renderer draws the website's chapter maps and the atlas from its GeoJSON, verified by Roborazzi screenshots (overview, journey, focused place; light/dark; Tamil/English). Record in ADR-12.
- [x] M8-1 **[web]** Study packs: Strong's (lexicon, occurrences, original words), persons, places, dictionary; `study.maps` (projected geometry at three detail levels, places, journeys, polities, church) and `study.maps.chapters` (static chapter maps) ([design §11.2](design.md#112-data)). Live: study.words 6.4 MB, study.people 1.7, study.dictionary 9.6, study.maps 2.4; each keeps the website's entities/ files under their paths.
- [x] M8-2 Original-words view per verse; Strong's page listing every verse in the current version. Built: verse card and menu → Original words sheet → Strong's page with every verse; Study › Root words browses the Strong's index. Checked on the Pixel 9 and the Galaxy Tab S11.
- [x] M8-3 Person, place and dictionary article screens with provenance badges; search sections. Built: person, place and dictionary article screens (Tamil paragraphs where drafted), Study › Dictionary. Checked on the Pixel 9 and the Galaxy Tab S11.
- [ ] M8-4 Study Bible side pane: places, persons, chapter map, original-language names. People and places sheet per chapter and verse with the chapter map (the website's SVG maps drawn natively, tap a place); place pages show their map. On wide windows the same lists open in the study pane.
- [x] M8-5 Atlas screen with the chosen renderer: base map, Places layer, rank-based labels with collision, Tamil labels in the reader's typeface ([design §11](design.md#11-atlas-and-maps)). Built: base map, places ranked by mentions with collision-free labels in the reader's language, journeys with numbered stops, tap for a place card; pinch, pan, double-tap and wheel zoom. Screenshot-tested. Checked on the Pixel 9 (5 Oct 2026): base map, labels in English and Tamil, +/- and double-tap zoom, pan, place card; a tap now goes to the named place (Jerusalem, not Gibeah). Pinch not exercised there (adb has no two-finger input).
- [x] M8-5a Camera: pinch, pan with fling, double-tap zoom in and two-finger-tap zoom out, zoom limits (out to about Europe-to-India, with the world filled in) and pan limits, animated fit-to-journey and glide to a chosen place, state saved (rememberSaveable).
- [x] M8-5b Selection: tap within 24 dp, a named place (its dot or its label) before an unnamed dot nearer the finger; details in a bottom card (compact), a narrower card at the side (medium) or a pane beside the map (expanded, with a journey's stops in order, tap to select); "Open" goes to the place page. Checked on the Galaxy Tab S11 in both orientations.
- [ ] M8-5c Journeys layer and list (grouped by period, colour plus dash legend, hover emphasis on large windows); Kingdoms timeline; Early church layer. Built: journeys as chips; Kingdoms timeline (Cliopatria rows, the website's year steps and hues, outlines thinned to 0.05°); Early church layer. Screenshot-tested; frame rate on a low-end phone still to measure.
- [x] M8-5d Mouse, keyboard and context menu on ALOS; place search on the map; App Links. Built: wheel zoom; arrow keys pan and +/- zoom; right-click menu (open the nearest place, centre or zoom here); place search; links for /atlas, /atlas/explore, /atlas/{journey}, /place/{slug}, /person/{slug}, /strongs/{num}, /dictionary (`DeepLinkTest`).
- [ ] M8-5g Label polish from the Pixel 9 check: labels slide under the zoom buttons and the system navigation bar; a few labels run over other places' dots (Marah over Ezion-geber's); some places have no Tamil name and show in English (Hatti, Pelusium).
- [x] M8-5e Static chapter maps in the Study pane (the website's SVG maps, drawn natively), with "Open in the atlas" fitting the atlas to the chapter's places.
- [~] M8-5f Accessibility: place semantics in rank order, list view, timeline and zoom controls; screenshot, gesture and Macrobenchmark tests. Built: zoom buttons with spoken labels; a list view (a journey's stops in order, or places by mentions) where a row opens the place and its map button shows it; screenshot tests. A Macrobenchmark for the atlas is open.
- [x] M8-6 Large single-verse view and share as image. Share as image built (Claude Design handoff, Reader Share Image 3a/3b): the verse card's Share ▾ menu (verse in its chapter, large text, share as image, open large text) and a sheet with a live preview, the website's six templates, icon, size (square, status, landscape), Tamil/English/both and light/dark, then Download to Pictures, Share or Copy; screenshot-tested. Large view: the verse card opens present mode on the verse (full screen, sized to fill, keys and taps to move on).
- [x] M8-7 Community highlight counts, book heatmap, heat overlay (from `/api/heat/{book}.json` and `all.json`, an hour in memory). Built behind the Study settings toggle, as on the website: verse tints in the reader and chapter tints in the book picker, in the website's quartiles. The API is empty until readers highlight.
- [ ] M8-8 Present mode: full screen, keyboard navigation, external display via `Presentation` on tablets and ALOS. Built: a passage verse by verse, full screen, sized to fill; clicker keys, taps, P from the reader; on a second display the verses go there through Presentation and the device shows now/next. Screenshot-tested; untested with a real second display.
- [~] M8-9 Stylus highlighting: in Study Bible, the pen drawn across a verse highlights the words it passes (snapped to whole words, live preview) as a word range in the last highlight colour; the eraser end or the side button removes word ranges. Fingers keep tap and selection. Unit-tested; untested with a real pen.

### Exit criteria
- Acts 13 shows its places on a map offline; "தமஸ்கு" in search opens Damascus.
- Tamil map labels (கொரிந்து, தமஸ்கு, ஸ்ரீ) render exactly as in the reader.
- Pan and zoom with Kingdoms on hold 60 fps on a Pixel 6a and at least 45 fps on a 2 GB Android 8 phone.
- A presentation runs from a tablet to an external display with keyboard control.

---

## Cross-cutting work in every milestone

- [ ] Add stats events for new features, with tests that they never block the UI.
- [ ] New content types go through `ContentSource`, so they work online and offline from the start.
- [ ] No host names in code, stored data or caches: only origin names and paths.
- [ ] Screenshot tests for new screens at all four width classes.
- [ ] Update the Baseline Profile when a new critical path is added.
- [ ] Tamil and English strings for every new string.
- [ ] Keep [requirements.md](requirements.md) and [design.md](design.md) current; record decisions as ADRs.

## Risks

| Risk | Effect | Mitigation |
|---|---|---|
| A licence changes for a text, commentary or recording | A pack must be withdrawn | The catalogue can hide a pack without an app release; audio can fall back to streaming |
| Vercel bandwidth from online reading and stats | Hosting cost | Content base URL is in the catalogue: mirror `/content` to R2 if needed; stats are batched to about one request per session |
| Tamil rendering differences on old Android versions | Broken conjuncts | Bundled fonts; screenshot tests on API 26 |
| ALOS device availability for testing | Desktop issues found late | Test on ChromeOS with Android apps as a proxy from M2; resizable emulator |
| Pack sizes grow (Tamil commentary drafts) | Slow downloads | Split large commentaries by testament; delta updates later |
| Supabase schema changes collide with website work | Broken web or app | All migrations in the website repo, reviewed together, pgTAP in CI |
| One developer | Slower delivery | Milestones are independently shippable; M8 can slip without blocking 1.0 |
