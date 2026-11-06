# Cinematic Ambient Mode & Screensaver

## Overview

Ambient Mode is LIQUID TV's signature screensaver experience inspired by Apple TV's aerial screensavers and Google TV ambient mode.

It automatically engages after a configurable period of inactivity (default: 3 minutes) or can be triggered manually via:
1. The remote shortcut inside the video player bottom control bar.
2. The Ambient Mode test tile in the Settings screen.

---

## Visual Aesthetics & Themes

Five atmospheric themes are supported:

| Theme | Mood / Colors | Description |
|---|---|---|
| **Midnight Glass** | Deep Black & Midnight Navy | Refined dark aesthetic with subtle cool blue refractive orbs |
| **Aurora** | Pine Deep Green & Cyan | Nordic northern lights atmospheric glow |
| **Ocean** | Deep Sea Navy & Cerulean | Gentle rolling undersea ambient radiance |
| **Cosmic** | Dark Violet & Nebula Purple | Deep space nebula aesthetic |
| **Minimal** | Pure Black & Charcoal Gray | Zero distraction high-contrast night theme |

---

## Television & OLED Protection Engineering

Televisions with OLED panels are susceptible to burn-in when displaying static pixels over prolonged durations. Ambient Mode implements several defensive measures:

1. **Slow Organic Drift**: All floating orbs and favorite channel artworks follow continuous harmonic sinusoidal paths (`phase1` over 28s, `phase2` over 38s).
2. **Periodic Pixel Shifting**: The minimal clock and date coordinates shift dynamically across a 32dp boundary (`burnInShiftX`, `burnInShiftY`) so static text never illuminates identical OLED subpixels.
3. **No Heavy GPU Shaders**: All visuals are executed using standard Compose Canvas draw calls without unmanaged runtime GPU shader loops, keeping SoC temperatures and power consumption low.
4. **Instant Exit**: Any key event caught via `Modifier.onKeyEvent` immediately dismisses Ambient Mode and restores the previous browsing or player state without lag.

