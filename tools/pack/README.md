# Pack de démarrage de la galerie

Trente et une œuvres livrées avec l'app : douze œuvres d'art du domaine public et dix-neuf photos de paysages, de villes et d'espace, une par paquet `.dwp` dans
`app/src/main/assets/gallery/`. Elles s'ajoutent aux sept scènes dessinées du Collectif GlimmerView.

Le pack pèse 36,8 Mo, rangé sans recompression dans l'APK.

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

### Œuvres d'art et photos de la NASA

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

### Photos de Wikimedia Commons

Photos choisies parmi les « images de qualité » de Commons, auteur et licence vérifiés sur chaque page. Elles ont été **recadrées** au format d'écran et accompagnées d'une **carte de profondeur calculée** : ces paquets dérivés restent sous la licence de la photo (CC BY-SA : même licence, auteur cité). L'app affiche l'auteur et la licence sur le cartel de chaque œuvre.

| Photo | Auteur | Source | Licence |
|---|---|---|---|
| *Machu Picchu*, 2009 | Martin St-Amant | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:80_-_Machu_Picchu_-_Juin_2009_-_edit.jpg) | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/deed.fr) |
| *Seceda above the clouds, Dolomites*, 2016 | Wolfgang Moroder | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Odles_da_Seceda_da_duman_tl_nibl_Gherd%C3%ABina.jpg) | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/deed.fr) |
| *Lac des Plagnes, French Alps*, 2020 | Krzysztof Golik | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Lac_des_Plagnes_07.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Lake Gosaikunda, Nepal*, 2014 | Sergey Pesterev | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Lake_Gosaikunda.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Pier on Lake Aasee, Münster*, 2020 | Dietmar Rabich | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:M%C3%BCnster,_Aasee,_Pier_--_2020_--_9215-7.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Nosy Iranja, Madagascar*, 2025 | Diego Delso | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Nosy_Iranja,_Madagascar,_2025-09-17,_DD_21.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *The Twelve Apostles, Port Campbell National Park*, 2019 | Dietmar Rabich | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Princetown_(AU),_Port_Campbell_National_Park,_Twelve_Apostles_--_2019_--_0930.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Snowstorm near Ogoy Island, Lake Baikal*, 2018 | Sergey Pesterev | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Snowstorm_near_Ogoy_island.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Laurel forest of Fanal, Madeira*, 2025 | Dietmar Rabich | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Fanal_(Madeira,_Portugal),_Lorbeerwald_--_2025_--_1546.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Forest stairs on Mount Hallasan, Jeju*, 2024 | Basile Morin | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Wooden_staircase_steps_in_the_forest_of_Hallasan_Park_Eorimok_Trail_at_dusk_on_Jeju_Island_in_South_Korea.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Seljalandsfoss, Iceland*, 2014 | Diego Delso | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Seljalandsfoss,_Su%C3%B0urland,_Islandia,_2014-08-16,_DD_189-191_HDR.JPG) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Multnomah Falls, Oregon*, 2019 | King of Hearts | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Multnomah_Falls_October_2019_stack.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Frosted path near Dülmen*, 2021 | Dietmar Rabich | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:D%C3%BClmen,_Kirchspiel,_Dernekamp,_Wirtschaftsweg_--_2021_--_9407.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Deadvlei, Namibia*, 2018 | Diego Delso | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Dead_Vlei,_Sossusvlei,_Namibia,_2018-08-06,_DD_086.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Eisriesenwelt cave, Tennen Mountains*, 2019 | Diego Delso | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Eisriesenwelt,_Macizos_de_Tennen,_Austria,_2019-05-18,_DD_58.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Empire State Building, New York*, 2008 | Daniel Schwen | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Empire_State_Building_pano.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Berlin TV Tower at night*, 2016 | Diego Delso | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Fernsehturm,_Berl%C3%ADn,_Alemania,_2016-04-21,_DD_40-42_HDR.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Rio de Janeiro at sunrise*, 2018 | Donatas Dabravolskas | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Contrasts_of_Rio_de_Janeiro_-_Rocinha,_Ipanema,_and_Mountains_at_Sunrise.jpg) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.fr) |
| *Pillars of Creation (NIRCam)*, 2022 | NASA, ESA, CSA, STScI | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Pillars_of_Creation_(NIRCam_Image).jpg) | Domaine public |

Les images de la NASA ne sont pas protégées par le droit d'auteur. La NASA demande d'être citée et
interdit de laisser croire à son soutien : l'app la cite comme autrice, sans logo ni mention de partenariat.
Les œuvres d'art recadrées portent la mention « détail » sur leur cartel.
