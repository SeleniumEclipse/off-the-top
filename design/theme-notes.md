# Off the Top — three visual directions

## Selected direction: green/red Card Table refinement

The user selected Card Table and asked for a stronger casino/playing-card feel, with **green for correct and red for pass**. They specifically rejected card suits and asked for original category icons rather than emoji. On 2026-09-09 they approved the build with one change: **remove the outer table outline**. This is now the selected native Android direction, superseding the earlier single-teal palette.

Open [the refined Card Table preview](card-table.html): flat bottle-green background, **no table boundary**, cream faces, red crosshatched backs and stacked card edges. The original Set fonts and hard press-down buttons remain. No gradients, gambling mechanics, money, bets or casino labels.

Original 48×48 category marks share a 3-unit square-ended stroke and are drawn directly on the cards: leaf for Wild World, mug for Everyday Things, figure in motion for Do Your Thing. They replace suits in the mirrored corners. Standalone SVG files are in icons/; they are not emoji, icon-font characters, stock pictures or copied card artwork.

The preview includes Decks, Round, Correct feedback and Pass feedback. Selecting a deck changes its words and category mark. Clicking a preview Correct/Pass button briefly shows feedback, advances a sample card, and updates only the local sample score. The clock remains fixed. The native Android implementation adds real gameplay and preserves the existing tilt engine, deck contents and saved data.

Checked with tools/check-card-table.cjs: all three decks and exported SVGs, mirrored category marks, sample-word fitting, correct/pass scoring, blocked feedback input, pause during feedback, keyboard dialog dismissal, four round lengths and 390/588/1280 px browser widths. No missing resources, JavaScript errors or horizontal page overflow. Six images exported in previews/: four screen states, an icon sheet and casino-table-comparison.png.

The original three alternatives below are retained as design history, not current app choices. Open theme-options.html to compare their deck selection and active rounds; those prototypes use sample data only. The approved green/red refinement is implemented in Android 1.4.0.

## Shared foundation from Set

The actual Set project uses Archivo Black and Barlow, sharp 2–3 dp corners, a 2 dp ink outline, and a hard 4 dp offset shadow. These previews reuse the existing licensed font files and that tactile style. No gradients, emoji icons, remote fonts, promo badges, automatic helper copy, or fake texture layers.

Teal remains the one accent. Each direction changes the **layout and physical metaphor**, not just its background color. Theme-specific card art, flap seams or ticket perforations are purposeful proposals in this study, not committed app requirements.

## Card Table

- **Reference:** [Oink Games — Deep Sea Adventure](https://oinkgames.com/en/games/analog/deep-sea-adventure/). Official packaging viewed in browser; compact physical box, strong title, limited-color print. Also reviewed [Insider](https://oinkgames.com/en/games/analog/insider/).
- **Adaptation:** forest-teal table with three physical deck stacks; familiar printed card faces, simple original geometric print on the packaging. The play word sits on a large cream card, with score/time as card corners.
- **Relationship to Set:** closest material language—ink, paper, heavy type and hard drops.
- **Trade-off:** a cream clue card is more luminous in a dark room. It is a dark table, not a fully dark play surface. A dark-card variant is possible if preferred.

## Scoreboard

- **Reference:** [Vestaboard product design](https://www.vestaboard.com/product) and [homepage](https://www.vestaboard.com/). Official product page viewed in browser; mechanical split-flap cells, matte dark chassis and modular high-contrast characters.
- **Adaptation:** warm charcoal control board, a bold teal title rail, split-flap number cells, and vertically separated time/score during play. The clue stays large on a flat dark panel; it is deliberately **not** split into letters, which would reduce readability.
- **Relationship to Set:** the same thick type and pressed controls, but with scoreboard organization rather than generic app cards.
- **Trade-off:** more competitive/game-show feel, less physical-card warmth. Recommended starting point for a truly dark theme.

## Box Office

- **Reference:** [Pentagram — The Public Theater](https://www.pentagram.com/work/the-public-theater). Official case study viewed in browser; expressive block typography, deliberately structured poster hierarchy. Also reviewed [Shake Shack](https://www.pentagram.com/work/shake-shack) for disciplined printed menu organization.
- **Adaptation:** warm near-black background; horizontal title lockup; three deck tickets with actual card counts in the stub. Active play uses a dark ticket body and teal timer/score stub. Minimal perforations, no neon, marquee bulbs, curtain texture or theatrical slogans.
- **Relationship to Set:** a theatrical version of the existing block-print language, rather than a different illustration style.
- **Trade-off:** the most expressive, but the ticket seams add more structure than the other choices.

## What the references do not mean

No third-party artwork, logos, screenshots or game content is bundled in the mockups. These are layout/material references, not screen copies. There is no claim that these aesthetics are objectively more “human,” nor that a familiar dark theme is inherently AI-generated. The goal is a visual identity the user actually likes.

The Playdate site was also attempted but could not be retrieved reliably, so it is not credited as a viewed reference.

## Review

- Compare the **same screen** across all three using Deck selection / In a round.
- Tap a deck for a local sample round; Pass and Correct advance sample words, including longer phrases.
- The timer is deliberately fixed for visual comparison. No sensor handling, saved scores or Android changes.
- On a small browser panel the landscape previews are scaled down. Select a concept and widen the panel, or open the page in an external desktop browser, for full-sized inspection.
- Sources reviewed 2026-09-09. Final production design, accessibility and physical-phone testing follow **after** choosing a direction.

## Preview validation

Checked the browser prototypes at 390, 588 and 1280 px browser widths: no horizontal page overflow, missing assets or JavaScript errors. Exercised all three deck choices per theme, duration choices, sample correct/pass scoring, pause/resume, help/settings/history overlays and Escape dismissal. Exported six full-size screen images plus the side-by-side comparison in previews/.

The small-browser view scales a landscape phone down rather than pretending to be a portrait app. This validates a visual study, not a production Android layout or accessibility certification. The actual app remains unchanged.

Local preview: `node tools/design-server.cjs`, then http://127.0.0.1:4176/design/theme-options.html. Browser capture script: tools/check-design.cjs; set PLAYWRIGHT_MODULE to an existing Playwright installation if it is not installed on the module path.