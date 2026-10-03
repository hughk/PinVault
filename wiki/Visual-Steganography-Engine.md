# Visual Steganography Engine

The core innovation of Pin Vault is its **Steganographic Matrix Engine**. Rather than storing or rendering plain numeric PINs, the system camouflages secret digits into an $M \times N$ two-dimensional grid of colored numeric tiles.

---

## 🧩 Matrix Structure

Matrices can be configured from **4×4 up to 8×8** dimensions:
- **Card PINs**: Typically 4 to 6 digits on a 5×5 or 6×6 grid.
- **Banking / Password Codes**: Up to 12 digits on a 7×7 or 8×8 grid.
- **Dynamic TOTP 2FA**: 6 digits on a 6×6 grid.

Each cell in the grid contains:
- A single numeric digit (`0` through `9`).
- A background token color (e.g., *Rose*, *Emerald*, *Amber*, *Cyan*, *Violet*).
- An optional directional indicator (during editing or biometric reveal).

---

## 🎨 Secret Path vs. Decoy Lines

To prevent statistical analysis and visual pattern detection by observers:

### 1. Secret Path
The user designates a specific geometric path and secret color:
- **Linear**: Straight line (horizontal row, vertical column, or diagonal).
- **Geometric / Artist**: Freeform path (L-shape, zigzag, knight's move, or custom perimeter walk).
- **Secret Digits**: Placed sequentially along this path.

### 2. Linear Decoy Generation (`DecoyRandomizer.kt`)
If only the secret PIN path possessed identical colors, onlookers would instantly notice the colored line. Pin Vault's decoy generation engine prevents this:
- Synthesizes multiple **false trails** (horizontal, vertical, diagonal) of identical colors across the matrix.
- The secret color is also used for false decoy paths across unoccupied cells, ensuring the secret color is not statistically unique in the grid.
- Inactive cells are populated with uniform pseudo-random distributions of numbers and colors.

```
Example 6x6 Matrix (Secret PIN = 1-2-3-4 in Rose on diagonal):
┌───┬───┬───┬───┬───┬───┐
│ 1*│ 8 │ 4 │ 2 │ 7 │ 0 │  (* = Rose tile: Secret Path)
├───┼───┼───┼───┼───┼───┤
│ 9 │ 2*│ 3 │ 1 │ 4 │ 8 │  (Other cells: Emerald, Cyan,
├───┼───┼───┼───┼───┼───┤   Amber, and Rose decoys)
│ 5 │ 6 │ 3*│ 9 │ 2 │ 1 │
├───┼───┼───┼───┼───┼───┤
│ 0 │ 4 │ 7 │ 4*│ 5 │ 3 │
├───┼───┼───┼───┼───┼───┤
│ 8 │ 1 │ 0 │ 3 │ 6 │ 9 │
├───┼───┼───┼───┼───┼───┤
│ 2 │ 5 │ 8 │ 1 │ 4 │ 7 │
└───┴───┴───┴───┴───┴───┘
```

---

## 👁️ Shoulder-Surfing Defense in Practice

When you glance at your phone screen:
1. You identify your designated starting point and geometric direction.
2. You read your 4–6 digits in under 2 seconds.
3. An observer sees a busy, vibrant matrix with dozens of digits and multiple colored trails, unable to discern which line is real.
