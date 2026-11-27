# Antitheft 1.3.0: in-game test checklist

Two players on your own server. **A** is the owner (not op). **B** is the other player (not op). Use `/op` only where a step says so.

**Supported servers:** Paper 1.18.2 – 26.3, and Paper forks such as Purpur and Folia. Java 17 or newer (whatever your Minecraft version needs). Spigot and CraftBukkit are not supported; on them the plugin disables itself with a clear message.

## Setup
1. Put `antitheft-1.3.0.jar` in `plugins/` and start the server. The console shows `Enabling Antitheft v1.3.0` and `N lockable block types loaded.`. On versions older than 26.3 it also lists the blocks that don't exist yet on that version (`Not on this Minecraft version, skipped: ...`). That is normal. On 1.21.9+ it also says `Copper golem protection enabled.`
2. **Upgrading from an older version:** your old `plugins/Antitheft/config.yml` and `messages.yml` are kept. To match 1.3.0, set `auto-lock-on-place: false` there and add `- JUKEBOX` to `lockable-blocks`, then run `/antitheft reload`. Existing locks and access lists keep working.
3. Both players join once. Give them the items (console, without the `/`):
```
give A chest 8
give A barrel 4
give A hopper 4
give A hopper_minecart 2
give A rail 8
give A red_shulker_box 1
give A jukebox 1
give A music_disc_cat 1
give A lectern 1
give A cobblestone 64
give A tnt 4
give A flint_and_steel 1
give A creeper_spawn_egg 2
give B chest 4
give B hopper 4
give B hopper_minecart 2
give B rail 4
give B stone 8
```
On 1.21.9+ also: `give A copper_chest 4`, `give A carved_pumpkin 1`, `give A copper_block 1`.

## Checklist

### Locking
- [ ] **1. No auto-lock.** A places a chest. No "locked automatically" message. `/lockinfo` says "That block isn't locked", and B can open it.
- [ ] **2. /lock.** A looks at the chest and runs `/lock` → "Block locked". `/lockinfo` shows `Owner: A`.
- [ ] **3. /unlock.** A runs `/unlock` on the barrel → "Lock removed". B can open it now. A runs `/lock` again.
- [ ] **4. No limit.** A places 20 or more blocks and runs `/lock` on each. Every one shows `Owner: A`.

### Denying B
- [ ] **5. Open.** B right-clicks A's chest → nothing opens, B sees "This block is locked by A".
- [ ] **6. Break.** B breaks A's chest → it stays, B sees "Only the owner can break it".
- [ ] **7. Commands.** B runs `/unlock`, `/lock add B`, and `/lock` on A's chest → "Only the owner can do that" or "already locked by A". The lock is unchanged.
- [ ] **8. Other blocks.** Repeat 5 with a furnace, a hopper, a shulker box, a lectern with a book, and a jukebox with a disc owned by A (and a copper chest on 1.21.9+). B is denied each time and can't take the book or the disc.
- [ ] **9. Clicking a locked block.** B holds a hopper and right-clicks A's chest → nothing is placed against it.

### Per-lock access
- [ ] **10. Add.** A looks at the chest and runs `/lock add B`. B can open it. `/lockinfo` (as A) shows `Access: B`.
- [ ] **11. Remove.** A runs `/lock remove B`. B is denied again.
- [ ] **12. Only that block.** While B has access to the chest (step 10), B is still denied at A's other locked blocks.
- [ ] **12b. Co-owner.** A locks a barrel and runs `/lock give ownership B` → "B is now a co-owner of this block". A is still the owner. B can open it, `/lockinfo` shows `Co-owners: B`, and B can run `/lock add` on it. B can't run `/lock give ownership` (owner only).
- [ ] **12c. Remove a co-owner.** A runs `/lock remove ownership B` → B can no longer open or break it.
- [ ] **12d. Re-lock keeps access.** A runs `/lock add B` on a chest, then `/unlock`, then `/lock` again → "Its co-owners and access list from before are back", and B can open it. If instead B locks the chest after A's `/unlock`, B's lock starts with an empty list.

### Help
- [ ] **12e. `/lock help`.** Any player runs `/lock help` → a short, readable list of all player commands. An op also sees the admin commands.

### Global trust
- [ ] **13. Trust.** A runs `/trust B`. B can open all of A's locks. `/trustlist` (as A) shows B.
- [ ] **14. Untrust.** A runs `/untrust B`. B is denied everywhere again, straight away.
- [ ] **14b. Future locks.** A runs `/trust B`, then locks a brand-new chest → B can open it straight away. A runs `/untrust B` afterwards.
- [ ] **14c. Co-owner of everything.** A runs `/trust ownership B` → B can open, break and `/unlock` all of A's locks, including a chest A locks afterwards. `/trustlist` shows B under "Co-owners". A runs `/untrust ownership B` → B is denied again.
- [ ] **15. Owner offline.** A runs `/trust B`, then A logs out. B can still open A's chests. B relogs and can still open them. (A runs `/untrust B` afterwards.)

### Double chests
- [ ] **16. Both halves.** A places two chests side by side to make a double chest. B is denied on both halves.
- [ ] **17. Extending someone else's chest.** A places a single chest (locked). B places a chest right next to it → placement is cancelled, B sees "You can't connect a chest to A's locked chest".
- [ ] **18. Extending your own.** A places a chest next to their own locked single chest → it becomes a double chest and both halves are locked to A (no `/lock` needed for the new half).

### Hoppers
Put 32 cobblestone in A's chest (locked with `/lock`), on a block 1 high. Break the block under it so the space under the chest is free.
- [ ] **19. B's hopper.** B places a stone next to the free space and places a hopper against that stone, so the hopper ends up under A's chest → no items move.
- [ ] **20. B's hopper minecart.** B places a rail under A's chest and puts a hopper minecart on it → no items move.
- [ ] **21. A's own hopper, no `/lock`.** A places a hopper under their own locked chest → items move into it straight away. A's hopper minecart does the same.
- [ ] **22. Hopper chains and pushing in.** A's hopper under the chest points into a second chest that A locked → items flow from the first locked chest, through the hopper, into the second locked chest. B's hopper pointing *into* A's chest from the side also delivers items.

### Explosions and fire
- [ ] **23. TNT.** Put A's locked chest and an unlocked chest next to TNT and light it. The locked chest survives; the unlocked one is destroyed.
- [ ] **24. Creeper.** Same with a creeper (spawn egg; in survival, let it see you and explode). The locked one survives.
- [ ] **25. Fire.** Light fire next to A's locked lectern. The lectern never burns away.

### Shulker boxes
- [ ] **26.** A places a shulker box with items in it, runs `/lock`, then breaks it. A places it again: `/lockinfo` says it isn't locked. The items are still inside.

### Copper golem (1.21.9+ only)
- [ ] **27.** Build a copper golem next to A's locked copper chest with items in it, and an empty chest nearby. The golem does not take items out of the locked copper chest. `/unlock` it → the golem starts sorting.

### Admin
- [ ] **28. Bypass.** `/op B`. B can open and break A's locked blocks.
- [ ] **29. admin-unlock.** B (op) runs `/lock admin-unlock` on one of A's locks → "Removed A's lock". `/deop B`, then B runs it on another lock → "You don't have permission to do that."
- [ ] **30. Reload.** Edit a line in `messages.yml`, run `/antitheft reload` as op → the new text shows.

### Restart
- [ ] **31.** With A's locks, B's per-lock access on one chest, and A trusting B, run `stop` and start the server again. Every lock, the access list and the trust still work as before, and B is still denied where B has no access.

## Done when
All boxes for your version are ticked.
