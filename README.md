# Collection Log Commands

Collection Log Commands is a RuneLite plugin that lets you share collection log progress from chat with `!log` commands.

Examples:
```
!log zulrah
!log zulrah missing
!log missing zulrah
!log cg
!log cox cm
!log summary
```

The plugin rewrites your original chat message in place, similar to RuneLite's built-in chat commands. After you send `!log zulrah`, the chat line updates to your formatted collection log output.

## Commands

| Command | Output |
| --- | --- |
| `!log <entry>` | Shows your collected items for that collection log entry. |
| `!log <entry> missing` | Shows the items you are still missing for that entry. |
| `!log missing <entry>` | Same as `!log <entry> missing`. |
| `!log aliases` | Shows common shorthand examples. |
| `!log summary` | Shows total cached progress across every entry the plugin knows about. |
| `!log` / `!log help` | Shows command usage. |

If an entry is complete, `!log <entry> missing` shows `complete!`. If you have no obtained items for an entry, `!log <entry>` shows `nothing yet`.

## Output Modes

The plugin settings include an **Output mode** dropdown.

| Mode | Format |
| --- | --- |
| `Verbose` | Shows progress, item icons, and item names. This is the default. |
| `Condensed` | Shows a compact blue entry header like `Chambers of Xeric (13/23):` followed by item icons only. If RuneLite exposes a collection log quantity greater than 1, condensed mode appends `xN` after that icon. |

## Entry Matching

Entry matching is case-insensitive and ignores punctuation. Multi-word entries work normally:

```
!log theatre of blood
!log larrans small chest
```

Common RuneLite-style shorthand is also supported where it maps cleanly to collection log entries:

```
!log kbd
!log cg
!log cox
!log cox solo
!log cox cm duo
!log tob
!log hmt solo
!log toa expert 8
!log hs 5
!log wt
!log gotr
```

Use `!log aliases` in-game for a short list of common examples.

## Cache Behavior

The plugin learns collection log entries when you open them in-game. Once an entry has been opened, it is cached per character and can be used by `!log` later.

- Open a collection log page once to cache its item list and obtained/missing state.
- Re-open a page after getting new drops to update that cached entry.
- Cache files are stored per RuneScape character, so different accounts do not mix.
- `!log summary` reports progress only across entries the plugin has cached.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
