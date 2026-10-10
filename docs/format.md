# Format `.dwp`, version 1

> *English summary: `.dwp` is an open, ZIP-based package for depth (parallax) wallpapers: one image, its disparity map as a 16-bit grayscale PNG, and a JSON manifest carrying attribution and suggested settings. This specification is dedicated to the public domain (CC0 1.0): any app may read and write it.*

- **Version** : 1, fixée le 4 octobre 2026. Le nom `.dwp` reste provisoire.
- **Licence de ce document** : [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/deed.fr). Toute app peut implémenter le format, sans condition.
- **Implémentation de référence** : module [`:format`](../format) de GlimmerView (Kotlin, sans dépendance hors du JDK et d'`org.json`).

## Vue d'ensemble

Un paquet `.dwp` est une archive ZIP ordinaire (méthodes *stored* ou *deflate*) :

```
oeuvre.dwp
├── manifest.json     obligatoire : fiche de l'œuvre et réglages conseillés
├── color.webp        image principale (WebP conseillé ; PNG ou JPEG acceptés)
├── depth.png         carte de disparité, niveaux de gris 16 bits
└── thumbnail.webp    facultatif : vignette 360 × 800
```

- Extension : `.dwp`. Type MIME : `application/x-dwp+zip` (non enregistré auprès de l'IANA).
- Les noms ci-dessus sont ceux qu'écrit GlimmerView. Un lecteur suit les noms annoncés par le manifest.
- `manifest.json` vient de préférence en premier, ce qui permet de lire la fiche sans parcourir toute l'archive.
- Les images, déjà compressées, sont de préférence stockées sans recompression (*stored*).

## `manifest.json`

Un objet JSON encodé en UTF-8. Seul `format` est obligatoire.

| Champ | Type | Rôle |
|---|---|---|
| `format` | `"dwp"` | **Obligatoire.** Identifie le format. |
| `formatVersion` | entier | Version du format (1 si absent). Un lecteur refuse une version plus récente que celle qu'il connaît. |
| `id` | texte | Identifiant stable de l'œuvre, choisi par son producteur. |
| `title` | texte | Titre. |
| `author` | objet `{ "name", "url" }` ou texte | Auteur. L'attribution est obligatoire pour les licences CC BY. |
| `license` | texte | Identifiant [SPDX](https://spdx.org/licenses/) : `CC0-1.0`, `CC-BY-4.0`, `CC-BY-SA-4.0`… ou `public-domain` pour une œuvre du domaine public sans licence (photos de la NASA, par exemple). Absent : tous droits réservés. |
| `sourceUrl` | texte | Page d'origine de l'image. |
| `aiGenerated` | booléen | Image produite par une IA générative. Absent : inconnu. |
| `medium` | texte | Technique, comme sur un cartel de musée (« Photographie »). |
| `language` | texte | Langue de `title` et de `medium` (étiquette BCP 47 : `fr-FR`). |
| `created` | texte | Date de création, ISO 8601 (`2026-10-04T08:00:00Z`). |
| `generator` | objet | Outils utilisés : `app`, `depthModel`, `inpaintModel` (textes libres). |
| `width`, `height` | entiers | Dimensions de l'image principale, en pixels. |
| `layers` | tableau | Calques, du fond au premier plan (voir plus bas). Absent : un seul calque `color.webp` + `depth.png`. |
| `defaults` | objet | Réglages conseillés (voir plus bas). |
| `thumbnail` | texte | Nom de la vignette dans l'archive. |
| `tags` | tableau de textes | Mots-clés (`nature`, `space`, `cities`, `art`…). |

### Calques

Chaque entrée de `layers` est un objet :

- `color` : nom de l'image du calque ;
- `depth` : nom de sa carte de disparité ;
- `mask` (facultatif) : PNG en niveaux de gris, ou canal alpha, indiquant la zone couverte par le calque.

Le **dernier calque est l'image principale** : le premier plan, ou l'image entière s'il n'y a qu'un calque. Un lecteur qui ne gère qu'un calque utilise celui-là et ignore les autres. Les calques d'arrière-plan (zones masquées reconstruites par *inpainting*) sont prévus pour une version ultérieure de GlimmerView ; ils ne demandent pas de changement de version du format.

### Réglages conseillés (`defaults`)

| Champ | Rôle |
|---|---|
| `strength` | Décalage maximal du premier plan, en fraction de la largeur visible. Repères : 0,012 discret, 0,035 moyen, 0,07 intense. |
| `focus` | Disparité (0 = fond, 1 = premier plan) du plan qui reste immobile. |
| `zoom` | Agrandissement minimal (≥ 1). Un lecteur peut l'augmenter pour ne jamais montrer les bords. |
| `motion` | Mouvement conseillé : `gyro` (inclinaison du téléphone), `touch` ou `auto`. |

Chaque lecteur adapte ces valeurs à son moteur de rendu ; elles ne sont que des suggestions.

### Exemple

```json
{
  "format": "dwp",
  "formatVersion": 1,
  "id": "glimmerview-dawn",
  "title": "Crêtes à l'aube",
  "author": { "name": "GlimmerView Collective" },
  "license": "CC0-1.0",
  "aiGenerated": false,
  "medium": "Illustration numérique, 5 plans",
  "language": "fr-FR",
  "generator": { "app": "GlimmerView 0.1.0" },
  "width": 1080,
  "height": 2400,
  "layers": [{ "color": "color.webp", "depth": "depth.png" }],
  "defaults": { "strength": 0.035, "focus": 0.5, "zoom": 1, "motion": "gyro" },
  "thumbnail": "thumbnail.webp",
  "tags": ["nature"]
}
```

## Carte de disparité

- PNG non entrelacé, en niveaux de gris **16 bits** (recommandé) ou 8 bits. Un PNG RVB ou RVBA est lu sur son premier canal.
- Valeurs de **disparité** : 0 = le plus loin, 65 535 (ou 255) = le plus proche. Ce n'est pas une distance métrique : seul l'ordre des plans et leur écart relatif comptent.
- Même cadrage que l'image du calque. La résolution est libre (souvent plus petite que l'image) : le lecteur l'étire sur l'image.
- Pixels rangés du haut vers le bas, dans le même sens que l'image.

## Image principale et vignette

- WebP conseillé, PNG ou JPEG acceptés. Les pixels sont stockés « à l'endroit » : une éventuelle orientation EXIF est ignorée.
- Taille conseillée : jusqu'à 1440 × 3200 pixels, le format d'un écran de téléphone. GlimmerView ramène toute image à 2400 pixels de grand côté.
- Vignette : WebP de 360 × 800 pixels, l'œuvre recadrée au centre au format d'un écran.

## Règles de lecture

- Les champs inconnus du manifest et les fichiers inconnus de l'archive sont ignorés.
- Un paquet dont `formatVersion` dépasse la version connue est refusé.
- Les noms de fichiers servent seulement à retrouver les entrées de l'archive ; ils ne sont jamais utilisés comme chemins sur le disque.
- Une entrée en double est une erreur.
- Les tailles se vérifient **pendant** la décompression : celles qu'annonce l'archive ne sont pas fiables (« zip bombs »).

Garde-fous de GlimmerView, à titre indicatif :

| Limite | Valeur |
|---|---|
| Archive (taille compressée) | 128 Mo |
| Fichiers dans l'archive | 64 |
| Un fichier, une fois décompressé | 48 Mo |
| Toute l'archive, une fois décompressée | 128 Mo |
| Carte de disparité | 8192 pixels de côté, 12 millions de pixels |
| Image principale | 16 384 pixels de côté, 100 millions de pixels |

## Ce qu'écrit GlimmerView

- Un seul calque : `color.webp`, `depth.png` (16 bits) et `thumbnail.webp`. L'image d'une œuvre de la collection part telle qu'elle est stockée, sans réencodage ; à l'import, une image WebP qui n'a pas besoin d'être réduite est gardée telle quelle.
- `defaults` reprend les réglages de l'œuvre au moment de l'export.
- Œuvres de la galerie : `author` « GlimmerView Collective », `license` `CC0-1.0`, `aiGenerated` `false`.
- Créations personnelles : ni `author` ni `license` (tous droits réservés), `generator.depthModel` `depth-anything-v2-small`.
- Œuvre importée puis réexportée : le manifest d'origine est conservé (auteur, licence, source), seuls le titre, les dimensions et les réglages sont mis à jour.

## Évolution du format

- Ajouter un champ facultatif ne change pas la version : les lecteurs ignorent ce qu'ils ne connaissent pas.
- Une modification que les lecteurs actuels interpréteraient mal (changer le sens d'un champ, rendre un champ obligatoire, changer l'encodage de la profondeur) fait passer `formatVersion` à 2.
