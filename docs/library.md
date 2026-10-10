# Bibliothèque de la galerie

La galerie présente des œuvres livrées sous forme de paquets [`.dwp`](format.md). Pour garder l'APK
léger, la plupart sont téléchargées à la demande depuis la **bibliothèque**, un simple site statique
servi par GitHub Pages depuis la branche `gh-pages` :

```
https://cocoke.github.io/GlimmerView/
├── index.json                 la liste des œuvres (ce document en décrit le format)
├── previews/<id>.webp         aperçu 540 × 1200 de chaque œuvre (cadres de la galerie)
└── works/<id>.dwp             le paquet complet (image, profondeur, réglages)
```

## Dans l'APK et sur le téléphone

- L'APK embarque une copie de `index.json`, **tous les aperçus** (la galerie s'affiche entière, même
  hors ligne) et quelques paquets marqués `bundled` dans `tools/pack/catalog.json`.
- Au plus une fois toutes les douze heures, l'app télécharge `index.json`. Si sa `revision` dépasse celle
  qu'elle connaît, les nouvelles œuvres apparaissent dans la galerie et leurs aperçus sont téléchargés.
- En ouvrant une œuvre absente du téléphone, l'app télécharge son paquet (progression affichée),
  vérifie sa taille et son empreinte SHA-256, puis le garde dans son stockage privé.
  Les Préférences indiquent la place occupée et permettent de la libérer.
- Une œuvre ajoutée à la collection ou accrochée est copiée dans la collection : libérer la
  bibliothèque ne la supprime pas.

## `index.json`

```json
{
  "format": "glimmerview-library",
  "version": 1,
  "revision": 1,
  "works": [
    {
      "id": "commons-lac-des-plagnes",
      "room": "water",
      "title": { "fr": "Lac des Plagnes", "en": "Lac des Plagnes" },
      "author": "Krzysztof Golik",
      "medium": { "fr": "Photographie numérique", "en": "Digital photograph" },
      "year": "2020",
      "circa": false,
      "detail": false,
      "license": "CC-BY-SA-4.0",
      "sourceUrl": "https://commons.wikimedia.org/wiki/File:Lac_des_Plagnes_07.jpg",
      "focus": 0.2195,
      "package": { "path": "works/commons-lac-des-plagnes.dwp", "size": 940123, "sha256": "…" },
      "preview": "previews/commons-lac-des-plagnes.webp"
    }
  ]
}
```

| Champ | Rôle |
|---|---|
| `format`, `version` | `glimmerview-library`, version 1. L'app refuse un index d'une version plus récente. |
| `revision` | Entier croissant : à augmenter à chaque publication, sinon les téléphones ne voient pas les changements. |
| `works` | Les œuvres, **dans l'ordre de la galerie** (la première d'une salle y est « à l'affiche »). |
| `id` | Identifiant stable, `[a-z0-9-]`, 48 caractères au plus. |
| `room` | Salle : `mountains`, `water`, `nature`, `space`, `cities` ou `art`. |
| `title`, `medium` | Textes par langue (`fr`, `en`…) ; l'app prend la langue du téléphone, sinon l'anglais. |
| `author`, `year`, `circa`, `detail` | Cartel : auteur, date (« vers » si `circa`), « (détail) » pour une œuvre recadrée. |
| `license` | Identifiant SPDX (`CC0-1.0`, `CC-BY-SA-4.0`…) ou `public-domain`. |
| `sourceUrl` | Page d'origine de l'image (attribution). |
| `focus` | Plan immobile conseillé (disparité 0..1), utilisé avant même le téléchargement du paquet. |
| `package` | Chemin, taille en octets (64 Mo au plus) et empreinte SHA-256 du paquet. |
| `preview` | Chemin de l'aperçu WebP. |

Une œuvre mal décrite (identifiant ou empreinte invalide, chemin qui remonte avec `..`, salle inconnue)
est ignorée ; les autres restent affichées.

## Publier une nouvelle version

1. Ajouter ou modifier les œuvres dans `tools/pack/catalog.json`, augmenter `revision`.
2. `python tools/pack/build_pack.py build` : la bibliothèque est écrite dans `tools/pack/out/library/`
   et la copie embarquée dans `app/src/main/assets/library/`.
3. Copier `tools/pack/out/library/` dans la branche `gh-pages` (avec son fichier `.nojekyll`), commiter
   et pousser : GitHub Pages publie le site en une à deux minutes.
4. Commiter la copie embarquée sur `main` : les nouvelles installations partent de cet index.
