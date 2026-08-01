# VisualWand

**In-Game Display Entity Editor for Paper**

A plugin that allows players and administrators to create, edit, and animate Display Entities (Block Display, Item Display, Text Display) without typing complicated commands.

**⚠️ Still in development, please report all bugs on our Discord or via GitHub!**

---

## ✨ Main Features

### 🎯 "Point and Click" Editing (Ray-tracing)
- No commands required
- Hold the **Architect's Wand** and right-click the air to create an object
- Right-click an existing object to select it and open its edit menu
- Choose a transformation mode, then use **LMB to increase** and **RMB to decrease**
- Press **Drop** while holding the wand to reopen the mode menu
- Keep the wand selected and hold **Shift** while scrolling to cycle line-of-sight displays inside
  the forward view cone and configured range (8 blocks by default)
- Starting a cycle exits any current editor selection; **RMB** opens the highlighted display

![Tool](https://cdn.modrinth.com/data/cached_images/0b766122818bd6540b28292eb473b86ef405b923.png)

### 🔧 Click Editing and Transformation Gizmo
- Choose from exactly 15 modes: Move XYZ, Left Rotation XYZ, Right Rotation XYZ, Scale XYZ,
  Uniform Scale, Entity Yaw, and Entity Pitch
- Fine, Normal, and Coarse step presets persist when selecting another display
- Visual particles show translation arrows, rotation circles, or scale handles for the active mode
- **Red axis = X**, **Green axis = Y**, **Blue axis = Z**
- The gizmo is read-only feedback; all edits use normal unmodified Minecraft clicks

![Gizmo](https://cdn.modrinth.com/data/cached_images/741f8db2fcba91c1f1c574b1053278a48caa8539.jpeg)

### 🎨 Custom Model Data Support
- Plugin automatically supports Resource Packs
- Easy placement of custom furniture, hats, and decorations
- No client-side mods required

![CMD](https://cdn.modrinth.com/data/cached_images/2a2e4a22dc768e37ef851aab51fedda1cc427703_0.webp)

### ✨ Simple Animations
- **Slow Rotation** - perfect for trophies and lootboxes
- **Levitation** - up-down floating for signposts
- **Pulsing** - size changing for attention-grabbing

### 🌐 Multi-language Support
- Switch language in-game with `/vw lang en` or `/vw lang pl`
- Wand items update automatically when language changes
- Currently available: English, Polish

### 🎨 Text Display Features
- **Color Selection** - choose text color from dye menu
- **Rotation Reset** - easily reset rotation to default

---

## 📦 Installation

1. Download the `.jar` file from
2. Place it in the `plugins/` folder of your Paper server
3. Restart the server
4. Done!

---

## 📋 Commands

| Command            | Description              | Permission         |
|--------------------|--------------------------|--------------------|
| `/vw wand`         | Get the Architect's Wand | `visualwand.give`  |
| `/vw reload`       | Reload configuration     | `visualwand.admin` |
| `/vw lang <pl/en>` | Change language          | `visualwand.admin` |
| `/vw help`         | Show help                | `visualwand.use`   |
| `/vwgive`          | Shortcut to get the wand | `visualwand.give`  |

## 🔐 Permissions

| Permission         | Description                      | Default |
|--------------------|----------------------------------|---------|
| `visualwand.use`   | Use the wand and editor          | OP      |
| `visualwand.give`  | Ability to receive the wand      | OP      |
| `visualwand.admin` | Full access (includes all above) | OP      |

---

## 🎮 How to Use?

### Creating Objects
1. Type `/vw wand` to get the Architect's Wand
2. Aim at the location where you want to create an object
3. Click **RMB** (right mouse button) in the air
4. Select object type in the menu:
   - **Block Display** - any block
   - **Item Display** - any item (supports CMD!)
   - **Text Display** - text with formatting

### Editing Objects
1. Aim the wand at an existing Block, Item, or Text Display
2. Click **RMB** to select it and open the edit menu
3. Open **Transformations** and choose one of the 15 modes; the menu closes immediately
4. Use ordinary **LMB to increase** and **RMB to decrease** the selected value
5. Held clicks repeat only at the configured bounded rate
6. Press **Drop** while holding the wand to reopen the transformation menu
7. Use the menu for presets, undo/redo, transform copy/paste, resets, cancel, or deselect

To choose among nearby displays, keep the wand selected and hold **Shift** while scrolling.
Cycling first exits any current editor selection, then moves through line-of-sight displays inside
the forward view cone and configured range (8 blocks by default). Press **RMB** to open the
highlighted display.

Animations and entity-specific properties remain available from the edit menu. No client mod is
required.

### Deleting Objects
- Select the object, then use the **TNT Delete** control in its edit menu.

---

## ⚙️ Configuration

The `config.yml` file allows customization:

```yaml
wand:
  material: BLAZE_ROD

gizmo:
  particle-density: 20
  size: 1.5
  update-interval: 2

editor:
  max-distance: 50.0
  targeting:
    cycle-range: 8.0
  steps:
    translation: {fine: 0.01, normal: 0.1, coarse: 1.0}
    rotation-degrees: {fine: 1.0, normal: 5.0, coarse: 15.0}
    scale: {fine: 0.01, normal: 0.1, coarse: 0.5}
  input:
    initial-delay-ticks: 6
    repeat-interval-ticks: 2
    release-gap-ticks: 8

animations:
  tick-rate: 2
```

---

## 💾 Data Storage

- All created Display Entities are saved in `displays.yml`
- Automatic save every 5 minutes (configurable)
- Save on server shutdown
- Animations are restored after restart

## 🛠️ Requirements

- **Paper** (or compatible fork)
- **Java 21** or newer

---

## 🤝 Issues

Issue reporting and contact: https://nariaris.com/VisualWand

## 📄 License

MIT License

---

**Created with ❤️ by Nariaris**
