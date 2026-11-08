# Interfaces: research and enforceable rules

Reviewed 2026-11-08. These are observations, not a request to copy another server's art.

## References studied

1. **Hypixel Bed Wars item shop.** The official forum's BedWars guide describes a recommended-items page for new players, separate item categories, explicit resource costs and a persistent shop entry point. The shop discussion also illustrates a real failure mode: clustering purchases by resource without enough separation makes misclicks easier. We take category discipline and a beginner-friendly first page, not the Bed Wars economy. [1](https://hypixel.net/threads/guide-bedwars-guide-everything-you-need-to-know.1198034/) [2](https://hypixel.net/threads/bedwars-shop-gui.4436000/)
2. **Hypixel My Profile and its achievement children.** I inspected the labelled nine-column profile screenshot in the official server guide (`TbkSJsA.png`). It groups destinations into rows and reserves the bottom row for utilities. The achievement guide documents a back arrow returning to Profile, a category-specific back target, completion counts in hover text, and stack counts that mean achievement points rather than inventory quantity. The useful lesson is consistency of the return affordance and one documented meaning for a count, not copying its density. [2](https://hypixel.net/threads/guide-hypixel-server-basics-features-statistics-and-more.4285518/) [5](https://hypixel.net/threads/guide-the-ultimate-guide-to-achievement-rewards-wip.1417864/)
3. **Cytooxien Select Language / Select Chat Community.** I inspected the actual `lang_inventory.jpg` screenshot published with Chat Overhaul. It uses sentence-leading title case, two visibly separated groups, selected-slot outlines and one confirmation control. The screenshot contains no empty state, so it is not evidence for a particular empty-state sentence. The rule we take is to name the actual object and selection, group related choices, and make confirmation explicit. Our empty-state copy is original: what is absent, why it matters, and the next useful action. [3](https://www.cytooxien.net/chat-revamp)
4. **Material Design 3.** Lists keep primary text and supporting visuals aligned, use brief labels and one to three supporting lines, and group with space before adding dividers. Buttons reserve visual emphasis for the primary action; labels describe a discrete action. Material's state guidance distinguishes enabled, selected and disabled instead of using colour as the only signal. Minecraft cannot render Material containers or focus rings, so we translate the principles into item names, lore, item models and fixed hit areas; we do not pretend to implement browser accessibility. [1](https://m3.material.io/components) [5](https://m3.material.io/components/divider/overview)

Material primary sources inspected directly: [M3 lists](https://m3.material.io/components/lists/guidelines), [M3 buttons](https://m3.material.io/components/buttons/guidelines), [M3 design tokens](https://m3.material.io/foundations/design-tokens), and [Material states](https://m2.material.io/design/interaction/states.html#disabled).

## Rules enforced by this implementation

- A chest is **9 columns**, 1–6 rows, at most **54 slots**. Slot declarations are immutable data; duplicate slots and out-of-range indices fail before opening.
- One renderer owns inventory creation, invisible fill, item placement, footer and click dispatch. A screen builder cannot hand-place an item.
- Keep the existing **176-pixel / six-pixel pack grid** and the deliberate `▪` title mark. Do not resize or luminance-remap another agent's sprites.
- Use `Palette.TEXT`, `MUTED` and `DIM` plus one principal accent. Semantic state may use its semantic token, but also has words and a distinct item state. No raw colour literals in new interface code.
- Use sentence-leading title case for item labels and plain English prose. No generated small-cap English, accents, double spaces, empty names, stars, pipe separators or marketing instructions. The only in-line separator is `Palette.DIM` middot.
- A clickable name starts with a verb: **Open shop**, **Equip classic ball**, **Claim rewards**, **Go back**. Information cards are noun labels and have no action. Selected, locked and equipped states are written out.
- An unaffordable purchase uses a distinct disabled model, muted non-bold name, no purchase action, and an exact **Need N more coins** explanation with balance and price. A click cannot bypass the affordance by reaching an old slot dispatcher.
- Empty/error states share one row formatter. They name the situation and offer a real next action: find players, clear a search, play a match, browse another category, or return to the parent. No fake result card and no silent return.
- Every screen has a working back control and an explicit close control. Root back closes; child back restores the previous screen's category, filter, page and target. Home is an intentional context reset. The back stack is bounded and loop-free.
- Pagination uses fixed previous/next slots, clamps the page, labels **Page N of M**, and never silently discards the tail of a list. Top-10 leaderboards and top-nine season standings are explicitly ranked excerpts, not general lists.
- Footer destinations have distinct icons and verbs. Category icons may repeat only when they denote the same category. Counts mean visible catalog entries; remaining unlocks are a separately labelled value.
- Names, reasons, guild text and mail subjects are escaped before MiniMessage parsing. Actions are server-side callbacks, never client-supplied commands or NBT instructions.
- No new action-bar, title, toast, sound or HUD ownership is introduced. Menus use the existing personal UI click sound only; there is no same-tick HUD competition to arbitrate.

## Audit method

The baseline audit reads the actual rebuilt `releases/BosketBall-ResourcePack.zip`, resolves every literal and model-valued icon used by menus, and compares slot declarations with their handlers. The permanent test repeats the pack lookup. `docs/agents/integration/interfaces.md` records the baseline findings, the fixes, and the ownership boundary. Research downloads stay outside the deliverable; no third-party sprite is copied into the pack.
