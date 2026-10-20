# Appearance And Monet

Updated: 2026-10-19

Keep themeModeIndex in the range 0..2 and persist monetEnabled independently. Preferences decoding maps legacy modes 3..5 to 0..2 and infers Monet only when the new preference is absent. Every settings update writes both normalized fields atomically.

PulseTheme resolves the Miuix Monet variant from appearance and the enabled flag. The settings screen gates the switch and palette controls using the active skin. The stable theme content entry preserves navigation during changes.

Verify all six legacy modes, explicit switch values, missing preferences and invalid indices with JVM unit tests. Run debug compilation, the unit suite and release assembly sequentially. Confirm visibility and appearance on a device. Text sizes retain the existing shared Miuix text style mapping.
