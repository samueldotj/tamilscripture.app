# Tamil Scripture Android App — Requirements

**தமிழ் வேதாகமம் — Android செயலி**

| | |
|---|---|
| Product | Native Android app for tamilscripture.com |
| Version | 0.1 draft, 3 October 2026 |
| Platforms | Android phones, foldables, tablets, Aluminium OS (ALOS) laptops and desktops |
| Backend | Supabase (stats, accounts, user data), Cloudflare R2 CDN (content packs, audio) |
| Package name | `com.tamilscripture.app` |
| Priority scheme | Must / Should / Could |

This document says **what** the app must do. **How** it is built is in [design.md](design.md). **When** each part is built is in [roadmap.md](roadmap.md).

Requirement IDs in square brackets, such as [R-1.2], point to the website's requirements (`tamilscripture.com/docs/requirements.md`). Using the same IDs keeps the two products in step.

## Contents

1. [Overview and goals](#1-overview-and-goals)
2. [Platforms and form factors](#2-platforms-and-form-factors)
3. [Features](#3-features)
4. [Offline content, online reading and updates](#4-offline-content-online-reading-and-updates)
5. [Stats collection](#5-stats-collection)
6. [Non-functional requirements](#6-non-functional-requirements)
7. [Decisions and open questions](#7-decisions-and-open-questions)

---

## 1. Overview and goals

The app is a native Android build of tamilscripture.com. The download contains only application code. Downloaded content is read from storage on the device. Content that is not downloaded can still be read online and is cached. Reading and listening stats are reported to Supabase in the background.

### Goals

1. **Match the site.** Same features and visual design as tamilscripture.com, in a native app.
2. **Every screen size.** One adaptive app designed from the start for phones, foldables, tablets and ALOS laptops and desktops.
3. **Fast.** Cold start to a readable chapter in under 1 second on a mid-range phone. Downloaded content never waits on the network.
4. **Offline-first, online when needed.** Users choose which Bibles, commentaries and audio to download, and downloaded content works with no connection. Anything not downloaded can still be read online. Content stays up to date automatically.
5. **Analytics.** Every verse read and every chapter listened to is recorded locally and synced to Supabase asynchronously, without slowing the UI.

### Non-goals for version 1

- iOS, wearables and Android TV.
- Social features: comments, or sharing reading plans with groups.
- Staff features of the website: review queue, `/mod/traffic`, the reading-plan editor and role management stay on the web.

### Terms

| Term | Meaning |
|---|---|
| Pack | A downloadable unit of content: one Bible version, one commentary, cross-references, a study-data set, or the audio for one book of one version. |
| Catalogue | The list of all packs the server offers, with sizes and versions. |
| Verse ID | `{USFM book}.{chapter}.{verse}`, such as `JHN.3.16`. The same in every version. |
| Window size class | Android's width buckets: compact, medium, expanded, large, extra-large. Layout is chosen from these, not from device type. |
| ALOS | Aluminium OS, Google's Android-based operating system for laptops and desktops. |

---

## 2. Platforms and form factors

The layout is chosen from window size classes, not device type. The same screen code serves a 6-inch phone and a resizable ALOS desktop window, and it re-flows live as the window changes.

| Window width class | Typical device | Layout |
|---|---|---|
| Compact (< 600 dp) | Phone portrait, folded foldable | Single pane. Bottom navigation bar. Commentary and notes open as a bottom sheet. |
| Medium (600–839 dp) | Phone landscape, unfolded foldable, small tablet | Navigation rail. Reader plus an optional side pane for commentary or a parallel version. |
| Expanded (840–1199 dp) | Tablet landscape, ALOS window | Navigation rail. Two panes: reader plus commentary or a parallel version, with a draggable divider. |
| Large / Extra-large (≥ 1200 dp) | ALOS desktop, large tablet | Permanent navigation drawer. Up to three panes: book and chapter list, reader, commentary or study pane. |

| ID | Requirement | Priority |
|---|---|---|
| FF-1 | Minimum Android 8.0 (API 26). Target the latest SDK. Edge-to-edge drawing on all devices. | Must |
| FF-2 | Never lock orientation, and never set fixed aspect ratios. The app stays usable in multi-window, split screen and free-form windows down to 320 dp wide. | Must |
| FF-3 | Support foldable postures. Tabletop: the reader on the top half, audio controls on the bottom half. Book: panes on either side of the hinge. | Should |
| FF-4 | Keep state across rotation, fold and resize: scroll position, selected verses and audio playback do not reset. | Must |
| FF-5 | Keyboard: Ctrl+F search, ← → and PgUp/PgDn for chapters, Space play/pause, Ctrl+B bookmark, Ctrl +/− font size, Esc closes panels, Ctrl+/ shows a shortcut list. [R-1.6] | Must |
| FF-6 | Mouse and trackpad: hover states, right-click context menu on verses, Ctrl+scroll to change font size. | Must |
| FF-7 | Stylus: select verses and highlight by drawing over text. | Could |
| FF-8 | Drag and drop: drag selected verses out as text into other apps. | Should |
| FF-9 | Multiple windows: open a second window, for example two books side by side on ALOS. | Should |
| FF-10 | Meet Google Play's large-screen app quality guidelines at Tier 2 ("optimized"), aiming for Tier 1 ("differentiated") on tablets and ALOS. | Must |

---

## 3. Features

The feature set follows the website's spec (`docs/requirements.md` and `docs/feature_*.md`, September 2026).

### 3.1 Bible texts

| ID | Requirement | Priority |
|---|---|---|
| A-1.1 | Versions at launch: IRVTAM (default), TCV, TOV (1957 Old Version, public domain), BSB (default English), WEB, KJV. Each is a separate downloadable pack. [R-3.x] | Must |
| A-1.2 | Keep all paratext: book introductions, section headings, poetry lines, paragraphs, footnotes, `\x` cross-reference markers, verse bridges, split verses, and words of Jesus in red (`wj` spans). [R-3.2, R-3.3] | Must |
| A-1.3 | Align verses across versions using the shared versification map. [R-3.4] | Must |
| A-1.4 | Show each version's licence and source in the reader footer and on an About screen. [R-3.5] | Must |
| A-1.5 | New versions, including other Indian languages, arrive as new packs in the catalogue with no app update. [R-3.6] | Should |

### 3.2 Reading and navigation

| ID | Requirement | Priority |
|---|---|---|
| A-2.1 | Book, chapter and verse picker. Books grouped by Old and New Testament, with Tamil and English names. The picker shows the current position. [R-1.1] | Must |
| A-2.2 | Reference box that accepts English and Tamil shorthand and Tamil numerals, such as `jn3.16`, `1 கொரி 13.4-7` and `சங் ௨௩`. It behaves exactly like the website's reference box. [R-1.2] | Must |
| A-2.3 | In-app back stack that restores passage, scroll position, version and format. System back and the predictive back gesture both work. Changing a display setting does not add a back entry. [R-1.3] | Must |
| A-2.4 | Previous and next chapter by buttons, horizontal swipe and keyboard. [R-1.5] | Must |
| A-2.5 | Tapping a verse number selects the verse. Tapping more numbers extends the selection. An action bar offers copy, share, highlight, note, cross-references, listen from here, commentary and original words. [R-1.7] | Must |
| A-2.6 | Independent toggles for introductions, headings, footnotes and cross-reference markers. [R-1.8] | Must |
| A-2.7 | Reader settings: font size (5 steps), line height, Tamil typeface (Mukta Malar, Noto Sans Tamil, Noto Serif Tamil), light/dark/system theme. [R-1.10, R-1.11] | Must |
| A-2.8 | Continue reading: the app opens on the last passage read. [R-10.6] | Must |
| A-2.9 | Large single-verse view in Tamil and English, shared as text or as an image. [R-1.16] | Should |
| A-2.10 | tamilscripture.com links open in the app when it is installed. Shared links point to the website so anyone can open them. [R-1.12, R-1.13] | Should |
| A-2.11 | Reader settings sync across devices when signed in. [R-1.9] | Should |

### 3.3 Display formats and dual view

| ID | Requirement | Priority |
|---|---|---|
| A-3.1 | Three formats: Reader, Standard and Study Bible. One control switches between them without losing the scroll position beyond the nearest verse. [R-8.1, R-8.2] | Must |
| A-3.2 | In Study Bible format, places, persons, the chapter map and original-language names appear in a side pane, or a sheet on compact windows. [R-8.4] | Should |
| A-3.3 | Any two versions side by side, aligned verse by verse. Columns on medium and wider windows; interleaved verses on compact windows. The columns scroll together, and selecting a verse selects it in both. [R-9.1, R-9.2] | Must |
| A-3.4 | Where one version lacks a verse, its cell shows a dash and a note rather than shifting alignment. [R-9.4] | Should |
| A-3.5 | Highlights, notes and selection behave the same in every format and in both columns. [R-8.3, R-9.5] | Must |

### 3.4 Search

| ID | Requirement | Priority |
|---|---|---|
| A-4.1 | One search box for references and words. A reference opens the passage; anything else shows results. [R-5.1] | Must |
| A-4.2 | Full-text search on the device, over downloaded versions only. Words in quotes match as an exact phrase. [R-5.2, R-5.3] | Must |
| A-4.3 | Tamil fuzzy matching that gives the same results as the website: Unicode normalisation, long and short vowel folding, ன/ண/ந, ல/ள/ழ and ர/ற folding, case-suffix stripping, and romanised Tamil input such as `anbu`. English matching ignores case and tolerates misspellings. [R-5.4] | Must |
| A-4.4 | Results are grouped by book in canonical order, with a count per book and matched words emphasised. Filters by testament or book. [R-5.5, R-5.8] | Must |
| A-4.5 | Autocomplete for book names in both scripts and for the user's recent searches. [R-5.7] | Should |
| A-4.6 | Places, people and dictionary articles appear in search results when their packs are downloaded. [R-11.5, R-12.7, R-13.5] | Should |
| A-4.7 | Common searches across all users, refreshed from the server when online and cached for offline use. [R-5.6] | Could |
| A-4.8 | Search works with no network connection for downloaded versions. | Must |
| A-4.9 | When the version being searched is not downloaded and the device is online, search uses the website's search service, with the same results grouping. | Should |

### 3.5 Study content

| ID | Requirement | Priority |
|---|---|---|
| A-5.1 | Cross-references (OpenBible.info plus USFM `\x`), top 10 by votes with an option to expand. Following one adds a back entry. [R-7.1–R-7.4] | Must |
| A-5.2 | Commentaries: Matthew Henry, Calvin, Geneva notes, Poole and Trapp. Show the Tamil draft where it exists; otherwise English, labelled "translation coming". Each commentary is a separate download. The Early Church Fathers are not included for now. | Must |
| A-5.3 | Commentary layout follows the site: a focus pane beside the text on expanded and wider windows, and inline cards under the verses on compact and medium windows. | Must |
| A-5.4 | Each commentary shows its title, year, attribution and licence. | Must |
| A-5.5 | Strong's concordance: tap an original word to see every verse it occurs in, in the version being read. | Should |
| A-5.6 | Dictionary articles (Easton, Smith's, Aquifer), person pages and place pages, with a provenance badge (AI draft, community-corrected, owner-authored). [R-12, R-13] | Should |
| A-5.7 | Atlas: an offline map of the biblical world with places, journeys, kingdoms over time and the early church, with zoom, pan and place selection on every screen size, and Tamil place names rendered correctly. Static chapter maps in the Study pane. No external map or tile service. [R-11, R-14] | Could |

### 3.6 Audio Bible

| ID | Requirement | Priority |
|---|---|---|
| A-6.1 | Play a chapter with play, pause, skip back and forward, previous and next chapter, and auto-continue into the next chapter. | Must |
| A-6.2 | Background playback with a media notification, lock-screen controls, headphone buttons, Bluetooth and Android Auto. | Must |
| A-6.3 | Stream when online, or play from downloaded audio, by book or by whole version. | Must |
| A-6.4 | Highlight the verse being read, and offer "play from here" on any verse. | Must |
| A-6.5 | Playback speed control. | Must |
| A-6.6 | Reading another chapter while listening does not stop playback. "Back to the chapter being read" is one tap away. | Must |
| A-6.7 | Where the current version has no recording (TCV), offer one in the same language ("Listen in IRV"). | Should |
| A-6.8 | Sleep timer. | Should |
| A-6.9 | Show each recording's narrator, licence and attribution. | Must |

### 3.7 Personal data

Reading never requires an account. Signing in adds memory and sync.

| ID | Requirement | Priority |
|---|---|---|
| A-7.1 | Sign in with Google or an email magic link, using the same accounts as the website. [R-10.1, R-10.2] | Must |
| A-7.2 | Highlights (whole verses or a word range) in yellow, green, blue or pink. [R-10.12, R-10.13, R-10.15] | Must |
| A-7.3 | Notes up to 5,000 characters on a verse or range, in Tamil or English, saved automatically. Optional margin display. [R-10.8, R-10.9, R-10.16] | Must |
| A-7.4 | Bookmarks: save a verse or chapter to return to. | Should |
| A-7.5 | Reading history grouped by day, filterable by book, with pause and clear. [R-10.5, R-10.7] | Must |
| A-7.6 | Highlights, notes and bookmarks attach to the verse, not the version, so they show in every version. [R-9.5] | Must |
| A-7.7 | Changes made offline sync when the device is back online. Changes made on the website appear in the app, and the other way round (see 3.9). | Must |
| A-7.8 | Export all personal data and delete the account. [R-10.4] | Must |

### 3.8 Reading plans, community and presentations

| ID | Requirement | Priority |
|---|---|---|
| A-8.1 | Reading plans: the four built-in plans (Bible in 1 year, Bible in 2 years, New Testament in 6 months, Psalms in 6 months) and published community plans, with Today, Browse and Stats screens. Progress works signed out and syncs when signed in. | Must |
| A-8.2 | Optional daily reading-plan reminder notification at a time the user chooses. | Should |
| A-8.3 | Home-screen widget showing today's reading-plan passages or a verse. | Should |
| A-8.4 | Community highlight counts, a heatmap per book and an optional heat overlay, refreshed when online. Counts below 3 are never shown. [R-2.1–R-2.4] | Could |
| A-8.5 | Present mode for verse presentations: full screen, keyboard navigation, and output to an external display on tablets and ALOS. [R-17] | Could |

### 3.9 One account across the app and the website

A reader can move between the app and tamilscripture.com freely. They sign in with the same account and find the same personal data in both places.

| ID | Requirement | Priority |
|---|---|---|
| X-1 | **Same account.** Signing in with Google or with an email address gives the same account in the app and on the website. A person who signed up on one never needs a new account on the other. | Must |
| X-2 | **Linked sign-in methods.** If someone uses Google on one and an email link on the other, and both use the same verified email address, they reach the same account and the same data. | Must |
| X-3 | **All personal data in both places.** Every item in the table below can be seen, changed and deleted in the app and on the website. A change made in either place shows in the other. | Must |
| X-4 | **Freshness.** A change made in one place, while both are online, appears in the other within 1 minute, or as soon as the other app or page is next opened or brought to the front. Changes made offline in the app appear on the website within 1 minute of the app reconnecting. | Must |
| X-5 | **Deletes travel too.** Removing a highlight, note, bookmark or history entry in one place removes it in the other. Clearing history clears it everywhere. | Must |
| X-6 | **Continue anywhere.** "Continue reading" in the app and on the website opens the passage last read in either place, when signed in. | Should |
| X-7 | **One export, one delete.** Export from either place contains all data from both. Deleting the account from either place deletes it everywhere, including data on every signed-in device. | Must |
| X-8 | **Sign-out is local.** Signing out in the app removes that account's personal data from the device but leaves it on the server and on the website. | Must |
| X-9 | **Open in the other place.** The app offers "Open on website" for the current passage, and the website offers "Open in app" on Android when the app is installed. | Should |

**Personal data shared between the app and the website:**

| Data | Shared | Notes |
|---|---|---|
| Highlights (whole verse and word range) | Yes | Word-range highlights show in the version they were made in |
| Notes | Yes | |
| Bookmarks | Yes | New on the website too; the website gains a bookmarks list and a bookmark action |
| Reading history | Yes | The same rule on both: views of one chapter within 10 minutes count as one entry [R-10.5]. Pausing history applies everywhere. |
| Reading-plan progress | Yes | Joined plans, start dates and passages read |
| Reader preferences | Yes | Format, paratext toggles, font size step, Tamil typeface, theme, interface language, default versions. Each device can override them. |
| Presentations | Yes | Created on the website; opened and presented in the app (A-8.5) |
| Contributions and suggestions | Yes | Read-only in the app |
| Opt-outs | Yes | "Contribute to community counts" and "pause history" follow the account |
| Recent searches | No | Kept on each device, as the website does today |
| Downloads, online cache, audio position | No | Device-specific |
| Usage stats setting | No | Per device, because stats are anonymous and not tied to the account |

---

## 4. Offline content, online reading and updates

The app download holds no scripture. Content reaches the device in two ways:

- **Downloaded packs.** The user downloads a whole Bible, commentary or audio set. It works with no connection, and reading it never touches the network.
- **Online reading.** Any chapter, commentary or cross-reference list that is not downloaded is fetched on demand when online and kept in a cache, so it also opens offline later.

Both kinds are kept up to date automatically.

### 4.1 What can be downloaded

Sizes are measured from the website's current build (content `a9aa63f0e1`, commentary `164ecce549cc`). Commentary download sizes are estimates at about 3:1 compression.

| Pack | Contents | Raw size | Approximate download |
|---|---|---|---|
| IRVTAM, TCV, TOV | Text, paratext, footnotes, search index | ~20 MB each | ~5 MB each |
| BSB, WEB, KJV | Same, English | 9–13 MB | 3–4 MB |
| Cross-references | OpenBible.info + USFM `\x` | 14 MB | ~2 MB |
| Matthew Henry | Commentary, English + Tamil drafts | 155 MB | ~50 MB |
| Calvin | Commentary | 47 MB | ~16 MB |
| Poole, Trapp, Geneva | Commentary | 17–29 MB each | 6–10 MB each |
| Study data | Places, persons, dictionary, Strong's, maps | 228 MB | ~34 MB in several smaller packs |
| Audio, per version | IRVTAM, BSB, KJV, WEB (no TCV recording) | 2.3–2.8 GB per Bible | Per book |

### 4.2 Requirements

| ID | Requirement | Priority |
|---|---|---|
| DL-1 | **Onboarding.** First run asks for the interface language and offers a starter set: IRVTAM, BSB and cross-references, about 10 MB. If the device is online, the reader opens at once using online reading while the starter set downloads in the background. The user can skip downloading. | Must |
| DL-2 | **Catalogue.** A Downloads screen lists every available pack with its size, language, licence and whether it is installed or has an update. | Must |
| DL-3 | **Ready to use.** A pack is usable, including search, as soon as it finishes installing. The phone does no indexing. | Must |
| DL-4 | **Download control.** Downloads continue in the background with a progress notification, resume after interruption, can be limited to Wi-Fi, and can be paused or cancelled. | Must |
| DL-5 | **Integrity.** A pack is verified before use. A failed or interrupted download never leaves a half-installed pack visible. | Must |
| DL-6 | **Automatic updates.** When a newer version of an installed pack exists, the app updates it automatically in the background. The default is any unmetered network; a setting allows mobile data too, or turns automatic updates off. The Downloads screen shows what was updated. Notes, highlights and bookmarks survive pack updates. | Must |
| DL-7 | **Manage storage.** Show each pack's size on disk, total space used, the size of the online-reading cache, and delete actions for each. Audio can be added and removed one book at a time. | Must |
| DL-8 | **Content not downloaded.** Opening a chapter, commentary or cross-reference list that is not downloaded fetches it online, with no prompt. If the device is offline and the item is not cached, the app says so and offers to download the pack for next time. | Must |
| DL-9 | **No app update for content.** New packs and pack updates arrive without releasing a new app version. | Must |
| DL-10 | **Space check.** The app checks free space before a download and explains when there is not enough. | Must |

### 4.3 Online reading

| ID | Requirement | Priority |
|---|---|---|
| ON-1 | **Read anything online.** Every Bible version, commentary and cross-reference list in the catalogue can be read chapter by chapter without downloading its pack. Shared links open even when nothing is downloaded. | Must |
| ON-2 | **Same experience.** Online chapters look and behave exactly like downloaded ones: formats, toggles, selection, highlights, notes, dual view and audio. | Must |
| ON-3 | **Cache.** Chapters read online are cached, so they open offline afterwards. The cache holds at least the last 200 chapters and is limited to 50 MB by default (adjustable), oldest removed first. Downloaded packs are never evicted. | Must |
| ON-4 | **Speed.** An online chapter appears within 1 s on a 4G connection. The next and previous chapters are fetched ahead while reading online. | Must |
| ON-5 | **Fresh content.** When the server has newer content, cached chapters are refreshed: the cached copy shows at once and the new copy replaces it in the background. | Must |
| ON-6 | **Suggest download.** After a user reads 10 or more chapters of a version online, the app suggests downloading it, once. | Should |
| ON-7 | **Data saver.** A setting turns off online reading on mobile data. | Should |
| ON-8 | **Clear source.** The Downloads screen distinguishes downloaded packs from the online cache. | Should |

---

## 5. Stats collection

Every read and every listen is recorded on the device first, then sent to Supabase in the background. Collecting stats never blocks or slows reading, and stats are not lost while offline. App numbers appear beside website numbers in the website's staff traffic report.

### 5.1 Events

| Event | When it is recorded | Data (beyond the common fields) |
|---|---|---|
| `view` | A chapter is opened in the reader | version, book, chapter, format, second version if in dual view |
| `read` | A verse stays at least 60% visible for at least 2 seconds while the app is in the foreground. Once per verse per chapter visit. (Confirmed by the owner, 3 Oct 2026.) | verse ID, version, time visible, downloaded or online |
| `verse` | A verse is selected by tapping its number | verse ID, action taken (copy, share, highlight, note, cross-references, commentary) |
| `audio` | Player actions, as on the website: `play`, `next`, `jump`, `end`, `time` | version, recording, verse ID if started from a verse, seconds listened (1–3,600) for `time`, streamed or offline |
| `search` | A search is run | query, language, result count; never linked to the user (as R-5.9) |
| `commentary` | A commentary unit is opened | commentary, verse ID, language shown |
| `plan` | A reading-plan passage is marked read | plan key, day and track |
| `download` | A pack finishes installing or is deleted | pack ID, size, time taken |

**Common fields:** event time, app version, device class (phone, foldable, tablet, desktop), window size class, Android version, interface language, theme, and an anonymous install ID.

### 5.2 Requirements

| ID | Requirement | Priority |
|---|---|---|
| ST-1 | **Never in the way.** Recording an event costs the UI thread under 1 ms and never waits on storage or network. | Must |
| ST-2 | **Batched.** Events are sent in batches, not one request per event, and only when a network is available. | Must |
| ST-3 | **Delivered once.** Every event is delivered at least once and counted once: the server drops duplicates, and the device deletes events only after the server confirms them. | Must |
| ST-4 | **Bounded.** At most 10,000 unsent events or 30 days are kept, whichever comes first; the oldest are dropped. | Must |
| ST-5 | **Supabase.** Events are stored in the same Supabase project as the website's analytics, marked as coming from Android. | Must |
| ST-6 | **Location from IP.** Country, region and city are derived from the request's IP address on the server, as the website does. The IP address itself is never stored. | Must |
| ST-7 | **Anonymous.** The install ID is random and never tied to the user's account. Signed-in users are counted only through the same daily-rotating hash the website uses. No event carries note text or highlight colours. | Must |
| ST-8 | **Opt-out.** A setting, "Share anonymous usage stats", stops collection and deletes unsent events. | Must |
| ST-9 | **Abuse limits.** The server rejects events dated more than 30 days in the past or in the future, and rate-limits each install. | Must |
| ST-10 | **Reporting.** The website's traffic report can split by source (web or Android), device class and offline share, and shows verses read and minutes listened per book per day. | Should |

---

## 6. Non-functional requirements

| ID | Area | Requirement | Target |
|---|---|---|---|
| NF-1 | Download size | The app bundle holds code, fonts and icons only. No scripture. | < 15 MB download per device |
| NF-2 | Cold start | Launch to the last-read chapter on screen, on a Pixel 6a class phone | < 1.0 s at p90 |
| NF-3 | Chapter change | Next or previous chapter | < 100 ms |
| NF-4 | Search | Full-text search of one Bible, Tamil or English | First results < 300 ms |
| NF-5 | Smoothness | Reader scrolling and pane resizing | 60 fps, < 1% janky frames |
| NF-6 | No network on the read path for downloaded content | Reading, search and commentary from downloaded packs, and downloaded audio, make no network calls before the content shows | 0 blocking calls, tested in airplane mode |
| NF-7 | Battery | No polling. Background work runs only under system scheduling constraints. | Stats sync at most once per 15 minutes |
| NF-8 | Accessibility | TalkBack reads verses with their numbers and switches voice for Tamil and English. Text scales to 200%. Contrast meets WCAG 2.2 AA, including on highlight colours. Touch targets ≥ 48 dp. Full keyboard operation with visible focus. | Passes Accessibility Scanner |
| NF-9 | Languages | Interface in Tamil and English, switchable in the app independently of the Bible version. Western digits by default, Tamil numerals as an option. | All strings translated |
| NF-10 | Tamil rendering | Conjuncts and grantha letters render correctly on every supported Android version, using bundled fonts. | Visual test on API 26 and latest |
| NF-11 | Privacy | No third-party analytics or advertising SDKs. A Play Data safety form that matches what is collected. | — |
| NF-12 | Security | No server secret ships in the app. Personal data is readable only by its owner. All traffic uses HTTPS. Downloaded packs are verified before use. | — |
| NF-13 | Reliability | Downloaded and cached content works when Supabase or the CDN is down. Only sign-in, sync, downloads and uncached online reading degrade, with a clear message. | Crash-free sessions ≥ 99.5% |
| NF-14 | Storage | Packs can be stored on removable storage where the device allows. | — |
| NF-15 | Low-end devices | Usable on a 2 GB RAM phone running Android 8. | No out-of-memory crashes in the test suite |
| NF-16 | Hosting independence | Content can move between hosting providers (for example Cloudflare R2 and Vercel) without an app release, without breaking installed versions, and without users re-downloading what they already have. If one provider is down, content still loads from the other where it is mirrored. | Host change is a configuration change only |

---

## 7. Decisions and open questions

Technical questions are in [design.md](design.md#19-open-design-questions).

### 7.1 Decisions (owner, 3 October 2026)

| # | Question | Decision | Where it applies |
|---|---|---|---|
| 1 | Reading without downloading | Supported. Anything not downloaded can be read online and is cached. Updated content is applied automatically, to packs and to cached chapters. | ON-1–ON-8, DL-1, DL-6, DL-8, A-4.9 |
| 2 | Location in stats | City, region and country from the IP address on the server, as the website does. The IP is never stored. | ST-6 |
| 3 | What counts as a verse read | At least 60% visible for at least 2 seconds. | §5.1 `read` |
| 4 | Early Church Fathers commentary | Not included for now. | A-5.2, §4.1 |
| 5 | Audio licences (KJV, WEB) | Confirmed: offline download of all four recordings is allowed. | A-6.3 |
| 7 | Facebook sign-in | Not offered. Google and email only, as on the website. | A-7.1 |
| 8 | Package name | `com.tamilscripture.app`, published from the owner's Play developer account. | Header |

### 7.2 Open: minimum Android version

The draft suggested that raising the minimum from Android 8.0 (API 26) to Android 10 (API 29) "would simplify storage code". Checked against this design, that is mostly not true:

- **Storage gains nothing.** The app keeps packs in its own app-specific folders (`filesDir`, `getExternalFilesDirs()`), including SD cards. Those need no permission and behave the same on every version since Android 4.4. Android 10's scoped storage only changes access to *shared* storage, which the app never uses.
- **What API 29 would actually remove** is a handful of small compatibility branches:
    - **Foreground service types** (`dataSync`, `mediaPlayback`) start at API 29. Older versions take a slightly different code path, which `ServiceCompat` already handles.
    - **System dark theme** starts at API 29. On 8.0–9 the "follow system" theme option has nothing to follow, so the app falls back to light or to battery saver.
    - **Gesture navigation and edge-to-edge insets** behave differently before API 29. AndroidX handles them, but they need separate visual testing.
    - **Older text shaping engines** on 8.0–9 make Tamil conjunct rendering a little riskier. The bundled fonts reduce the risk, but those versions still need screenshot tests.
    - **A smaller test matrix:** no Android 8–9 devices to keep, which in India are mostly low-end Android Go phones.
- **What it would cost:** every Android 8.0–9 device would be excluded. Those are disproportionately the low-cost phones many Tamil readers use.

**Recommendation:** keep API 26. The extra work is limited to the branches above, and it keeps the app available to the people least able to upgrade. Revisit after launch using the Play Console's device statistics. *Owner to confirm.*
