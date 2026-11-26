# Liquid Glass Design System

## Premium dark theme family

LiquidTV provides ten coordinated dark palettes: Royal Sapphire, Emerald Prestige, Obsidian Gold, Ruby Royale, Atlantic Noir, Amethyst Elite, Titanium Luxe, Copper Imperial, Electric Indigo, and Rose Gold Noir.

Each theme defines a three-stop atmospheric background gradient, two soft wave colors, accent/focus colors, and dark card/panel tint colors. Theme IDs remain stable so existing saved selections continue to resolve. The default is Royal Sapphire.

## Shared glass-card material

All themes share one Liquid Glass material system; only its tint, accent glow, and border colors change.

| Layer | Default opacity |
|---|---:|
| Standard card glass fill | 12–14% |
| Secondary glass surface | 8–10% |
| Selected card fill | 18% |
| Focused card fill | 20–22% |
| Standard border | 22% |
| Focus border | Up to 82% |
| Top specular highlight | 45% source alpha, softened by the renderer |

The fill alpha is applied literally. It is not amplified based on the theme, preventing cards from becoming opaque slabs and allowing the background gradient and ambient waves to remain visible through the glass.

## Optical layers

1. **Translucent tinted glass bed** using the active theme's card tint.
2. **Soft top reflection** for a polished glass edge.
3. **Subtle lower depth gradient** for separation from the background.
4. **Thin perimeter border** tinted by the active theme.
5. **Low-intensity accent glow** around focused cards.
6. **TV focus response**: a short scale-up, brighter border, and stronger glow without changing layout dimensions.

## Shared component

`com.liquidtv.app.ui.components.LiquidGlassSurface` is the single material primitive used by category cards, channel cards, settings cards, and other glass surfaces. Preserve its focus, press, click, and interaction-source behavior when extending the theme system.

## Signature navigation

`MirrorBubbleNavigation` keeps its existing floating glass pill and animated selected bubble. Its palette continues to resolve from the active app theme.

## Scope

The app theme is independent from the Ambient/screensaver theme. Changing an app theme must not rewrite Ambient preferences or alter playback, navigation, or data behavior.
