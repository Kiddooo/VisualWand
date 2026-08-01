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
- Keep the wand selected and hold **Shift** while scrolling to cycle every line-of-sight display
  in front of you within the configured range (8 blocks by default), regardless of client FOV
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
Cycling first exits any current editor selection, then moves through every line-of-sight display in
front of you within the configured range (8 blocks by default), regardless of the client's FOV
setting. Press **RMB** to open the highlighted display.

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

# VisualWand (Polski / Polish)

**Edytor Display Entity w grze dla Paper**

Plugin typu "In-Game Editor" pozwalający graczom i administratorom tworzyć, edytować i animować obiekty Display Entities (Block Display, Item Display, Text Display) bez wpisywania skomplikowanych komend.

---

## ✨ Główne Funkcjonalności

### 🎯 Edycja "Wskaż i Kliknij" (Ray-tracing)
- Nie musisz wpisywać komend
- Trzymaj **Różdżkę Architekta** i kliknij PPM w powietrze, aby stworzyć obiekt
- PPM na istniejącym obiekcie wybiera go i otwiera menu edycji
- Wybierz tryb transformacji, potem używaj **LPM, aby zwiększać**, i **PPM, aby zmniejszać**
- Naciśnij **wyrzucenie przedmiotu**, trzymając różdżkę, aby ponownie otworzyć menu trybów
- Trzymając wybraną różdżkę, przytrzymaj **Shift** i przewijaj, aby przełączać wszystkie obiekty
  Display przed graczem, które są w zasięgu wzroku i skonfigurowanym promieniu (domyślnie
  8 bloków), niezależnie od ustawienia FOV klienta
- Rozpoczęcie przełączania kończy bieżący wybór edytora; **PPM** otwiera podświetlony obiekt

![Tool](https://cdn.modrinth.com/data/cached_images/0b766122818bd6540b28292eb473b86ef405b923.png)

### 🔧 Edycja kliknięciami i Gizmo transformacji
- Wybierz jeden z dokładnie 15 trybów: przesuwanie XYZ, lewa rotacja XYZ, prawa rotacja XYZ,
  skala XYZ, skala równomierna, obrót obiektu i pochylenie obiektu
- Presety Dokładny, Normalny i Zgrubny pozostają wybrane po zmianie obiektu
- Cząsteczki pokazują strzałki, okręgi lub uchwyty skali dla aktywnego trybu
- **Czerwona oś = X**, **Zielona oś = Y**, **Niebieska oś = Z**
- Gizmo jest tylko podglądem; edycja używa zwykłych kliknięć bez moda klienta

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
1. Celuj różdżką w istniejący Block, Item lub Text Display
2. Kliknij **PPM**, aby wybrać obiekt i otworzyć menu edycji
3. Otwórz **Transformacje** i wybierz jeden z 15 trybów; menu zamknie się automatycznie
4. Używaj zwykłego **LPM, aby zwiększać**, i **PPM, aby zmniejszać** wybraną wartość
5. Przytrzymane kliknięcia powtarzają się tylko z ograniczoną częstotliwością
6. Naciśnij **wyrzucenie przedmiotu**, trzymając różdżkę, aby wrócić do menu transformacji
7. Menu zawiera presety, cofanie/ponawianie, kopiowanie transformacji, resety,
   anulowanie trybu i odznaczenie obiektu

Aby wybrać jeden z pobliskich obiektów Display, trzymaj wybraną różdżkę, przytrzymaj **Shift**
i przewijaj. Przełączanie najpierw kończy bieżący wybór edytora, a następnie przechodzi przez
wszystkie obiekty przed graczem, które są w zasięgu wzroku i skonfigurowanym promieniu
(domyślnie 8 bloków), niezależnie od FOV klienta. Naciśnij **PPM**, aby otworzyć podświetlony obiekt.

Animacje i właściwości danego typu obiektu pozostają dostępne w menu edycji.

### Usuwanie obiektów
- Wybierz obiekt, a następnie użyj opcji **Usuń (TNT)** w menu edycji.

---

## ⚙️ Konfiguracja

Plik `config.yml` pozwala dostosować:

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
