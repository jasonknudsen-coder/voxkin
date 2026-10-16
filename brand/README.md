# Voxkin visual identity

Production geometry based on the user-approved brand board (2026-10-13): a soft ribbon V with **two rounded sound bars on each side**. Cyan/blue always appears on the left; purple/magenta on the right. The inner bars are taller than the outer bars.

**Tagline:** Talk to your agent.

**Positioning:** Voice-first. Direct. Flexible.

## Masters and exports

- `voxkin-mark.svg`: transparent vector mark, tightly framed with breathing room.
- `voxkin-mark-monochrome.svg`: identical geometry using `currentColor`.
- `voxkin-app-icon.svg`: full-square vector icon on opaque navy.
- `../App/Assets.xcassets/AppIcon.appiconset/Icon.png`: production 1024 × 1024 opaque PNG. No baked-in rounded corners; iOS supplies the mask.
- `icon-preview-60.png`, `icon-preview-120.png`: small-size readability references, not app asset-catalog sources.

The mark has no text so it remains usable at app-icon sizes. Do not add a tagline to the icon, remove a side bar, or swap the right bars to cyan. Use the monochrome master when gradients cannot be reproduced. The reference board's typography is inspiration rather than a separately licensed font asset; the native app uses platform typography.

## Palette

- Navy background: `#0B1830` → `#040914`
- Cyan: `#12D9EC`
- Blue: `#087EFF`
- Cyan ribbon return: `#04CBEA`
- Magenta: `#EE67F7`
- Violet: `#7939FF`
- Deep violet: `#4932F5`

Gradients are decorative brand colors, not automatically accessible body-text colors. Use semantic foreground colors for app text and verify contrast on the rendered background.

## Regenerate

From the repository root on macOS:

```sh
swift scripts/generate-icon.swift App/Assets.xcassets/AppIcon.appiconset/Icon.png
sips -g pixelWidth -g pixelHeight -g hasAlpha App/Assets.xcassets/AppIcon.appiconset/Icon.png
```

Expected: 1024 × 1024, `hasAlpha: no`. The Swift generator and SVG masters use the same 1000-unit, top-left coordinate system; update them together if geometry changes. The app's native mark should also use these paths, not raster crops of the brand board.

The V spans approximately x=275–745 and y=281–682. Sound bars are 42 units wide with 21-unit corner radii at `(110,448,42,104)`, `(175,410,42,180)`, `(783,410,42,180)`, `(848,448,42,104)`. Standalone SVG viewBox is `100 270 800 425`.

## Native presentation
`App/VoxkinBrand.swift` renders the same paths with uniform scaling and centering.
Talk and Settings use a navy background, cyan actions and purple reply accents.
Conversation text follows Dynamic Type; the pinned transport controls cap at XXXL
so keyboard, talk/send and Stop remain reachable at accessibility text sizes.
The content above remains scrollable. No recording, pairing, gateway, or voice
selection behavior is changed by this visual layer.

Square app-icon exports translate the mark down 20 units (2% of canvas height) to center its visible bounds. The standalone mark and native UI crop retain their original geometry.

## License scope

The repository-authored vector assets, generated icon exports and drawing code
are included under the root Apache-2.0 license. Reference branding boards informed
the design; they are not bundled third-party font files or stock-image assets.
Apache-2.0 does not grant trademark rights. No uniqueness or trademark clearance
is claimed for the name or mark.
