# Design rules — Off the Top

## Research and its limits

Reviewed 2026-09-08; updated with the user's approved direction on 2026-09-09. “AI-looking” is an aesthetic judgment, not a reliable test of authorship. For this game, the user's preference is decisive: **flat green table, cream cards, red printed backs, original category marks, green correct and red pass**. The user explicitly requested **no outer poker-table outline**. No gradients, emoji icons, card suits or habitual subtext. Set's print-inspired type, hard edges and pressed buttons stay.

The green/red Card Table preview was selected and approved for native implementation. This supersedes the earlier teal-only/no-category-art experiment. The three original category drawings are deliberate card-corner artwork, not arbitrary decorative icon tiles.

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
| Emoji or font glyphs used as icon assets | Material Sharp action icons plus original leaf/mug/motion category vectors, with consistent stroke and no playing-card suits. Normal punctuation is fine. |
| Heading + subtitle + badge repeated everywhere | One clear label. Card counts stay because they are useful data. No automatic explanation under each setting or deck. |
| Decorative icon tiles with arbitrary pictures | The user approved category marks printed directly in opposing card corners. No stand-alone decorative picture tiles or phone illustration. |
| Icon-only controls in the name of minimalism | Back, Start round, Pass and Correct remain visibly named. Outcomes retain screen-reader descriptions; actions expose their purpose. |
| Slogans, fake urgency and marketing copy in a utility/game screen | No “nice guessing,” promo badges, ads claims, taglines or novelty labels on the play path. |
| Every element competing as a filled card or accent | Green marks correct; red marks pass and card backs. Cream faces use dark green ink. Both day/night modes preserve that meaning. |
| Replacing one default recipe with another | Keep the existing type, familiar game flow, sharp borders and small hard button shadows; no unrelated redesign. |
| Removing useful guidance just to reduce word count | Keep one forehead instruction, the countdown hold prompt, readiness only when needed, and the explicit How to play screen. |

## Check before publishing

- Solid background in **both** themes; opaque clue surface; readable contrast.
- No gradient brush, glow implementation, emoji or font-glyph UI icon.
- No table perimeter, playing-card suits or decorative deck descriptions; do not reintroduce old filler copy.
- Category vectors must match the approved original leaf, mug and motion SVGs. Patterns stay off the felt and readable card face.
- Measure timer, score and category corners before the clue; no collisions at enlarged font sizes.
- Buttons remain labeled and at least 48 dp tall; outcome vectors have accessible descriptions.
- Diagnostics remain under Tilt setup, not forced on every round.
- Run unit guardrails, Android pixel/accessibility checks, and the full gameplay smoke test.

These are project rules, not a claim that every app must look like this. Design taste still requires the user's playtest and feedback.