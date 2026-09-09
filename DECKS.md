# Decks — Off the Top 1.5.0

## Counts and intentional reuse

Counts exclude blank lines and comments. A deck entry is one displayed topic title; the same title can belong in more than one themed deck.

| Deck | Entries | New titles in 1.5.0 | Reused original titles |
|---|---:|---:|---|
| Wild World | 622 | 0 | None; unchanged original deck |
| Everyday Things | 617 | 0 | None; unchanged original deck |
| Do Your Thing | 605 | 0 | None; unchanged original deck |
| Characters | 580 | 580 | None |
| Silent Acting | 534 | 140 | 394 exact Do Your Thing prompts |
| Food & Drink | 550 | 369 | 115 Everyday Things + 66 Wild World titles |
| **Total** | **3,508** | **1,089** | **575 intentionally reused entries** |

The original three decks retain all **1,844 distinct titles** unchanged. Adding **1,089 new titles** makes **2,933 unique topic strings** across the six decks, not 3,508 unique topics. The extra 575 entries are deliberate overlap. There are no duplicate normalized titles within any deck.

## Content and provenance

These are independently curated lists of topic names, not copied proprietary card lists. “Independently curated” describes the lists; it does not claim ownership of referenced fictional characters or franchises. No affiliation with Heads Up!, its publishers or the referenced franchises is claimed. Category artwork consists of six original vector drawings, not franchise art.

- **Wild World:** animals, plants, Earth, weather and space.
- **Everyday Things:** objects, food, tools, clothing, instruments and transport.
- **Do Your Thing:** activities, jobs, places, sports and situations.
- **Characters:** fictional characters from games, cartoons, books, comics, films and TV. Familiar pop culture is included by design. The list includes names from mature works; it is not a children-only selection. Recognition depends on age, interests, region and which stories the group knows.
- **Silent Acting:** actions, jobs and situations chosen for miming. **Clue givers act silently; the guesser can speak.** Normal tilt/touch controls and scoring still apply. The app gives the rule but does not listen for speech or enforce silence; it requests no microphone permission. Mime dangerous situations rather than attempting them.
- **Food & Drink:** ingredients, dishes, snacks and drinks, including alcoholic drinks. Names and familiarity vary across regions and cuisines. These are guessing topics, not instructions to consume anything.

No deck promises universal familiarity or a children-only content filter. Pass a prompt the group does not know or does not want to use. Exact-string totals do not prove that every concept is unrelated: similar topics can have different names.

## How repeats are avoided

- **One shared memory:** a title is remembered when it is actually displayed during a round. It is then treated as seen in every deck containing that title, including after an app restart. Full deck counts therefore differ from the current unseen counts.
- **Exact normalized titles:** identity is `word.trim().lowercase(Locale.ROOT)`. This ignores surrounding whitespace and letter case without depending on the phone's language. It does not merge aliases, plurals, punctuation changes or merely similar concepts.
- **One-time upgrade:** when the shared-memory key is absent, all legacy per-deck seen sets are combined, normalized and stored once under `seen-shared-v1`. Existing played-card memory is retained, including overlap with the new decks.
- **Reset all cards:** resetting writes an empty shared set rather than deleting its key. That key also acts as the migration marker, so old per-deck sets are not imported again on the next launch. Settings and round history are kept.
- **Exhausted deck:** if the selected deck has no unseen titles when a round starts, only that deck's title identities are removed from shared memory, then its cards are shuffled for another cycle. Those shared titles also become available in any other deck containing them. Unrelated remembered titles are retained.

The shared title memory prevents switching decks from immediately serving the same overlapping prompt again; it is not a promise that a title can never repeat after a reset or deck exhaustion.