# Changelog

All notable changes to Antitheft. Versions follow [Semantic Versioning](https://semver.org/).

## [1.3.0] - 2026-11-27

### Added
- **Co-owners for one lock:** `/lock give ownership <player>` and `/lock remove ownership <player>`. A co-owner can open, break and `/unlock` the block and use `/lock add` / `/lock remove` on it. Only the owner adds or removes co-owners.
- **Co-owners for all your locks:** `/trust ownership <player>` and `/untrust ownership <player>`. Covers every lock you have now and every lock you make later. `/trustlist` now lists co-owners too.
- **`/lock help`:** every command explained in plain language. Admins also see the admin commands. The text is in `messages.yml` (`help`, `help-admin`).
- `/lockinfo` shows co-owners to the owner, co-owners and admins.

### Changed
- **`/lock give <player>` is now `/lock give ownership <player>` and adds a co-owner instead of handing the lock over.** The old form shows the new usage and changes nothing.
- **`/unlock` remembers the block's access list and co-owners.** When the same owner runs `/lock` on it again, they come back ("its co-owners and access list from before are back"). If someone else locks the block, they start with an empty list. `/lock admin-unlock` still removes everything.
- Co-owners count as having access everywhere access matters, including hoppers they placed.
- An unknown `/lock` subcommand now points to `/lock help`.

## [1.2.0] - 2026-11-27

### Added
- `/lock give <player>`: the owner makes another player the owner of the lock. Both halves of a double chest change owner. The new owner is taken off the access list, everyone else on it keeps their access, and the old owner loses access unless the new owner adds or trusts them. Uses the existing `antitheft.lock` permission.
- Antitheft now remembers who placed each hopper, the same way it already did for hopper minecarts.

### Changed
- **Hoppers work for the people who may use the locked block, with no `/lock` needed.** A hopper or hopper minecart may pull items out of a locked block when the player who placed it has access (owner, per-lock access, or trusted). A hopper's own lock still counts first if it has one. Hoppers placed by other players still can't pull anything out.

### Fixed
- Hoppers not pulling from locked containers (chests, furnaces, barrels and so on), which stopped hopper chains, sorting systems and furnace output at the locked block. Pushing items into a locked block always worked and still does.
- New messages that an older `messages.yml` doesn't have yet (for example `ownership-given`) showed their key name instead of text. They now fall back to the built-in text.

### Notes
- Hoppers placed before 1.2.0 don't know who placed them. `/lock` them once, or break and place them again.
- Droppers, dispensers and crafters fired by redstone are unchanged.

## [1.1.1] - 2026-11-25

### Changed
- Auto-lock on placement is now **off by default** (`auto-lock-on-place: false`). Players lock blocks with `/lock`. Existing servers keep their old `config.yml`, so set it to `false` there yourself.

## [1.1.0] - 2026-11-25

### Added
- One jar for **Paper 1.18.2 – 26.3** and Paper forks (Purpur, Folia), compiled for Java 17.
- **Folia** support.
- Fire protection: locked lecterns, chiseled bookshelves and shelves can't burn away.
- Jukeboxes are lockable.
- Copper golem protection turns itself on only on servers that have copper golems (1.21.9+).
- Clear message and self-disable on Spigot and CraftBukkit, which aren't supported.
- Startup summary of lockable blocks and of block names this Minecraft version doesn't have.
- Startup warning if Paper's `hopper.disable-move-event` is on, because that would let hoppers drain locked blocks.

### Changed
- Access lists are stored in a format every supported version can read. Locks from 1.0.0 are still read and converted the next time they change.

### Fixed
- Trust lists are now also loaded when a player joins, as a fallback if the pre-login load didn't run.

## [1.0.0] - 2026-11-11

### Added
- First release, for Paper 26.2 and 26.3.
- `/lock`, `/unlock`, `/lockinfo`, `/lock add`, `/lock remove`, `/lock admin-unlock`, `/trust`, `/untrust`, `/trustlist`, `/antitheft reload`.
- Protection against opening, breaking, explosions, hoppers, connecting a second chest, and copper golems.
- Locks stored on the block itself; unlimited locks per player; trust lists saved off the main thread.
- Configurable lockable blocks (`config.yml`) and messages (`messages.yml`, MiniMessage).
