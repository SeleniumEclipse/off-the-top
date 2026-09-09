# Design rules — Off the Top

## Research and its limits

Reviewed 2026-09-08; updated with the user's approved direction on 2026-09-09. “AI-looking” is an aesthetic judgment, not a reliable test of authorship. For this game, the user's preference is decisive: **flat green table, cream cards, red printed backs, original category marks, green correct and red pass**. The user explicitly requested **no outer poker-table outline**. No gradients, emoji icons, card suits or habitual subtext. Set's print-inspired type, hard edges and pressed buttons stay.

The green/red Card Table preview was selected and approved for native implementation. This supersedes the earlier teal-only/no-category-art experiment. The original category drawings are deliberate card-corner artwork, not arbitrary decorative icon tiles. The user then authorized Characters, Silent Acting and Food & Drink, including familiar fictional pop culture. The native app now has six original vector category marks; the earlier preview records the three-deck design, not the current deck list.

Sources:

- [Anthropic: Improving frontend design through Skills](https://claude.com/blog/improving-frontend-design-through-skills) (2025-11-12). Describes models converging on predictable layouts, font choices and purple gradients. This is practitioner/vendor guidance, not a controlled study proving a universal “AI style.” It also recommends atmospheric backgrounds: **we explicitly reject that suggestion here**, rather than swapping one stock recipe for another.
- [Nielsen Norman Group: Aesthetic and Minimalist Design](https://www.nngroup.com/articles/aesthetic-minimalist-design/) (2021-01-24). Remove low-value decoration and irrelevant text while keeping everything necessary to perform the task. Minimal is not the same as empty.
- [Nielsen Norman Group: Icon Usability](https://www.nngroup.com/articles/icon-usability/) (2014-07-27). Most icons are ambiguous; use familiar meanings and visible labels for actions. Avoid inventing icons for concepts that words already express clearly.
- [Nielsen Norman Group: Progressive Disclosure](https://www.nngroup.com/articles/progressive-disclosure/) (2006-12-03). Keep common actions visible; put rarely needed details behind clearly named controls.
- [Nielsen Norman Group: Flat-Design Best Practices](https://www.nngroup.com/articles/flat-design-best-practices/) (2017-03-12). Flat color must not erase cues that show what is clickable. Keep clear boundaries and feedback.
- [Google: Material Symbols guide](https://developers.google.com/fonts/docs/material_symbols). Explains consistent icon styles/weights and Android vector assets. The app uses **Compose Material Icons Sharp**, not the newer Material Symbols font: only the small core vector dependency, no remote icon/font service.

## Common traps and our decisions

| Trap | Decision in this game |
|---|---|
| Generic “polish” through gradients, glows, glass or decorative blobs | Solid opaque green background without an outer outline. Stacked cream card faces; original crosshatching only on red card backs. |
| Emoji or font glyphs used as icon assets | Material Sharp action icons plus six original category vectors, with consistent stroke and no playing-card suits. Normal punctuation is fine. |
| Heading + subtitle + badge repeated everywhere | One clear label. Card counts stay because they are useful data. No automatic explanation under each setting or deck. |
| Decorative icon tiles with arbitrary pictures | The user approved category marks printed directly in opposing card corners. No stand-alone decorative picture tiles or phone illustration. |
| Icon-only controls in the name of minimalism | Back, Start round, Pass and Correct remain visibly named. Deck-page arrows have Previous decks / Next decks screen-reader labels and a visible page count. Outcomes retain screen-reader descriptions; actions expose their purpose. |
| Slogans, fake urgency and marketing copy in a utility/game screen | No “nice guessing,” promo badges, ads claims, taglines or novelty labels on the play path. |
| Every element competing as a filled card or accent | Green marks correct; red marks pass and card backs. Cream faces use dark green ink. Both day/night modes preserve that meaning. |
| Replacing one default recipe with another | Keep the existing type, familiar game flow, sharp borders and small hard button shadows; no unrelated redesign. |
| Removing useful guidance just to reduce word count | Keep one forehead instruction, the countdown hold prompt, readiness only when needed, and the explicit How to play screen. |

## Six-deck layout and rules

- Keep the original three decks intact; add Characters, Silent Acting and Food & Drink without squeezing all six cards onto one screen.
- Use the home area's **inner width**, after screen-cutout allowance and page padding: at least **650 dp** means **two pages of three cards**; below that means **three pages of two**. Here, dp is Android's display-density-independent unit, not a raw screenshot pixel.
- Allow horizontal swipes and accessible arrow buttons named **Previous decks** and **Next decks**. Show the page count, announce it to screen readers, and disable arrows at the ends. Remember the page while navigating within the current app session; do not imply it persists across process restarts.
- Keep deck names and counts readable on compact screens and with larger system text. Fit titles to their intentional line breaks; never split Characters inside the word. Reserve count space before fitting titles. Duration buttons keep complete single-line labels, with their own row on narrow screens or at large font sizes.
- Use six original native vector drawings: leaf for Wild World, mug for Everyday Things, motion figure for Do Your Thing, mask for Characters, a finger-to-lips face for Silent Acting, and fork/glass for Food & Drink. Reuse each category's mark on its deck stack and playing card. These are not franchise artwork.
- Silent Acting changes the clue-giving rule, not the controls or scoring: **clue givers mime silently; the guesser can speak**. Show that distinction before play and a short countdown reminder. No microphone permission or silence detection. Mime dangerous situations rather than performing them.
- Characters may use familiar fictional pop culture, including names from mature works. Food & Drink includes alcoholic drinks. Do not describe all decks as children-only or universally familiar. Let players pass. [Deck content and reuse](DECKS.md) records the limits.
- A deck's printed count is its full size, not its remaining unseen count. Played titles are shared across decks; selecting a new themed deck does not promise that every title is new. Explain the exact-title matching, one-time upgrade migration and reset behavior in [the deck guide](DECKS.md#how-repeats-are-avoided), not as repeated home-screen filler.

Native screenshots from the final signed build: [new deck page](screenshots/decks-new.png), [Characters round](screenshots/characters-playing.png), [Silent Acting rules](screenshots/silent-acting-rules.png), and [Food & Drink round](screenshots/food-drink-playing.png).

## Check before publishing

- Solid background in **both** themes; opaque clue surface; readable contrast.
- No gradient brush, glow implementation, emoji or font-glyph UI icon.
- No table perimeter, playing-card suits or decorative deck descriptions; do not reintroduce old filler copy.
- Preserve the approved leaf, mug and motion vectors; the Characters, Silent Acting and Food & Drink marks must use the same original vector style. Patterns stay off the felt and readable card face.
- Reach all six decks through both swipes and accessible page arrows; check both sides of the 650 dp inner-width threshold and return-to-home page memory.
- Check long deck names and Silent Acting guidance on compact layouts and with enlarged text.
- Measure timer, score and category corners before the clue; no collisions at enlarged font sizes.
- Buttons remain labeled and at least 48 dp tall; outcome vectors have accessible descriptions.
- Diagnostics remain under Tilt setup, not forced on every round.
- Run unit guardrails, Android pixel/accessibility checks, and the full gameplay smoke test.

These are project rules, not a claim that every app must look like this. Design taste still requires the user's playtest and feedback.