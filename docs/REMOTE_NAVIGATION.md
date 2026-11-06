# Android TV Remote Navigation & Focus Handling

## 10-Foot UI Design Principles

LIQUID TV is built from the ground up for the television 10-foot experience. It contains zero touch-only gesture listeners. Every user action can be completed with a standard 5-way D-Pad remote control.

---

## Remote Key Mapping

| Hardware Remote Key | KeyEvent Code | Action in LIQUID TV |
|---|---|---|
| **D-pad Up / Down / Left / Right** | `KEYCODE_DPAD_*` | Moves focus across channels, tabs, and filter controls |
| **OK / Center** | `KEYCODE_DPAD_CENTER`, `KEYCODE_ENTER` | Selects item, launches channel in player, reveals playback controls |
| **Back** | `KEYCODE_BACK` | Exits player overlay, exits Ambient mode, pops navigation stack |
| **Play / Pause** | `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_PLAY`, `KEYCODE_MEDIA_PAUSE` | Toggles playback directly |
| **Channel Up / Down** | `KEYCODE_CHANNEL_UP`, `KEYCODE_CHANNEL_DOWN`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS` | Instant channel zap in full-screen player |

---

## Focus Management Rules

1. **Initial Focus Granting**: Screens utilize `FocusRequester` coupled with `LaunchedEffect(Unit)` to immediately grant initial focus to the primary interactive element (such as the Hero Watch button or the Search bar).
2. **Focus Scaling Animation**: Focused cards smoothly scale up (1.06× to 1.08×) using spring physics (`dampingRatio = 0.6f, stiffness = 300f`).
3. **No Lost Focus Traps**: Every lazy list row, grid, and modal dialog establishes boundary handling so remote navigation never lands in an unfocusable dead-end.
4. **On-Screen Keyboard Integration**: The Search screen integrates `BasicTextField` with TV-compatible IME actions so users can input queries using standard Android TV remote software keyboards.

