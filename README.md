# GlimmerView

[![Build](https://github.com/cocoke/GlimmerView/actions/workflows/build.yml/badge.svg)](https://github.com/cocoke/GlimmerView/actions/workflows/build.yml)

> *Subtle shifts, stunning screens.*

**GlimmerView** est une application Android libre et gratuite qui transforme n'importe quelle image en fond d'écran 3D : une carte de profondeur est générée sur le téléphone, puis l'image réagit aux mouvements de l'appareil (effet de parallaxe).

*GlimmerView is a free and open-source Android app that turns any picture into a 3D parallax live wallpaper, with on-device depth estimation.*

- Sans pub, sans traceur, sans compte
- Traitement local : les photos ne quittent pas le téléphone
- Licence [GPL-3.0](LICENSE)

## État du projet

Phase 1 en cours, vers la v0.1 (voir [PLAN.md](PLAN.md)). L'interface suit la charte « Galerie » : murs plâtre, encre, un seul accent bleu de Prusse, titres en Spectral et cartels en Hanken Grotesk.

- **Galerie** : sept œuvres dessinées en plans (domaine public, CC0), présentées dans des cadres qui basculent avec le téléphone.
- **Œuvre** : aperçu 3D en direct (même moteur OpenGL ES 3 que le fond d'écran) et vue « cyanotype » de la carte de profondeur.
- **Atelier et tirage** : une photo choisie avec le sélecteur système devient une œuvre. La profondeur est calculée sur le téléphone par Depth Anything V2 Small (ONNX Runtime, modèle de 27 Mo téléchargé une seule fois et vérifié par SHA-256).
- **Partage** : depuis la galerie photo, le navigateur ou une messagerie, « Partager → GlimmerView » envoie l'image directement au tirage.
- **Format .dwp** : une œuvre s'exporte dans un fichier ouvert qui réunit l'image, sa profondeur et ses réglages ([spécification](docs/format.md), domaine public). On le partage ou on l'enregistre ; un .dwp reçu s'ouvre depuis l'atelier, avec « Ouvrir avec » ou par le menu « Partager ».
- **Mise au point** : un diaphragme règle l'intensité (de f/16 à f/4) et une bague de mise au point choisit le plan immobile. On peut aussi toucher le sujet directement sur l'œuvre.
- **Accrochage** : « maintenir pour accrocher » sur l'accueil (3D), le verrouillage (image fixe) ou les deux.
- **Collection et préférences** : œuvres créées et acquises ; recentrage, réduction des mouvements, cadence de 30 ou 60 i/s, pause en économie d'énergie.
- **Laboratoire** : l'écran de mesures de la Phase 0 reste accessible depuis les préférences.

Langues : français et anglais.

## Compiler

Prérequis : JDK 17+, SDK Android (plateforme 37), Android Studio récent (compatible AGP 9.4).

```bash
./gradlew :app:assembleDebug
```

Installer sur un téléphone branché (débogage USB activé) :

```bash
./gradlew :app:installDebug
```

## Structure

| Module | Rôle |
|---|---|
| `app` | Interface (Jetpack Compose) : thème « Galerie », écrans, œuvres dessinées, laboratoire |
| `core` | Modèles de données, collection d'œuvres, fond d'écran actif et préférences partagés avec le fond d'écran |
| `render` | Moteur de rendu OpenGL ES 3, capteurs, aperçu |
| `ml` | Estimation de profondeur (ONNX Runtime), post-traitement, benchmarks |
| `wallpaper` | Service de fond d'écran animé (process séparé) |

## Modèles d'IA

Téléchargés à la demande depuis l'app, jamais versionnés dans Git, vérifiés par empreinte SHA-256 :

- Depth Anything V2 Small (Apache-2.0), export ONNX de onnx-community : INT8 (27 Mo, utilisé par défaut) et FP32 (99 Mo, laboratoire).

## Polices

Spectral (Production Type) et Hanken Grotesk, sous licence SIL Open Font License 1.1 ; les licences sont livrées avec l'app (`app/src/main/assets/licenses`).

## Contribuer

Les règles du projet (liberté, vie privée, charte graphique, textes et licences des œuvres) sont dans [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

GlimmerView est distribué sous licence GNU GPL v3.0. Voir [LICENSE](LICENSE).
