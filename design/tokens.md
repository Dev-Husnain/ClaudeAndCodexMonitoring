# AgentMon design tokens (snapshot)

Source: `AGENT_MONITOR_SPEC.md` §9 (there is no Figma). The theme files are generated from this
snapshot: `app/.../core/theme/Color.kt` + `AppColors.kt`, and `desktop/.../ui/theme/DesktopTheme.kt`.
Token names use the last four hex digits (guidelines §5.10).

## Colour

| Role | Dark | Token | Light | Token |
|---|---|---|---|---|
| Background | `#0B0F1A` | Color0F1A | `#F6F7FB` | ColorF7FB |
| Surface | `#121826` | Color1826 | `#FFFFFF` | ColorFFFF |
| Surface elevated | `#1A2236` | Color2236 | `#EEF1F8` | ColorF1F8 |
| Outline | `#26304A` | Color304A | `#D9DEEA` | ColorDEEA |
| Text primary | `#E6EAF5` | ColorEAF5 | `#0B0F1A` | Color0F1A |
| Text secondary | `#94A3B8` | ColorA3B8 | `#5B6478` | Color6478 |
| Brand gradient start | `#7C5CFF` | Color5CFF | `#6D4AFF` | Color4AFF |
| Brand gradient end | `#22D3EE` | ColorD3EE | `#0891B2` | Color91B2 |
| Status: running | `#22D3EE` | ColorD3EE | `#0891B2` | Color91B2 |
| Status: waiting | `#FBBF24` | ColorBF24 | `#D97706` | Color7706 |
| Status: done | `#34D399` | ColorD399 | `#059669` | Color9669 |
| Status: error | `#F87171` | Color7171 | `#DC2626` | Color2626 |
| Status: stale | `#94A3B8` | ColorA3B8 | `#64748B` | Color748B |

The light values aren't in the spec. They are darker variants that I chose so the colours keep
enough text contrast on white (decision D3 in PROJECT-GUIDE.md).

## Typography (bundled, SIL OFL)

| Use | Family | Weights |
|---|---|---|
| Headings, titles | Space Grotesk | 500, 600, 700 |
| Body, labels | Inter | 400, 500, 600 |
| Terminal, ids, tools | JetBrains Mono | 400, 700 |

Scale: display 34/40, headline 28/34 and 22/28, title 20/26, 17/22 and 15/20, body 16/24, 14/20 and 12/16,
label 14/20, 12/16 and 11/14 (uppercase section labels, +0.08em).

## Shape and spacing

- Radii: 8, 12, 20, 24 (cards), 28 (hero cards), pill.
- Spacing scale: 2, 4, 8, 12, 16, 20 (screen padding), 24, 32, 48.
- Touch target 48dp; max content width 720dp (tablets).

## Motion

- Status orb: 1.6s pulse ring while running; 0.9s breathing shimmer while waiting; static otherwise.
- Navigation: 320ms slide (1/5 width) + fade; the reverse on back.
- All infinite motion is disabled when the system "Remove animations" setting is on.
