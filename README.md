# Collection Log Commands

A RuneLite plugin that adds an in-game `!log` chat command for sharing your Collection Log progress.

## Commands

| Command | Output |
| --- | --- |
| `!log <entry>` | `Entry: X/Y collected` followed by icons + names of the items you've obtained. |
| `!log <entry> missing` | `Entry: X/Y missing` followed by icons + names of items you still need. |
| `!log missing <entry>` | Same as `!log <entry> missing`. |
| `!log aliases` | Shows common shorthand examples such as `cg`, `cox`, `tob`, `toa`, `kbd`, `wt`, and `gotr`. |
| `!log summary` | Shows total cached progress across every collection log entry the plugin knows about. |
| `!log` / `!log help` | Shows command usage. |

Entry matching is case-insensitive, ignores punctuation, and falls back to a fuzzy match if there's no exact substring hit. Multi-word entries work (`!log theatre of blood`, `!log larrans small chest`).

The plugin rewrites the original chat message in place, similar to RuneLite's built-in chat commands. For example, after sending `!log zulrah`, the chat line updates to the formatted collection log output.

Common RuneLite-style shorthand is supported for entries where players usually type abbreviations instead of full names, such as `!log cg`, `!log cox`, `!log cox cm`, `!log tob hm`, `!log toa expert`, `!log kbd`, `!log wt`, and `!log gotr`.

Examples:
```
!log zulrah          -> Zulrah: 5/10 collected [icons...]
!log zulrah missing  -> Zulrah: 5/10 missing [icons...]
!log missing zulrah  -> Zulrah: 5/10 missing [icons...]
!log cg              -> Corrupted Gauntlet: 4/12 collected [icons...]
!log summary         -> Collection log cache: 128/412 items collected across 37 cached entries
!log araxxor         -> Araxxor: 2/10 collected [icons...]
```

If you fully collected the entry, `!log <entry> missing` prints `complete!`. If you have nothing, `!log <entry>` prints `nothing yet`.

## How the cache works

The plugin scrapes the Collection Log interface live, so it only knows about entries you've actually opened in-game. The scraped data is then persisted to disk per character, so you only need to open each entry **once, ever**.

- The first time you open an entry's page in-game, the plugin captures its name, item list, and obtained/unobtained state.
- The cache is written to `~/.runelite/collection-log-commands/cache_<rsn>.json` (one file per character, named after your in-game RSN).
- On every subsequent RuneLite launch, the cache is loaded automatically when you log in. `!log` queries work immediately, without re-opening the Collection Log.
- Cache entries are updated automatically whenever you re-open a page (e.g. after getting a new drop). Disk writes only happen when the data actually changes.

If you log into a different character, that character's cache (or an empty one) is loaded automatically; characters never mix.

## Installation (sideloading)

This plugin is not on the Plugin Hub. To use it:

1. Clone the repo:
   ```
   git clone https://github.com/unk-user-shade/collection-log-commands
   cd collection-log-commands
   ```
2. Build:
   ```
   ./gradlew build
   ```
3. Run a dev RuneLite client with the plugin loaded:
   ```
   ./gradlew run
   ```
   This launches RuneLite in developer mode with the plugin classpath-loaded. Log into your account from there.

Java 11+ is required (the project targets Java 11 release).

## Implementation notes

- Caching is driven by the `COLLECTION_DRAW_LIST` client script (id 2731), which fires every time a Collection Log entry page is drawn. The plugin reads the entry name from `InterfaceID.Collection.HEADER_TEXT` and the item grid from `InterfaceID.Collection.ITEMS_CONTENTS`.
- Obtained vs. unobtained is detected via `Widget.getOpacity() == 0` (the same heuristic the official `chatcommands` plugin uses for pets).
- Cache writes are atomic (`.tmp` then `Files.move` with `REPLACE_EXISTING, ATOMIC_MOVE`), so a crash mid-write can't corrupt the file.
- All disk I/O happens on a dedicated single-thread executor; the client thread is never blocked on file reads or writes.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
