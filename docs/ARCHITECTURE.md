# Architecture Documentation

## Overview

LIQUID TV follows modern native Android Architecture principles combining **Clean Architecture**, **MVVM (Model-View-ViewModel)**, and **Unidirectional Data Flow (UDF)**.

```
┌────────────────────────────────────────────────────────┐
│                   UI Layer (Compose)                   │
│   Screens, Components, Navigation, Mirror Bubble       │
└─────────────────────────┬──────────────────────────────┘
                          │ StateFlow / Events
┌─────────────────────────▼──────────────────────────────┐
│                    ViewModel Layer                     │
│   HomeViewModel, LiveTVViewModel, PlayerViewModel...   │
└─────────────────────────┬──────────────────────────────┘
                          │ Coroutines / Flow
┌─────────────────────────▼──────────────────────────────┐
│                     Domain Layer                       │
│   Models (Channel, Category...), Repository Interfaces │
└─────────────────────────┬──────────────────────────────┘
                          │
┌─────────────────────────▼──────────────────────────────┐
│                      Data Layer                        │
│   ChannelRepositoryImpl, Room DAO, DataStore, M3UParser│
└──────────────┬──────────────────────────┬──────────────┘
               │                          │
┌──────────────▼──────────┐    ┌──────────▼──────────────┐
│   Remote (iptv-org API) │    │ Local Cache (SQLite DB) │
└─────────────────────────┘    └─────────────────────────┘
```

---

## Package Organization

```
com.liquidtv.app/
├── LiquidTVApplication.kt        # Hilt Application entry point & WorkManager config
├── MainActivity.kt               # Single TV Activity, window insets & full immersion
├── di/                           # Dagger-Hilt Dependency Injection modules
│   └── AppModule.kt              # DatabaseModule, NetworkModule, RepositoryModule
├── domain/
│   ├── model/Models.kt           # Channel, Category, Language, Country, Filter models
│   └── repository/               # ChannelRepository, UserPreferencesRepository
├── data/
│   ├── remote/                   # Retrofit IptvApiService & M3U URLs
│   ├── parser/M3UParser.kt       # High-performance M3U/M3U8 parser
│   ├── local/                    # Room Database, Daos, Entities
│   ├── datastore/                # AndroidX DataStore for user preferences
│   └── repository/               # Repository implementations
├── player/
│   ├── LiquidTVPlaybackService.kt# MediaSessionService foreground player
│   └── PlayerViewModel.kt        # ExoPlayer state manager & playback control
├── ambient/
│   └── AmbientViewModel.kt       # Screensaver animation & theme controller
└── ui/
    ├── LiquidTVApp.kt            # Root composable with top floating navigation
    ├── theme/                    # LiquidColors, LiquidTypography, LiquidTVTheme
    ├── components/               # LiquidGlassSurface, GlassChannelCard, LiveBadge...
    ├── navigation/               # NavGraph & MirrorBubbleNavigation
    └── screens/                  # Home, LiveTV, Categories, Languages,
                                  # Countries, Search, Favorites, Settings,
                                  # Player, Ambient
```

---

## State Management and Data Flow

1. **Unidirectional Data Flow**: Each Screen observes an immutable `UiState` exposed as a `StateFlow` from its corresponding ViewModel.
2. **Offline-First Reactive Caching**: Room queries return `Flow<List<ChannelEntity>>`. The UI updates automatically whenever the database is populated or synchronized without blocking the main thread.
3. **Chunked Insertion**: To maintain 60fps on lower-end Android TV hardware, the `M3UParser` streams entries into batches of 500 records before performing Room `@Upsert` operations.
4. **Lifecycle-Aware Video Playback**: `ExoPlayer` is managed via a dedicated `PlayerViewModel` and backed by a foreground `MediaSessionService` (`LiquidTVPlaybackService`) so media sessions are preserved during UI transitions and system events.

