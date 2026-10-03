# GlimmerView

> *Subtle shifts, stunning screens.*

**GlimmerView** est une application Android libre et gratuite qui transforme n'importe quelle image en fond d'écran 3D : une carte de profondeur est générée sur le téléphone, puis l'image réagit aux mouvements de l'appareil (effet de parallaxe).

*GlimmerView is a free and open-source Android app that turns any picture into a 3D parallax live wallpaper, with on-device depth estimation.*

- Sans pub, sans traceur, sans compte
- Traitement local : les photos ne quittent pas le téléphone
- Licence [GPL-3.0](LICENSE)

## État du projet

Phase 0, validation technique (voir [PLAN.md](PLAN.md)). L'app contient pour l'instant un écran « Lab » qui :

1. compare l'estimation de profondeur (Depth Anything V2 Small) avec **LiteRT** (GPU, CPU, NPU) et **ONNX Runtime** (CPU, XNNPACK) sur ton téléphone ;
2. affiche un aperçu 3D interactif avec le moteur de rendu OpenGL ES 3 (ray-marching) et mesure son coût ;
3. applique le résultat comme fond d'écran animé et relève ses statistiques (images dessinées, temps de rendu).

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
| `app` | Interface (Jetpack Compose), écran Lab |
| `core` | Modèles de données, stockage du fond d'écran actif |
| `render` | Moteur de rendu OpenGL ES 3, capteurs, aperçu |
| `ml` | Estimation de profondeur (LiteRT, ONNX Runtime), post-traitement, benchmarks |
| `wallpaper` | Service de fond d'écran animé (process séparé) |

## Modèles d'IA

Téléchargés à la demande depuis l'app, jamais versionnés dans Git :

- Depth Anything V2 Small (Apache-2.0) : export TFLite de Qualcomm AI Hub, export ONNX de onnx-community.

## Licence

GlimmerView est distribué sous licence GNU GPL v3.0. Voir [LICENSE](LICENSE).
