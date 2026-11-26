<img src="icon.png" width="128" alt="Antitheft icon">

# Antitheft

A server-side Minecraft plugin that lets players lock chests and other storage blocks, so other players can't open, break or drain them. Players don't need to install anything.

- **Version:** 1.1.1
- **Servers:** Paper 1.18.2 – 26.3, and Paper forks such as Purpur and Folia. One jar for all of them.
- **Java:** 17 or newer (whatever your Minecraft version needs).
- **Not supported:** Spigot and CraftBukkit. The plugin disables itself there with a message.
- **Dependencies:** none besides the server.

## What it does
- **Lock a block** with `/lock`, remove it with `/unlock`, see the owner with `/lockinfo`.
- **Share one lock** with `/lock add <player>` and `/lock remove <player>`.
- **Trust a player** with `/trust <player>` to let them open all of your locks; `/untrust` and `/trustlist` manage the list.
- **No limit** on how many locks a player can have.
- **Auto-lock on placement** is available but **off by default**. Server owners can turn it on in `config.yml`.

A locked block is protected from anyone without access:
- opening it (right-click)
- breaking it (only the owner, or an admin with `antitheft.bypass`)
- explosions (TNT, creepers, beds, end crystals and so on)
- fire (lecterns, bookshelves, shelves)
- hoppers and hopper minecarts pulling items out
- connecting a chest to it to make a double chest
- copper golems taking items out (1.21.9+)

Both halves of a double chest share one lock. Breaking a locked shulker box removes its lock, so the item doesn't carry it.

## Lockable blocks
Set in `config.yml` (`lockable-blocks`). The default list covers every storage block in the game:
- chests, trapped chests, copper chests
- barrels, shulker boxes
- hoppers, droppers, dispensers, crafters
- furnaces, smokers, blast furnaces, brewing stands
- chiseled bookshelves, decorated pots, lecterns, jukeboxes, shelves

Blocks that don't exist on your Minecraft version are skipped, so the same list works everywhere.

## Commands and permissions
| Command | What it does | Permission | Default |
|---|---|---|---|
| `/lock` | Lock the block you're looking at | `antitheft.lock` | everyone |
| `/lock add <player>` / `remove <player>` | Give or take access to this lock (owner only) | `antitheft.lock` | everyone |
| `/unlock` | Remove your lock | `antitheft.unlock` | everyone |
| `/lockinfo` | Show the owner (the owner also sees the access list) | `antitheft.lockinfo` | everyone |
| `/trust <player>` | Trust a player with all your locks | `antitheft.trust` | everyone |
| `/untrust <player>` | Remove them | `antitheft.untrust` | everyone |
| `/trustlist` | List who you trust | `antitheft.trustlist` | everyone |
| `/lock admin-unlock` | Remove anyone's lock | `antitheft.adminunlock` | op |
| `/antitheft reload` | Reload `config.yml` and `messages.yml` | `antitheft.reload` | op |
| — | Open and break any locked block | `antitheft.bypass` | op |

Commands that work on a block act on the block you're looking at, within 5 blocks. `/lock add` and `/trust` only work for players who have joined the server before.

## Install
1. Put `antitheft-1.1.1.jar` in your server's `plugins/` folder.
2. Restart the server.
3. Edit `plugins/Antitheft/config.yml` and `messages.yml` if you want, then run `antitheft reload`.

Messages use MiniMessage format.

**Upgrading:** your old `config.yml` is kept. To match 1.1.1, set `auto-lock-on-place: false` and add `- JUKEBOX` to `lockable-blocks`. Existing locks keep working.

## How it works
- **Locks are stored on the block itself** (in its saved data), so they load and unload with the world. There's no database and no global list.
- **Trust lists** are small files in `plugins/Antitheft/trust/`, one per player. They're loaded when a player logs in and saved on a background thread, never on the main server thread.
- **Hopper checks are cheap:** pushes are skipped right away, and only pulls from a lockable block are checked.
- **A hopper may pull from a locked block** only if the hopper itself is locked by someone with access, or the hopper minecart was placed by someone with access. With auto-lock off, owners `/lock` their own hoppers.
- **Folia-safe:** no Bukkit scheduler calls, and all shared state is thread-safe.
- **Startup warning** if Paper's `hopper.disable-move-event` is turned on, because that would let hoppers drain locked blocks.

## Known limits
- A globally trusted player's hopper pulls only while the owner or that player is online. A per-lock `/lock add` has no such limit.
- Droppers, dispensers and crafters powered by someone else's redstone can still push items out, because the server doesn't say who powered them.
- Withers and the ender dragon are not stopped.

## Build
```
./gradlew build
```
The jar is written to `build/libs/antitheft-1.1.1.jar`. You need JDK 17 or newer to run Gradle; the wrapper downloads Gradle 9.2.1.

To check the code against another Paper API version:
```
./gradlew compileJava --rerun-tasks -PpaperApi=26.3.build.262-stable -PjavaRelease=25
```

## Testing
- **Compiles** against Paper API 1.18.2, 1.19.4, 1.20.4, 1.20.6, 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2, 26.2 and 26.3, with no deprecated or soon-removed calls.
- **Automated server tests** on real Paper servers of all of those versions, including a restart.
- **Two real clients** in multiplayer on Paper 1.18.2, 1.20.4, 1.21.11, 26.1.2 and Folia 1.21.11, 26.1.2.
- `TESTING.md` is the in-game checklist for two players.
