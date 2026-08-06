---
name: elisa-well-color-style
description: Use this skill when creating ELISA result slides or figures for TNF-a, IL-6, and IL-1β concentration experiments where each result has three replicates and the visual color must be based on the color inside the wells, not the full photo background.
---

# ELISA Well Color Style

Use this skill to reproduce the ELISA color language extracted from the well interiors in the user's `Elisa.pptx`. The photos show TNF-a, IL-6, and IL-1β ELISA results at different concentrations, with three repeated wells per result.

## What To Extract

Only use color from inside the ELISA wells.

Exclude:

- white plastic plate background
- transparent well wall and rim
- bright specular highlights
- shadows between wells
- labels, dust, bubbles, and edge artifacts
- whole-photo dominant colors

Prefer the central liquid area or clearly blue/blue-green reaction-liquid pixels. If a well is nearly transparent, keep it as a low-saturation baseline color rather than forcing it into the blue palette.

## Well Color Palette

The extracted ELISA well colors form a transparent-to-blue-green gradient. Use these as the experimental color palette:

| Intensity | Hex | Use |
|---|---:|---|
| Blank / near transparent | `#9B9790` | no visible color, blank control, very low concentration |
| Clear warm gray | `#9C9A93` | weak or background-level result |
| Very pale cyan-gray | `#7B9694` | first visible blue-green shift |
| Pale cyan | `#7F9594` | low positive signal |
| Light teal | `#709392` | mild positive signal |
| Muted teal | `#7C9191` | moderate positive signal with gray plate influence |
| Blue-gray teal | `#778C8D` | moderate-to-strong positive signal |
| Darker teal | `#6F8685` | strongest extracted well color in this PPT |

## Quantized Swatches

Use these swatches for charts, legends, heatmaps, and simplified diagrams:

```text
Transparent / blank: #9B9790, #9C9A93
Weak signal:          #849C9C, #789090, #849C90
Low signal:           #7F9594, #789090, #849C9C
Medium signal:        #709392, #6C9090, #609090, #789C9C
Strong signal:        #778C8D, #6C8484, #788484, #607878
```

## Replicate Handling

For TNF-a, IL-6, and IL-1β results:

1. Treat each result as three replicate wells.
2. Extract each replicate well separately.
3. Report the replicate median color first, then the averaged/median group color.
4. If one replicate is affected by glare, bubbles, or well-edge distortion, use the center liquid area or remove that replicate from the visual summary with a short note.
5. Do not average the entire image; average only the accepted well-interior pixels.

## Extraction Procedure

When Codex needs to extract colors again:

1. Open the PPTX and extract embedded images.
2. Locate the ELISA wells or the 3 replicate wells for each cytokine/concentration.
3. Sample a small circular or elliptical region inside each well, avoiding the rim.
4. Remove pixels with very high brightness and low saturation, because they are usually reflection or plastic.
5. For blue ELISA signal, prioritize pixels with blue-green hue and a visible cyan bias.
6. Use median RGB for the well color. Median is preferred over mean because glare and bubbles skew the mean.
7. Save both replicate-level colors and grouped result colors.

## Presentation Theme Colors

Use these extracted Office theme colors only for slide structure, text, and charts. They should not replace the well-interior ELISA colors above.

| Role | Hex | Use |
|---|---:|---|
| Text / dark anchor | `#0E2841` | titles, labels, axes |
| Background | `#FFFFFF` | clean slide background |
| Soft structure | `#E8E8E8` | table lines, separators |
| Primary accent | `#156082` | main non-experimental accent |
| Warm contrast | `#E97132` | warnings or comparison highlights |
| Biological green | `#196B24` | positive biological interpretation |
| Cyan accent | `#0F9ED5` | secondary emphasis |

## Visual Rules

- Use the ELISA well palette for experimental signal and legends.
- Use the presentation theme palette only for non-data design elements.
- Keep the background white or very light gray so subtle well colors remain visible.
- In figures, label TNF-a, IL-6, and IL-1β clearly and keep the three replicates visually grouped.
- For heatmaps or concentration ladders, order colors from `#9B9790` to `#6F8685`.
- Avoid saturated royal blue, neon cyan, purple gradients, and arbitrary brand colors; they do not match the well interiors.
- Do not use the earlier full-image gray palette as ELISA result colors, because it mostly came from plastic, shadows, and photo background.

## Quick CSS Tokens

```css
:root {
  --elisa-blank: #9B9790;
  --elisa-clear: #9C9A93;
  --elisa-very-weak: #7B9694;
  --elisa-weak: #7F9594;
  --elisa-medium: #709392;
  --elisa-medium-gray: #7C9191;
  --elisa-strong: #778C8D;
  --elisa-strongest: #6F8685;

  --elisa-text: #0E2841;
  --elisa-bg: #FFFFFF;
  --elisa-structure: #E8E8E8;
  --elisa-accent: #156082;
  --elisa-warm-contrast: #E97132;
}
```
