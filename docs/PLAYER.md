# Video Player Architecture (Media3 ExoPlayer)

## Architecture Overview

Video playback in LIQUID TV is powered by **AndroidX Media3 ExoPlayer 1.5.0**.

```
┌────────────────────────────────────────────────────────┐
│                   PlayerScreen.kt                      │
│   Full-screen AndroidView hosting PlayerView           │
│   Floating Liquid Glass Control Overlay (Auto-hide 5s) │
└──────────────────────────┬─────────────────────────────┘
                           │ StateFlow / Actions
┌──────────────────────────▼─────────────────────────────┐
│                   PlayerViewModel.kt                   │
│   Manages channel index, next/prev, retry, state       │
└──────────────────────────┬─────────────────────────────┘
                           │ Service Connection / Intent
┌──────────────────────────▼─────────────────────────────┐
│             LiquidTVPlaybackService.kt                 │
│   Foreground MediaSessionService, ExoPlayer Instance   │
│   HLS MediaSource Factory, OkHttp HTTP Data Source     │
└────────────────────────────────────────────────────────┘
```

---

## Streaming Protocols Supported

- **HLS (HTTP Live Streaming)**: Streams ending in `.m3u8` or utilizing adaptive bitrate manifests.
- **MPEG-TS**: Transport streams commonly utilized in traditional IPTV broadcasts (`.ts`).
- **MP4 / Progressive HTTP**: Direct stream files.

---

## Buffer and Network Optimization

Configured in `LiquidTVPlaybackService`:
- **DefaultHttpDataSource.Factory**:
  - `User-Agent`: Custom TV client identifier (`LiquidTV/1.0 (Android TV)`).
  - `ConnectTimeout`: 15,000 ms.
  - `ReadTimeout`: 20,000 ms.
  - Cross-protocol redirects enabled.
- **DefaultLoadControl**:
  - Min Buffer: 15,000 ms.
  - Max Buffer: 50,000 ms.
  - Playback Start Buffer: 2,500 ms (fast startup).
  - Rebuffer Duration: 5,000 ms.

---

## Error Handling & Recovery

1. **Transient Network Errors**: Automatic reconnection attempts up to user-configured retry limits.
2. **Stream Offline / Geographic Restriction**: When ExoPlayer enters `STATE_IDLE` with an error, the player transitions to a clean glass error overlay displaying:
   - Specific stream diagnosis.
   - Immediate "Retry Stream" action button.
   - Quick "Next Channel" zap button.

