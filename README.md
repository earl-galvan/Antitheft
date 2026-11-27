<img src="icon.png" width="128" alt="Antitheft icon">

# 🔒 Antitheft

**Lock your chests and other storage blocks so other players can't open, break or drain them.**
Server-side only — players don't need to install anything.

| | |
|---|---|
| **Version** | 1.3.0 |
| **Servers** | Paper 1.18.2 – 26.3, plus Paper forks such as Purpur and Folia (one jar for all) |
| **Java** | 17 or newer (whatever your Minecraft version needs) |
| **Dependencies** | None |
| **Not supported** | Spigot and CraftBukkit (the plugin disables itself there with a message) |

---

## ⬇️ Download

**[Download antitheft-1.3.0.jar](https://github.com/earl-galvan/Antitheft/raw/main/downloads/antitheft-1.3.0.jar)** (38 KB) · [Changelog](CHANGELOG.md)

One jar for Paper, Purpur and Folia, Minecraft 1.18.2 – 26.3. Put it in your server's `plugins/` folder and restart.

---

## 🆕 New in 1.3.0

- 👑 **Co-owners.** `/lock give ownership <player>` lets a friend open, break and manage one of your locks with you. `/trust ownership <player>` makes them co-owner of **all** your locks, including ones you make later.
- 🔁 **Access survives re-locking.** `/unlock` a block and `/lock` it again, and its access list and co-owners come back.
- ❓ **`/lock help`** shows every command in plain language, right in chat.

---

## ✨ Features

- 🔐 **Lock a block** with `/lock`, remove it with `/unlock`, and see the owner with `/lockinfo`.
- 👥 **Share one lock** with `/lock add <player>` and `/lock remove <player>`.
- 👑 **Add a co-owner to one lock** with `/lock give ownership <player>`, remove them with `/lock remove ownership <player>`.
- 🤝 **Trust a player** with `/trust <player>` to let them open all of your locks, now and in the future.
- 🏠 **Share everything with a partner** using `/trust ownership <player>`: they co-own all your locks, now and in the future.
- ❓ **`/lock help`** lists the commands for new players.
- ♾️ **No limit** on how many locks a player can have.
- 🪣 **Hopper-friendly:** hoppers and hopper minecarts placed by you, or by anyone with access, move items in and out of your locked blocks normally.
- ⚙️ **Auto-lock on placement** is available but **off by default**. Server owners can turn it on in `config.yml`.

## 🛡️ What a lock protects against

Anyone without access is stopped from:

| Threat | Protected |
|---|:---:|
| Opening it (right-click) | ✅ |
| Breaking it (only the owner, a co-owner, or an admin with `antitheft.bypass`, can) | ✅ |
| Explosions (TNT, creepers, beds, end crystals and so on) | ✅ |
| Fire (lecterns, bookshelves, shelves) | ✅ |
| Hoppers and hopper minecarts **they** placed pulling items out | ✅ |
| Connecting a chest to it to make a double chest | ✅ |
| Copper golems taking items out (1.21.9+) | ✅ |

Both halves of a double chest share one lock. Breaking a locked shulker box removes its lock, so the item doesn't carry it.

---

## 🤝 Sharing your locks

| Command | Works on | Can open | Can break & manage* | Covers locks you make later |
|---|---|:---:|:---:|:---:|
| `/lock add <player>` | One block | ✅ | ❌ | — |
| `/lock give ownership <player>` | One block | ✅ | ✅ | — |
| `/trust <player>` | All your locks | ✅ | ❌ | ✅ |
| `/trust ownership <player>` | All your locks | ✅ | ✅ | ✅ |

\*Manage means `/unlock`, `/lock add` and `/lock remove`. Only the real owner adds or removes co-owners.

Access and co-owners on a block stay through restarts, through extending it into a double chest, and through `/unlock` followed by `/lock` again by the same owner. If someone else locks a block you unlocked, they start with an empty list.

---

## 📦 Lockable blocks

Set in `config.yml` under `lockable-blocks`. The default list covers every storage block in the game:

- **Chests:** chests, trapped chests, copper chests
- **Storage:** barrels, shulker boxes
- **Redstone:** hoppers, droppers, dispensers, crafters
- **Cooking & brewing:** furnaces, smokers, blast furnaces, brewing stands
- **Decorative storage:** chiseled bookshelves, decorated pots, lecterns, jukeboxes, shelves

Blocks that don't exist on your Minecraft version are skipped, so the same list works everywhere.

---

## 🪣 How hoppers work

| Situation | Result |
|---|---|
| Your hopper under your own locked chest | ✅ Pulls items, no `/lock` needed |
| Hopper placed by a co-owner, someone you added (`/lock add`) or someone you trust (`/trust`) | ✅ Pulls items |
| Hopper placed by someone without access | ❌ Pulls nothing |
| Any hopper pushing items **into** a locked block | ✅ Always works |
| Hopper chain: locked chest → your hopper → another locked chest | ✅ Items flow through |

---

## 💬 Commands & permissions

| Command | What it does | Permission | Default |
|---|---|---|---|
| `/lock help` | Show all commands in plain language | `antitheft.lock` | Everyone |
| `/lock` | Lock the block you're looking at | `antitheft.lock` | Everyone |
| `/lock add <player>` | Let a player open this block (owner or co-owner) | `antitheft.lock` | Everyone |
| `/lock remove <player>` | Take that away (owner or co-owner) | `antitheft.lock` | Everyone |
| `/lock give ownership <player>` | Make a player co-owner of this block (owner only) | `antitheft.lock` | Everyone |
| `/lock remove ownership <player>` | Remove a co-owner (owner only) | `antitheft.lock` | Everyone |
| `/unlock` | Remove the lock (owner or co-owner) | `antitheft.unlock` | Everyone |
| `/lockinfo` | Show the owner (owners and co-owners also see who else has access) | `antitheft.lockinfo` | Everyone |
| `/trust <player>` | A player may open all your locks, now and later | `antitheft.trust` | Everyone |
| `/trust ownership <player>` | A player co-owns all your locks, now and later | `antitheft.trust` | Everyone |
| `/untrust <player>` | Undo `/trust` | `antitheft.untrust` | Everyone |
| `/untrust ownership <player>` | Undo `/trust ownership` | `antitheft.untrust` | Everyone |
| `/trustlist` | List who you trust and who co-owns your locks | `antitheft.trustlist` | Everyone |
| `/lock admin-unlock` | Remove anyone's lock | `antitheft.adminunlock` | Op |
| `/antitheft reload` | Reload `config.yml` and `messages.yml` | `antitheft.reload` | Op |
| — | Open and break any locked block | `antitheft.bypass` | Op |

> **Note:** Block commands act on the block you're looking at, within 5 blocks. Commands that name a player only work for players who have joined the server before.

---

## 🚀 Installation

1. Put `antitheft-1.3.0.jar` in your server's `plugins/` folder.
2. Restart the server.
3. *(Optional)* Edit `plugins/Antitheft/config.yml` and `messages.yml`, then run `/antitheft reload`.

Messages, including the `/lock help` text, use the [MiniMessage](https://docs.advntr.dev/minimessage/format.html) format.

> **Upgrading?** Your old `config.yml` and `messages.yml` are kept; new messages use the built-in text. **`/lock give <player>` is now `/lock give ownership <player>`, and it adds a co-owner instead of handing the lock over.** To match 1.3.0, set `auto-lock-on-place: false` and add `- JUKEBOX` to `lockable-blocks`. Existing locks keep working. Hoppers placed before 1.2.0 don't know who placed them: `/lock` them once, or break and place them again.

---

## ⚙️ How it works

- **Locks are stored on the block itself** (in its saved data), so they load and unload with the world. No database, no global list.
- **`/unlock` remembers the block's access list and co-owners** on the block, so the same owner gets them back with `/lock`.
- **Hoppers remember who placed them**, so the plugin knows whose hopper is pulling from a locked block.
- **Trust and co-owner lists** are small files in `plugins/Antitheft/trust/`, one per player. They're loaded when a player logs in and saved on a background thread, never on the main server thread.
- **Hopper checks are cheap:** pushes are skipped right away, and only pulls from a lockable block are checked.
- **Folia-safe:** no Bukkit scheduler calls, and all shared state is thread-safe.
- **Startup warning** if Paper's `hopper.disable-move-event` is turned on, because that would let hoppers drain locked blocks.

---

## ⚠️ Known limits

- Hoppers placed before 1.2.0, or by a dispenser or a command, don't know who placed them, so they can't pull from locked blocks until someone with access `/lock`s them.
- A globally trusted player's (or global co-owner's) hopper pulls only while the owner or that player is online. Per-lock access and co-owners have no such limit.
- Droppers, dispensers and crafters powered by someone else's redstone can still push items out, because the server doesn't say who powered them.
- Withers and the ender dragon are not stopped.

---

## 🔧 Build
```
./gradlew build
```
The jar is written to `build/libs/antitheft-1.3.0.jar`. You need JDK 17 or newer to run Gradle; the wrapper downloads Gradle 9.2.1.

To check the code against another Paper API version:
```
./gradlew compileJava --rerun-tasks -PpaperApi=26.3.build.262-stable -PjavaRelease=25
```

## 🧪 Testing
- **Compiles** against Paper API 1.18.2, 1.19.4, 1.20.4, 1.20.6, 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2, 26.2 and 26.3, with no deprecated or soon-removed calls.
- **Automated server tests** on real Paper servers of all of those versions, including a restart.
- **Two real clients** in multiplayer on Paper 1.18.2, 1.20.4, 1.21.11, 26.1.2 and Folia 1.21.11, 26.1.2.
- `TESTING.md` is the in-game checklist for two players.
