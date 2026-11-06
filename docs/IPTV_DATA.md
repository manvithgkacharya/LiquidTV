# IPTV Data Integration & M3U Parser

## Data Source

LIQUID TV integrates directly with the [iptv-org](https://github.com/iptv-org/iptv) open-source channel repository.

### Public Endpoints

| Resource | URL |
|---|---|
| **Main Playlist** | `https://iptv-org.github.io/iptv/index.m3u` |
| **Category Playlist** | `https://iptv-org.github.io/iptv/index.category.m3u` |
| **Language Playlist** | `https://iptv-org.github.io/iptv/index.language.m3u` |
| **Country Playlist** | `https://iptv-org.github.io/iptv/index.country.m3u` |
| **Region Playlist** | `https://iptv-org.github.io/iptv/index.region.m3u` |

---

## M3U Parser Architecture

Located in `com.liquidtv.app.data.parser.M3UParser`:

### Extended M3U Format
```m3u
#EXTM3U x-tvg-url="https://iptv-org.github.io/epg/guides/..."
#EXTINF:-1 tvg-id="CNN.us" tvg-name="CNN" tvg-logo="https://..." group-title="News" tvg-country="us" tvg-language="eng",CNN US (1080p)
https://turner-live.fastly.net/hls/live/cnn_slate.m3u8
```

### Parser Engineering Rules
1. **Streaming String Processing**: Does not load the entire playlist into unmanaged heap memory; processes lines progressively with progress callbacks `(processedLines, totalLines)`.
2. **Attribute Extraction**: Uses regex `(\S+?)=["']([^"']*)["']` over the attribute segment prior to the display name delimiter `,`.
3. **Graceful Fallbacks**:
   - If `tvg-id` is absent, generates a stable hash from channel title and stream URL.
   - If display title is missing, falls back to `tvg-name` and finally to path extraction from stream URL.
   - Converts ISO 3166-1 alpha-2 country codes (e.g. `us`, `gb`, `jp`) to human-readable names and flag emojis.
4. **Deduplication**: Filters out duplicate stream URLs while preserving user favorites and historical watch records.
5. **Database Storage**: Upserts into SQLite in transactions of 500 records to prevent memory pressure on Android TV devices.

