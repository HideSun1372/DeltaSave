# ✨ DeltaSave

> *"The power of saving shines within you."*

**DeltaSave** is a Deltarune-inspired checkpoint save system for Minecraft servers. Place sacred save blocks across your world, let players preserve their progress, roll back mistakes, and carry their adventure forward — all wrapped in a sleek, themed GUI experience.

---

## 🌟 Features

### 💾 Checkpoint Save System
The heart of DeltaSave — a full-state save and load system that captures everything:

* 📍 **Location** — World, coordinates, and rotation
* ❤️ **Health & Food** — Including max health and saturation
* 🎒 **Full Inventory** — All 36 slots plus armor
* 🧪 **Potion Effects** — Every active effect preserved
* 🎮 **Gamemode** — Saved and restored seamlessly
* ⏱️ **Playtime** — Accumulated across all sessions

### 🔮 Save Blocks
Admins can craft unique save blocks tied to a **location name** and **chapter**. Players interact with these beacons to open the Save Menu — no commands needed. Save blocks are automatically scanned and unregistered if ever destroyed, keeping your world clean.

> Standing on a save block? You might receive a message from the save gods.

### 🧱 Block Change Rollback
DeltaSave tracks every block placed and broken since the last save — held in memory for zero-lag gameplay. On `/load`, those changes are **non-destructively rolled back**, undoing only what happened after the last checkpoint. Intentional world changes stay safe on disk.

### 📦 Chapter-Based Storage
Every save block is assigned a chapter. The higher the chapter, the more storage pages a player unlocks — **18 slots per chapter**, scaling infinitely. Storage persists across sessions and server restarts.

### 🖥️ GUI Interface
A beautiful, Deltarune-styled inventory GUI accessible from any save block:

| Menu | Description |
|------|-------------|
| **SAVE** | Save your current state and close the menu |
| **CHECK** | View your health, location, playtime, and block stats |
| **STORAGE** | Access your chapter-scaled persistent storage |
| **DELETE SAVE** | Two-step deletion with timed confirmation |

### 🔄 Auto Join & Respawn
- Players who rejoin are **automatically loaded** to their last checkpoint
- On death, players **respawn at their last save** — no manual intervention needed
- New players with no save are teleported to the configurable **default spawn**

---

## 📜 Commands

| Command | Usage | Description |
|---------|-------|-------------|
| `/save` | `/save [player]` | 💾 Save your (or another player's) game state |
| `/load` | `/load [player]` | 📂 Load from last checkpoint and roll back unsaved changes |
| `/deletesave` | `/deletesave [player]` | 🗑️ Delete a save file (double-confirm for self) |
| `/saveinfo` | `/saveinfo [player]` | 🔍 View detailed save data — location, health, playtime, blocks |
| `/storage` | `/storage [player]` | 📦 Open a player's persistent storage inventory |
| `/givesaveblock` | `/givesaveblock "<Name>" <Chapter>` | 🔮 Create a named save block beacon item |
| `/setdefaultspawn` | `/setdefaultspawn` | 🌍 Set the server's default spawn from your current position |
| `/togglebeacongui` | `/togglebeacongui` | 🚫 Toggle blocking of the vanilla beacon GUI |
| `/scannow` | `/scannow` | 🔎 Instantly scan for and unregister missing save blocks |

> **Example:** `/givesaveblock "Card Castle - ??????" 1`

---

## ⚙️ Configuration

`config.yml` gives you full control over DeltaSave's behavior:

```yaml
prefix: "§6[SAVE] §r"               # Chat message prefix
block-beacon-gui: true              # Block vanilla beacon GUI on right-click
scan-interval: 1200                 # Save block integrity scan frequency (ticks)
beacon-message-cooldown: 140        # Cooldown between motivational messages (ticks)
delete-confirm-timeout-gui: 160     # GUI delete confirmation window (ticks)
delete-confirm-timeout-command: 160 # Command delete confirmation window (ticks)
```

Fully customize every **message**, **sound effect**, and **beacon flavor text** straight from the config.

---

## 🔐 Permissions

| Permission | Description |
|------------|-------------|
| Operator | Required for all save management commands |
| `deltasave.admin` | Allows acting on other players (`/save <player>`, `/load <player>`, `/deletesave <player>`, `/givesaveblock`) |

---

## 📂 Data Storage

| File | Contents |
|------|----------|
| `saves/<uuid>.yml` | Full player save state |
| `storage/<uuid>.yml` | Chapter-scaled persistent item storage |
| `saveblocks.yml` | Registry of all placed save block locations |

---

## 🛠️ Compatibility

- **Minecraft API:** `26`
- **Latest Plugin Version:** `1.3`

---

* Built with **DETERMINATION**.
