# Pack de démarrage de la galerie

Douze œuvres du domaine public livrées avec l'app, une par paquet `.dwp` dans
`app/src/main/assets/gallery/`. Elles s'ajoutent aux sept scènes dessinées du Collectif GlimmerView.

## Reconstruire le pack

```bash
python -m pip install --user onnxruntime numpy pillow opencv-python
python tools/pack/build_pack.py crops   # planche des cadrages : tools/pack/out/crops.jpg
python tools/pack/build_pack.py build   # paquets + planche image/profondeur : tools/pack/out/pack.jpg
```

Le script télécharge les images sources (`tools/pack/sources/`, non versionné) et le modèle
Depth Anything V2 Small FP32 (99 Mo, Apache-2.0, empreinte SHA-256 vérifiée), puis, pour chaque œuvre :

1. recadre l'image au format d'écran 9:20, sans jamais l'agrandir ;
2. calcule la profondeur avec le modèle de l'app, en 518 × 1148 (518 × 518 sur le téléphone) ;
3. applique le même post-traitement que l'app : percentiles 1–99 %, filtre guidé par l'image,
   dilatation du premier plan ;
4. écrit le paquet selon [docs/format.md](../../docs/format.md) : image WebP (1080 × 2400 au plus),
   disparité en PNG 16 bits (540 × 1200), vignette 360 × 800, plan immobile conseillé.

`catalog.json` décrit chaque œuvre : source, auteur, date, technique, licence et cadrage. Les titres et
techniques affichés dans l'app, en français et en anglais, sont dans les ressources (`exhibit_*`,
`medium_*`), et le catalogue côté app dans `app/.../data/Gallery.kt`.

## Œuvres et droits

| Œuvre | Auteur | Source | Droits |
|---|---|---|---|
| *Under the Wave off Kanagawa (The Great Wave)*, vers 1830–1832 | Katsushika Hokusai | [Met 39799](https://www.metmuseum.org/art/collection/search/39799) | CC0 (Met Open Access) |
| *Earthset from Orion*, 2026 | NASA, équipage d'Artemis II | [art002e021278](https://images.nasa.gov/details/art002e021278) | Domaine public |
| *Cypresses*, 1889 | Vincent van Gogh | [Met 437980](https://www.metmuseum.org/art/collection/search/437980) | CC0 (Met Open Access) |
| *The Boulevard Montmartre on a Winter Morning*, 1897 | Camille Pissarro | [Met 437310](https://www.metmuseum.org/art/collection/search/437310) | CC0 (Met Open Access) |
| *Asakusa Ricefields and Torinomachi Festival*, 1857 | Utagawa Hiroshige | [Met 36539](https://www.metmuseum.org/art/collection/search/36539) | CC0 (Met Open Access) |
| *Moonlight on the Solar Arrays*, 2025 | NASA, équipage de l'ISS | [iss073e0039722](https://images.nasa.gov/details/iss073e0039722) | Domaine public |
| *Maples at Mama*, 1857 | Utagawa Hiroshige | [Met 37096](https://www.metmuseum.org/art/collection/search/37096) | CC0 (Met Open Access) |
| *Piazza San Marco*, fin des années 1720 | Canaletto | [Met 435839](https://www.metmuseum.org/art/collection/search/435839) | CC0 (Met Open Access) |
| *Jūmantsubo Plain at Fukagawa Susaki*, 1856 | Utagawa Hiroshige | [Met 55733](https://www.metmuseum.org/art/collection/search/55733) | CC0 (Met Open Access) |
| *Two Men Contemplating the Moon*, vers 1825–1830 | Caspar David Friedrich | [Met 438417](https://www.metmuseum.org/art/collection/search/438417) | CC0 (Met Open Access) |
| *Sudden Shower over Shin-Ōhashi Bridge and Atake*, 1857 | Utagawa Hiroshige | [Met 55433](https://www.metmuseum.org/art/collection/search/55433) | CC0 (Met Open Access) |
| *View from Mount Holyoke… (The Oxbow)*, 1836 | Thomas Cole | [Met 10497](https://www.metmuseum.org/art/collection/search/10497) | CC0 (Met Open Access) |

Les images de la NASA ne sont pas protégées par le droit d'auteur. La NASA demande d'être citée et
interdit de laisser croire à son soutien : l'app la cite comme autrice, sans logo ni mention de partenariat.
Les œuvres d'art recadrées portent la mention « détail » sur leur cartel.
