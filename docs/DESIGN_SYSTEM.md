# Liquid Glass Design System

## Philosophy

The **Liquid Glass** design system in LIQUID TV translates the aesthetics of Apple's Liquid Glass material and Apple TV tvOS interface into native Jetpack Compose for television displays.

Rather than utilizing flat cards or generic Material Design rectangles, the application models optical properties of polished curved glass:
- Translucent refraction surfaces.
- Dynamic specular highlights along top and left incident edges.
- Inner diffused shadows providing physical depth.
- Fluid spring physics mimicking physical surface tension.

---

## Color Palette

| Token | Hex / Value | Description |
|---|---|---|
| `DeepBlack` | `#000000` | True OLED black background |
| `MidnightNavy` | `#080C14` | Atmospheric television background depth |
| `GlassPrimary` | `26% White` | Standard card surface fill |
| `GlassBorder` | `20% White` | Thin crisp perimeter rim |
| `GlassBorderFocused` | `80% White` | Bright luminescent border on D-pad selection |
| `AccentBlue` | `#3D9BE9` | Cool electric blue highlight |
| `AccentViolet` | `#8B5CF6` | Secondary accent gradient |
| `LiveRed` | `#FF3B30` | Pulsing broadcast indicator |

---

## Core Component: `LiquidGlassSurface`

Located in `com.liquidtv.app.ui.components.LiquidGlassSurface`:

```kotlin
LiquidGlassSurface(
    modifier = Modifier,
    cornerRadius = 16.dp,
    glassAlpha = 0.15f,
    borderAlpha = 0.2f,
    highlightAlpha = 0.35f,
    isFocused = isFocused,
) {
    // Card contents
}
```

### Optical Layers Drawn via `DrawScope`
1. **Translucent Glass Bed**: Rounded rectangle filled with `glassColor.copy(alpha = activeGlassAlpha)`.
2. **Inner Ambient Shadow**: Vertical gradient fading to subtle dark scrim on the lower 40% of the surface.
3. **Top Incident Refraction**: Vertical white gradient simulating overhead light hitting the upper rim.
4. **Perimeter Rim**: Crisp 1dp stroke that smoothly brightens from 20% to 80% opacity when the TV remote focuses the element.

---

## Signature Feature: Mirror Bubble Navigation

`com.liquidtv.app.ui.navigation.MirrorBubbleNavigation`

A floating horizontal glass pill positioned at the top of the display. When moving between tabs (Home, Live TV, Categories, Languages, Countries, Favorites, Search, Settings):
- The bubble's horizontal coordinate (`bubbleX`) and width (`bubbleWidth`) are tracked dynamically.
- Movement utilizes `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)`.
- As the bubble glides into position, the active item icon scales up to 1.15× while inactive tabs remain elegantly translucent.

