# Logo generation

The production logo is the hand-authored vector in `logo.svg` (rendered to `logo_512.png` / `logo_192.png`
and converted to the adaptive launcher icon in `app/src/main/res/drawable/ic_launcher_*.xml`).

## Concept

* **Terminal**: the prompt chevron `>` and a block cursor `▌`.
* **Time / watch**: a thin watch-face ring with four hour indices; the chevron's vertex points at the centre
  like a pair of hands, the cursor sits at 3 o'clock like a crown.
* **Habit**: the chevron's second stroke reads as a check-mark ✓ when glanced at.
* Palette: Dracula background `#282A36`, green→cyan accent gradient `#50FA7B → #8BE9FD`, foreground `#F8F8F2`.

## Prompt (for image models – marketing renders / store listing)

> Minimalist flat app icon, rounded square, dark slate background (#282A36). In the centre a thin muted-blue
> circular watch-face ring with four small tick marks at 12, 3, 6 and 9 o'clock. Inside the ring, a bold
> terminal prompt chevron ">" drawn with rounded strokes in a green-to-cyan gradient (#50FA7B to #8BE9FD),
> its tip pointing at the ring centre like a watch hand, followed by a small solid off-white rectangular
> block cursor at 3 o'clock resembling a watch crown. No text, no gloss, no shadows, vector style, sharp
> edges, generous padding, symmetrical, suitable as an Android adaptive icon.

Negative prompt: text, letters, 3D, bevel, gradient background, photorealistic, clutter.
