# Design rules — Off the Top

## Research and its limits

Reviewed 2026-09-08. “AI-looking” is an aesthetic judgment, not a reliable test of authorship. Gradients, common fonts and explanatory text are not inherently bad. For this game, the user's preference is decisive: **no gradients, emoji icons, decorative icon tiles, or habitual subtext**. Teal and the print-inspired typography stay.

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
| Generic “polish” through gradients, glows, glass or decorative blobs | Solid opaque navy and ivory. Solid slightly raised card surfaces. No background effects. |
| Emoji or font glyphs used as icon assets | Material Sharp vector icons only, consistent size/weight. Normal punctuation is fine; no character arrows/checkmarks as icons. |
| Heading + subtitle + badge repeated everywhere | One clear label. Card counts stay because they are useful data. No automatic explanation under each setting or deck. |
| Decorative icon tiles with arbitrary pictures | Remove the deck pictures and phone illustration; keep the actual deck names and essential hold instruction. |
| Icon-only controls in the name of minimalism | Back, Start round, Pass and Correct remain visibly named. Outcomes retain screen-reader descriptions; actions expose their purpose. |
| Slogans, fake urgency and marketing copy in a utility/game screen | No “nice guessing,” promo badges, ads claims, taglines or novelty labels on the play path. |
| Every element competing as a filled card or accent | Teal marks important actions/results. Navy/ivory do the rest. No new accent palette per deck. |
| Replacing one default recipe with another | Keep the existing type, familiar game flow, sharp borders and small hard button shadows; no unrelated redesign. |
| Removing useful guidance just to reduce word count | Keep one forehead instruction, the countdown hold prompt, readiness only when needed, and the explicit How to play screen. |

## Check before publishing

- Solid background in **both** themes; opaque clue surface; readable contrast.
- No gradient brush, glow implementation, emoji or font-glyph UI icon.
- No decorative deck descriptions or pictures; do not reintroduce old filler copy.
- Buttons remain labeled and at least 48 dp tall; outcome vectors have accessible descriptions.
- Diagnostics remain under Tilt setup, not forced on every round.
- Run unit guardrails, Android pixel/accessibility checks, and the full gameplay smoke test.

These are project rules, not a claim that every app must look like this. Design taste still requires the user's playtest and feedback.