# Character avatars

48 curated Disney characters in Disney favorites, Princesses, Animal friends,
and Villains. Character names and original artwork sources are recorded in
`roku/data/character-avatars.json`, based on https://disneyapi.dev/docs/ and the
linked Disney Wiki character pages. Disney character artwork belongs to its
respective rights holders; these images are not part of the CC0 DiceBear catalog.

The bundled thumbnails explicitly request PNG because the CDN otherwise serves
WebP even for a .png URL. Roku uses the bundled thumbnails for instant browsing
and the exact corresponding PNG URL for persisted profile images.

Selections are stable, 1-based positions within each category. Do not reorder
published choices; append new entries instead. IDs are persisted in avatar_seed.
