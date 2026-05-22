# Collection Log Commands

Collection Log Commands is a RuneLite plugin that lets you view cached collection log progress from local chat commands.

Commands use RuneLite's local `::` command syntax, so output is only shown to you. It is not sent to public, clan, friends, or private chat.

Examples:
```
::log missing
::log zulrah
::log zulrah missing
::log missing zulrah
::log cg
::log cox cm
::log summary
::log cached
::log clear zulrah
```

## Commands

| Command | Output |
| --- | --- |
| `::log missing` | Lists cached entries that are incomplete, with missing counts. |
| `::log <entry>` | Shows your collected items for that collection log entry. |
| `::log <entry> missing` | Shows the items you are still missing for that entry. |
| `::log missing <entry>` | Same as `::log <entry> missing`. |
| `::log aliases` | Shows common shorthand examples. |
| `::log summary` | Shows total cached progress across every entry the plugin knows about. |
| `::log cached` | Lists cached entries with collected/total counts. |
| `::log clear <entry>` | Removes one cached entry for the current character. |
| `::log clearcache` | Clears every cached entry for the current character. |
| `::log` / `::log help` | Shows command usage. |

If an entry is complete, `::log <entry> missing` shows `complete!`. If you have no obtained items for an entry, `::log <entry>` shows `nothing yet`.

## Recommended Workflow

Use `::log missing` as the main overview command. It reports only incomplete cached entries, such as `Zulrah (2 missing)`, so you can quickly see what still needs attention.

Use `::log <entry> missing` when you want the actual missing item names and icons for one entry:

```
::log zulrah missing
```

Use `::log cached` when you want to audit what pages the plugin has cached. This can be noisy if you have opened many Collection Log pages.

## Output Modes

The plugin settings include an **Output mode** dropdown.

| Mode | Format |
| --- | --- |
| `Verbose` | Shows progress, item icons, and item names. This is the default. |
| `Condensed` | Shows a compact blue entry header like `Chambers of Xeric (13/23):` followed by item icons only. If RuneLite exposes a collection log quantity greater than 1, condensed mode appends `xN` after that icon. |

## Entry Matching

Entry matching is case-insensitive and ignores punctuation. Multi-word entries work normally:

```
::log theatre of blood
::log larrans small chest
```

Common RuneLite-style shorthand is also supported where it maps cleanly to collection log entries:

```
::log kbd
::log cg
::log cox
::log cox solo
::log cox cm duo
::log tob
::log hmt solo
::log toa expert 8
::log hs 5
::log wt
::log gotr
```

Use `::log aliases` in-game for a short list of common examples.

## Cache Behavior

The plugin learns collection log entries when you open them in-game. Once an entry has been opened, it is cached per character and can be used by `::log` later.

- Open a collection log page once to cache its item list and obtained/missing state.
- Re-open a page after getting new drops to update that cached entry.
- Cache files are stored per RuneScape character, so different accounts do not mix.
- The side panel lists cached entries and shows the selected entry after you run `::log <entry>`.
- `::log summary` reports progress only across entries the plugin has cached.
- `::log missing` shows which cached entries are incomplete.
- `::log cached` shows which entries are currently cached.
- `::log clear <entry>` and `::log clearcache` let you remove stale cached data.

The plugin also shows a one-time in-game reminder after first login, and the settings panel includes the same cache reminder above the output mode dropdown.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
