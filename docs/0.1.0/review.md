# 0.1.0 beta review notes

## Resource safety

- Partial overflow refunds retain only the uninserted remainder.
- Every partial fluid transfer marks the device dirty, including while ME stays full.
- Cancelled, unsubmitted fluid inputs stay in fluid custody, not item GUI slots.
- Player deposits reject fluid recipe samples: a description is not a fluid container.
- Breaking an interface stores buffered fluids by owner and block position in level saved data.
  Replace an interface at the **same position, with the same owner** to recover those fluids.
  Fluids still held by a cancelled Singulo escrow remain there until such replacement.
  Item refunds retain the existing item-drop behavior. No fluid sample item is dropped.
- A replay of an accepted request discards the stale local offer copy rather than refunding it twice.

Minecraft saved data and block-entity saves are not a transactional database. These protections cover
normal transfers, partial capacity, reload and device removal; they do not promise atomic recovery
from a process or disk failure between unrelated save files.

## Icon

`src/main/resources/applied-singulo.png` is the code-drawn Orbital Interface candidate (512px RGBA).
It combines a gold orbit (Singulo/Arsenal family) and cyan network nodes with a central interface.
No AI image generation or copied AE2 logo was used. Packaging checks verify exact inclusion.

## Pattern and stock review

- Recipe refresh compares complete AE2 definition keys, not their display strings.
- Duplicate stock slots use the largest target for a key, not independent jobs.
- Before submitting a calculated plan, current grid, inventory and outstanding requests are checked again.
- Stale calculations are cancelled after config changes, unload or deficit changes.
