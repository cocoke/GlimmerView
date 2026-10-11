# ADR 0001 : runtime d'inférence pour l'estimation de profondeur

- **Statut** : ✅ décidé : **ONNX Runtime**
- **Date** : 3 octobre 2026

## Contexte

GlimmerView estime la profondeur sur le téléphone avec Depth Anything V2 Small (Apache-2.0). Deux runtimes sont candidats.

| Runtime | Points forts | Points faibles |
|---|---|---|
| **LiteRT 2.2** | GPU (OpenCL/OpenGL) et NPU ; modèles déjà convertis (Qualcomm AI Hub, litert-community) | Accélérateur GPU fourni en binaire non libre ; dépendance `com.google.android.play:ai-delivery` non libre |
| **ONNX Runtime 1.30** | 100 % libre (MIT) ; même fichier `.onnx` sur mobile, serveur et CI | Pas de GPU générique sur Android (CPU MLAS ou XNNPACK) |

## Protocole

1. Installer l'app Lab (`./gradlew :app:installDebug`) sur au moins un téléphone, idéalement trois (entrée, milieu et haut de gamme).
2. Télécharger les trois modèles depuis l'écran Lab.
3. Choisir une photo, cocher toutes les configurations, puis lancer 5 inférences par mesure, téléphone froid et branché sur secteur.
4. Ouvrir « Aperçu 3D » sur le meilleur résultat et mesurer le rendu (glFinish) à 0, 8, 16 et 32 pas, en mode « rendu continu ».
5. Appliquer le fond d'écran, utiliser le téléphone 10 minutes, revenir au Lab et noter les statistiques du fond d'écran.
6. Copier le rapport (bouton « Copier ») et le coller ci-dessous.

## Critères de décision

| Critère | Seuil |
|---|---|
| Inférence (moyenne), milieu de gamme | < 3 s, idéalement < 1 s |
| Rendu à 16 pas, milieu de gamme | < 8 ms par image (60 i/s possibles) |
| Fond d'écran immobile | ~0 image/s (rendu à la demande effectif) |
| Robustesse | Aucune erreur sur les appareils testés |

**Règle.** Si LiteRT GPU fonctionne partout et va au moins 3× plus vite que le meilleur chemin CPU, on retient LiteRT (variante `full`) avec ONNX Runtime CPU pour la variante `foss`. Sinon, on retient ONNX Runtime partout : plus simple et entièrement libre.

## Résultats

### Émulateur : validation fonctionnelle (3 octobre 2026)

Environnement : Android 14 x86_64, GPU émulé en logiciel (SwiftShader, OpenGL ES 3.0). **Les durées ne sont pas représentatives d'un téléphone.**

| Élément | Résultat |
|---|---|
| Téléchargement des modèles | ✅ Tailles exactes. La reprise (HTTP Range) a été validée après une coupure réseau. |
| LiteRT · GPU | ❌ Échec attendu, « Failed to compile model » : pas d'OpenCL, et le repli OpenGL exige les compute shaders. **À mesurer sur téléphone.** |
| LiteRT · NPU | ⚠️ Sans pilote NPU du constructeur, LiteRT bascule silencieusement sur le CPU. |
| ONNX Runtime · XNNPACK | ❌ Le noyau Resize de XNNPACK refuse le modèle à l'initialisation. Configuration retirée du Lab. |
| LiteRT · CPU (FP32) | 20,7 s par inférence (émulation) |
| ONNX Runtime · CPU (FP32 / INT8 / INT8 portrait) | 16,5 s / 14,8 s / 19,6 s (émulation) |
| Qualité de la profondeur | ✅ Sujet et fond bien séparés. Avec le filtre guidé, les contours suivent la silhouette (post-traitement : 786 ms en 512×512). |
| Moteur de rendu | ✅ Tests instrumentés : le plan de focus reste immobile, le premier plan et le fond partent en sens opposés, et le ray-marching retombe sur le décalage simple quand la profondeur est plate. |
| Fond d'écran animé | ✅ Service dans un process séparé ; appliqué via le sélecteur système. |
| Rendu à la demande | ✅ 10 images en 41,5 s d'affichage pour 2 065 événements capteur. |
| Material You | ✅ La palette système suit le fond d'écran. |
| Poids natif arm64 | ONNX Runtime 31,5 Mo ; LiteRT 8,8 Mo, dont 3,0 Mo d'accélérateur GPU |

### Téléphones

**Samsung Galaxy S26 Ultra** (SM-S948B) : Snapdragon 8 Elite Gen 5, Adreno 840, Android 16, 11,7 Go de RAM. Image 1439×2560, état thermique normal.

| Configuration | Init | 1re inférence | Moyenne (5) | PSS |
|---|---|---|---|---|
| LiteRT · GPU · FP32 · 518² | 851 ms | *0,4 ms* | *28,8 ms* (max 48,5 ms) | 584 Mo |
| LiteRT · CPU · FP32 · 518² | 36 ms | 254 ms | 225 ms | 627 Mo |
| ONNX Runtime · CPU · INT8 · 518² | 162 ms | 369 ms | 322 ms | 742 Mo |

- **Mesure LiteRT GPU faussée.** `run()` est asynchrone sur GPU, donc le chronomètre s'arrêtait avant la fin du calcul. Le vrai temps se situe plutôt vers 40–50 ms (d'après le maximum). Le Lab mesure désormais `run()` + lecture de la sortie.
- Post-traitement : 176 ms (normalisation 7 ms, redimensionnement 73 ms, contours 95 ms).
- Rendu de l'aperçu à 16 pas en 1440×3120 : 6,88 ms par image, sous le seuil de 8 ms. Sur milieu de gamme, il faudra sans doute le palier 8 pas ou une résolution de rendu réduite (paliers de qualité prévus).
- Observation de l'utilisateur : **ONNX Runtime donne le meilleur rendu visuel.**

## Décision

**ONNX Runtime (CPU, modèle Depth Anything V2 Small en INT8) est retenu comme runtime unique.**

Raisons :

1. **Meilleur rendu visuel**, constaté sur appareil. Le modèle étant le même, l'écart vient probablement de l'export TFLite de Qualcomm ou de la précision FP16 sur GPU. Avec ONNX, on maîtrise l'export.
2. **100 % libre (MIT)**. Une seule variante de build, compatible F-Droid : ni accélérateur GPU propriétaire, ni dépendance Google Play (`ai-delivery`), ni contournement Gradle (`android.uniquePackageNames=false`).
3. **Même fichier `.onnx`** sur le téléphone, le serveur HD et la CI de la bibliothèque.
4. **Vitesse suffisante** pour une génération ponctuelle : 0,32 s sur haut de gamme. Estimation : environ 1 s en milieu de gamme et quelques secondes en entrée de gamme.

Conséquences :

- **Début de la Phase 1 (fait le 3 octobre 2026)** : LiteRT retiré de l'app (module `ml`, catalogue de modèles, `gradle.properties`), interface `DepthEstimator` conservée. Les modèles téléchargés sont désormais vérifiés par empreinte SHA-256.
- **Taille** : ONNX Runtime ajoute 31,5 Mo de code natif par ABI. Parades : AAB avec découpage par ABI, puis une build ONNX Runtime réduite aux opérateurs du modèle.
  **Fait le 11 octobre 2026** : APK découpés par ABI depuis la v0.1.0, et ONNX Runtime 1.31 compilé sur mesure ([tools/onnxruntime](../../tools/onnxruntime/README.md)) : opérateurs des modèles seulement, CPU seul, **sans la télémétrie Microsoft** que le paquet officiel embarque. 13,9 Mo en arm64 au lieu de 33,0 Mo.
- **Pas d'accélération GPU générique.** Si l'entrée de gamme se révèle trop lente, on réévaluera un accélérateur optionnel (LiteRT GPU dans une variante `full`, ou le fournisseur QNN de Qualcomm).
- **À vérifier plus tard** : ONNX FP32 contre INT8 (qualité et vitesse), mesures sur un téléphone d'entrée de gamme, statistiques du fond d'écran sur une journée.
