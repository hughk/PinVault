# Artist Fingerpaint Mode

**Artist Fingerpaint Mode** allows users to define custom, non-linear geometric paths across the matrix grid by physically dragging a finger over the screen.

---

## 🎨 Why Fingerpaint Mode?

Straight horizontal, vertical, or diagonal lines are simple to remember, but non-linear shapes provide superior resistance against shoulder-surfing:
- **L-Shapes & Corners**: Down 2 cells, Right 2 cells.
- **Knight's Moves / Zigzags**: Non-adjacent steps across the board.
- **Custom Patterns**: Circles, perimeter sweeps, or distinctive initials.

---

## 👆 How It Works

1. **Input Gestures**:
   - As you drag your finger across the matrix in the **Matrix Editor**, the touch coordinates are continuously mapped to grid cells via hit-testing.
   - Cells are added sequentially to your custom path.
   - Duplicate adjacent cells are automatically ignored to prevent jitter.

2. **Directional Numbered Chevrons**:
   - Each cell in the path displays its sequence index: `#1`, `#2`, `#3`, etc.
   - Small directional indicators point toward the subsequent cell in the trail, making the sequence obvious to you during construction.

3. **Decoy Randomization Integration**:
   - When clicking **"Randomise ? Decoys"**, the system preserves every cell along your painted path exactly as entered.
   - Only unassigned decoy cells displaying a `?` token are randomized.

---

## 💡 Best Practices for Artist Paths

- **Keep It Memorable**: Choose an intuitive geometric shape (e.g., a "Z" or a corner turn) that you can recall without checking the hint.
- **Use Consistent Colors**: Assign high-contrast secret colors (*Rose*, *Emerald*, or *Cyan*) that stand out cleanly in OLED Dark Mode.
- **Biometric Backup**: You can record an optional natural-language rule hint (e.g., *"Top-left corner down 2, right 2"*) sealed inside the hardware KeyStore, revealable only via biometric authentication.
