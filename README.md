# VisualWand

**In-Game Display Entity Editor for Paper**

A plugin that allows players and administrators to create, edit, and animate Display Entities (Block Display, Item Display, Text Display) without typing complicated commands.

**⚠️ Still in development, please report all bugs on our Discord or via GitHub!**

---

## ✨ Main Features

### 🎯 "Point and Click" Editing (Ray-tracing)
- No commands required
- Hold the **Architect's Wand**, right-click in the air and a GUI appears
- Select a model (block, item, or text)
- RMB on an existing object opens the edit menu
- Shift + RMB deletes the object

![Tool](https://cdn.modrinth.com/data/cached_images/0b766122818bd6540b28292eb473b86ef405b923.png)

### 🔧 Transformation Gizmo (Killer Feature!)
- Visual arrows made with particles around the object
- **Red axis = X**, **Green axis = Y**, **Blue axis = Z**
- Three modes:
  - **Move** - arrows to move the object
  - **Rotate** - circles for rotation
  - **Scale** - cubes to change size
- Feels like working in a game engine (like Unity or Blender)!

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
1. Aim at an existing Display object with the wand
2. Click **RMB** to open the edit menu
3. Options:
   - **Transformations** - move, rotate, scale
   - **Animations** - add animations
   - **Properties** - change object details
   - **Gizmo** - enable visual editor

### Using Gizmo
1. In the edit menu, click "Toggle Gizmo"
2. You'll see colored arrows/circles around the object
3. Use **LMB** (left click) to switch modes
4. Modes:
   - 🔴🟢🔵 Arrows = Moving
   - ⭕ Circles = Rotating
   - 🔶 Cubes = Scaling

### Deleting Objects
- Aim at object + **Shift + RMB** = Delete

---

## ⚙️ Configuration

The `config.yml` file allows customization:

```yaml
# Language (pl/en)
language: en

# Wand settings
wand:
  material: BLAZE_ROD

# Gizmo settings
gizmo:
  particle-density: 20
  size: 1.5
  update-interval: 2

# Editor settings
editor:
  max-distance: 50
  move-sensitivity: 0.1
  rotate-sensitivity: 5.0
  scale-sensitivity: 0.05

# Animations
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

# VisualWand (Polski / Polish)

**Edytor Display Entity w grze dla Paper**

Plugin typu "In-Game Editor" pozwalający graczom i administratorom tworzyć, edytować i animować obiekty Display Entities (Block Display, Item Display, Text Display) bez wpisywania skomplikowanych komend.

---

## ✨ Główne Funkcjonalności

### 🎯 Edycja "Wskaż i Kliknij" (Ray-tracing)
- Nie musisz wpisywać komend
- Trzymasz **Różdżkę Architekta**, klikasz prawym na powietrze i pojawia się GUI
- Wybierasz model (blok, przedmiot lub tekst)
- PPM na istniejący obiekt otwiera menu edycji
- Shift + PPM usuwa obiekt

![Tool](https://cdn.modrinth.com/data/cached_images/0b766122818bd6540b28292eb473b86ef405b923.png)

### 🔧 Gizmo Transformacji (Killer Feature!)
- Wizualne strzałki zrobione z particles wokół obiektu
- **Czerwona oś = X**, **Zielona oś = Y**, **Niebieska oś = Z**
- Trzy tryby:
  - **Przesuwanie** - strzałki do przesuwania obiektu
  - **Obracanie** - okręgi do rotacji
  - **Skalowanie** - kostki do zmiany rozmiaru
- Daje wrażenie pracy w silniku gry (jak Unity czy Blender)!

![Gizmo](https://cdn.modrinth.com/data/cached_images/741f8db2fcba91c1f1c574b1053278a48caa8539.jpeg)

### 🎨 Wsparcie dla Custom Model Data
- Plugin automatycznie wspiera Resource Packi
- Łatwe wstawianie niestandardowych mebli, czapek i dekoracji
- Bez potrzeby modów po stronie klienta

![CMD](https://cdn.modrinth.com/data/cached_images/2a2e4a22dc768e37ef851aab51fedda1cc427703_0.webp)

### ✨ Proste Animacje
- **Powolny Obrót** - idealne dla trofeów i lootboxów
- **Lewitacja** - unoszenie góra-dół dla drogowskazów
- **Pulsowanie** - zmiana rozmiaru dla przyciągania uwagi

### 🌐 Wielojęzyczność
- Zmiana języka w grze za pomocą `/vw lang pl` lub `/vw lang en`
- Różdżki aktualizują się automatycznie po zmianie języka
- Dostępne języki: Angielski, Polski

### 🎨 Funkcje Text Display
- **Wybór koloru** - wybierz kolor tekstu z menu barwników
- **Reset obrotu** - łatwy reset rotacji do wartości domyślnych

---

## 📦 Instalacja

1. Pobierz plik `.jar`
2. Umieść w folderze `plugins/` serwera Paper
3. Zrestartuj serwer
4. Gotowe!

---

## 📋 Komendy

| Komenda            | Opis                        | Uprawnienie        |
|--------------------|-----------------------------|--------------------|
| `/vw wand`         | Otrzymaj Różdżkę Architekta | `visualwand.give`  |
| `/vw reload`       | Przeładuj konfigurację      | `visualwand.admin` |
| `/vw lang <pl/en>` | Zmień język                 | `visualwand.admin` |
| `/vw help`         | Wyświetl pomoc              | `visualwand.use`   |
| `/vwgive`          | Skrót do otrzymania różdżki | `visualwand.give`  |

## 🔐 Uprawnienia

| Uprawnienie        | Opis                                      | Domyślnie |
|--------------------|-------------------------------------------|-----------|
| `visualwand.use`   | Używanie różdżki i edytora                | OP        |
| `visualwand.give`  | Możliwość otrzymania różdżki              | OP        |
| `visualwand.admin` | Pełny dostęp (zawiera wszystkie powyższe) | OP        |

---

## 🎮 Jak używać?

### Tworzenie obiektów
1. Wpisz `/vw wand` aby otrzymać Różdżkę Architekta
2. Celuj w miejsce gdzie chcesz stworzyć obiekt
3. Kliknij **PPM** (prawy przycisk myszy) w powietrze
4. Wybierz typ obiektu w menu:
   - **Block Display** - dowolny blok
   - **Item Display** - dowolny przedmiot (wspiera CMD!)
   - **Text Display** - tekst z formatowaniem

### Edytowanie obiektów
1. Celuj różdżką w istniejący obiekt Display
2. Kliknij **PPM** aby otworzyć menu edycji
3. Opcje:
   - **Transformacje** - przesuwaj, obracaj, skaluj
   - **Animacje** - dodaj animacje
   - **Właściwości** - zmień szczegóły obiektu
   - **Gizmo** - włącz wizualny edytor

### Używanie Gizmo
1. W menu edycji kliknij "Przełącz Gizmo"
2. Zobaczysz kolorowe strzałki/okręgi wokół obiektu
3. Używaj **LPM** (lewy przycisk) aby przełączać tryby
4. Tryby:
   - 🔴🟢🔵 Strzałki = Przesuwanie
   - ⭕ Okręgi = Obracanie
   - 🔶 Kostki = Skalowanie

### Usuwanie obiektów
- Celuj w obiekt + **Shift + PPM** = Usuń

---

## ⚙️ Konfiguracja

Plik `config.yml` pozwala dostosować:

```yaml
# Język (pl/en)
language: pl

# Ustawienia różdżki
wand:
  material: BLAZE_ROD

# Ustawienia Gizmo
gizmo:
  particle-density: 20
  size: 1.5
  update-interval: 2

# Ustawienia edytora
editor:
  max-distance: 50
  move-sensitivity: 0.1
  rotate-sensitivity: 5.0
  scale-sensitivity: 0.05

# Animacje
animations:
  tick-rate: 2
```

---

## 💾 Przechowywanie danych

- Wszystkie stworzone Display Entities są zapisywane w `displays.yml`
- Automatyczny zapis co 5 minut (konfigurowalne)
- Zapis przy wyłączeniu serwera
- Animacje są przywracane po restarcie

## 🛠️ Wymagania

- **Paper** (lub kompatybilny fork)
- **Java 21** lub nowsza

---

## 🤝 Issues / Problemy

Issue reporting and contact: https://nariaris.com/VisualWand

Zgłaszanie błędów oraz kontakt: https://nariaris.com/VisualWand

## 📄 License / Licencja

MIT License

---

**Created with ❤️ by Nariaris**
