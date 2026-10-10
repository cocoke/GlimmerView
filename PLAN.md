# GlimmerView : plan du projet

> *Subtle shifts, stunning screens.*
>
> Fonds d'écran 3D (parallaxe) pour Android, open source (GPL-3.0).
> Document de cadrage v1.1, 3 octobre 2026 : faisabilité, choix techniques, architecture, feuille de route, risques.

---

## 0. En bref

- **La génération peut se faire entièrement sur le téléphone.** La carte de profondeur (Depth Anything V2 Small) se calcule en ~50–85 ms sur le NPU d'un Snapdragon récent, en ~0,5 s sur le GPU d'un milieu de gamme et en quelques secondes en CPU. Le remplissage des zones cachées derrière le sujet (inpainting MI-GAN) prend de ~6 à ~30 ms sur GPU. Les fonctions de base n'ont besoin d'aucun serveur.
- **Architecture recommandée : local d'abord, serveur en option.** L'app fonctionne hors ligne, sans compte et sans envoyer de photo. Un serveur « HD » auto-hébergeable (gros modèles) peut venir plus tard. La bibliothèque est servie en fichiers statiques, pour un coût quasi nul.
- **Stack :** Kotlin + Jetpack Compose, `WallpaperService`, moteur OpenGL ES 3 maison (shader de parallaxe par ray-marching, 2 calques), **ONNX Runtime** pour l'IA (choisi en Phase 0), 100 % libre et compatible F-Droid.
- **Modèles retenus (licences libres) :** Depth Anything V2 Small (Apache-2.0) et MI-GAN (MIT) sur mobile ; Depth Anything 3 Mono-Large (Apache-2.0) et LaMa (Apache-2.0) côté serveur. **À exclure :** Depth Anything V2 Base/Large/Giant et DA3 Large/Giant (CC BY-NC, non commercial), Apple SHARP (recherche uniquement).
- **Feuille de route :** Phase 0 prototypes (2–3 sem.), puis v0.1 MVP, v0.2 qualité, v0.3 bibliothèque en ligne, v0.4 serveur HD (optionnel) et v1.0 publique. Compter ≈ 6–7 mois à temps partiel.

---

## 1. Vision et principes

**Objectif.** Une app gratuite, sans pub, sans traceur et open source, qui transforme n'importe quelle image en fond d'écran animé. L'effet de profondeur réagit aux mouvements du téléphone. L'app propose aussi une bibliothèque de fonds prêts à l'emploi.

**Principes**
1. **Local d'abord.** Par défaut, aucune photo ne quitte le téléphone, et l'app fonctionne hors ligne.
2. **Libre de bout en bout.** Le code, le format de fichier, les modèles d'IA et les contenus de la bibliothèque sont sous licences libres.
3. **Sobre.** L'impact sur la batterie est mesuré et minimal : aucun rendu quand rien ne bouge.
4. **Pour tout le monde.** Android 8+, téléphones d'entrée de gamme (même sans gyroscope), accessibilité, plusieurs langues.
5. **Ouvert.** Format documenté, import/export, serveur auto-hébergeable.

**Défauts des apps existantes et notre réponse**

| Défaut fréquent | Notre réponse |
|---|---|
| Pubs, abonnement, filigrane | Gratuit et sans pub, dons facultatifs |
| Photos envoyées sur un serveur | Traitement 100 % local par défaut |
| Bords étirés, halos autour du sujet | Arrière-plan reconstruit par IA (inpainting) et correction des contours |
| Vieux modèles de profondeur | Modèles récents (Depth Anything V2/3) |
| Batterie vidée par un rendu permanent à 60 i/s | Rendu à la demande, 0 i/s à l'arrêt, respect du mode économie |
| Formats fermés, pas d'export | Format ouvert, import/export, export vidéo |
| Fonction réservée à certains appareils (« Cinematic wallpapers » des Pixel, « scènes spatiales » d'iOS 26) | Fonctionne sur tous les Android 8+ |

**Projets de référence**
- **DepthFlow** (Python, AGPL-3.0) : shader de parallaxe par ray-marching, très abouti. On s'inspire de la technique sans copier le code, à cause de la licence.
- **Lively Wallpaper** (Windows, GPL-3.0) : crée déjà des fonds parallaxe à partir d'une estimation de profondeur.
- **Muzei** (Android, Apache-2.0) : référence d'architecture pour un fond d'écran animé avec sources de contenu et rotation.
- **litert-community** et **Qualcomm AI Hub** (Hugging Face) : modèles déjà convertis pour mobile, avec des benchmarks.

---

## 2. Comment fonctionne l'effet (et où sont les pièges)

```
 inclinaison du téléphone ─► décalage à l'écran = intensité × (disparité − focus)

   disparité 1.0   premier plan    ████████  ──►  suit l'inclinaison
   disparité 0.6   plan de focus   ▒▒▒▒▒▒▒▒       immobile
   disparité 0.0   horizon, ciel   ░░░░░░░░  ◄──  part en sens inverse
```

- Quand le point de vue se déplace, un objet proche bouge beaucoup à l'écran et un objet lointain bouge peu. Le décalage est proportionnel à **1/profondeur**, ce qu'on appelle la **disparité**.
- Les modèles d'estimation monoculaire (Depth Anything, MiDaS…) produisent justement une **disparité relative**, définie à un facteur près. Pas besoin de profondeur métrique.
- **Plan de focus (pivot).** Ce qui est à cette profondeur reste fixe. Ce qui est devant bouge dans un sens, ce qui est derrière dans l'autre, d'où l'effet « fenêtre ». L'utilisateur choisit ce plan en touchant le sujet.
- **Les trois pièges**
  1. **Halos.** La carte de profondeur est basse résolution et floue sur les contours, donc des pixels du fond « collent » au sujet. Parade : upsampling guidé par l'image, puis dilatation de l'avant-plan.
  2. **Zones découvertes (désocclusion).** En bougeant, on voit derrière le sujet, là où l'image n'a aucune information. Résultat : des étirements. Parade : un calque d'arrière-plan reconstruit par **inpainting**.
  3. **Bords de l'écran.** Le décalage fait apparaître du vide sur les bords. Parade : un léger zoom (*overscan*) proportionnel à l'intensité.

---

## 3. Génération : sur le téléphone ou sur un serveur ?

### 3.1 Le pipeline

```
Image 2D
  │  1. cadrage au format de l'écran (+ marge d'overscan)
  ▼
Estimation de profondeur ......... mobile : Depth Anything V2 Small
  │                                 serveur : Depth Anything 3 Mono-Large
  │  2. normalisation robuste → disparité 0..1
  │  3. upsampling guidé par l'image (pleine résolution, contours nets)
  │  4. anti-halo (dilatation de l'avant-plan, lissage)
  ▼
Découpage en calques ............. masque d'avant-plan (profondeur, puis « touche le sujet »)
  ▼
Inpainting de l'arrière-plan ..... mobile : MI-GAN  ·  serveur : LaMa
  ▼
Paquet .dwp (image + profondeur + fond reconstruit + réglages) ─► moteur de rendu
```

### 3.2 Modèles d'estimation de profondeur

| Modèle | Taille | Licence | Où | Commentaire |
|---|---|---|---|---|
| **Depth Anything V2 Small** | 24,7 M param. (~50 Mo en FP16) | Apache-2.0 | 📱 **par défaut** | Très bon rapport qualité/vitesse ; versions TFLite et ONNX déjà publiées |
| MoGe-2 ViT-S | 35 M (71 Mo en FP16) | MIT | 📱 alternative | Fournit aussi normales et masque de confiance ; tourne entièrement sur GPU en LiteRT |
| Depth Anything 3 Small / Base | 80 M / 120 M | Apache-2.0 | 📱 haut de gamme ou 🖥️ | Plus récents et plus lourds ; qualité sur une image seule à évaluer |
| **Depth Anything 3 Mono-Large** | 350 M | Apache-2.0 | 🖥️ **serveur HD** | Modèle dédié à la profondeur relative monoculaire de haute qualité |
| Marigold v1-1 | ~1 Md (diffusion) | Apache-2.0 | 🖥️ mode « ultra » | Détails très fins, mais lent |
| Depth Anything V2 Base/Large/Giant | 97 M – 1,3 Md | CC BY-NC 4.0 | ❌ | Non commercial, incompatible avec une app libre |
| Depth Anything 3 Large/Giant/Nested | 350 M – 1,4 Md | CC BY-NC 4.0 | ❌ | Idem |
| Apple Depth Pro | — | Apple (ASCL) | ⚠️ | Licence spécifique, non approuvée OSI, à faire valider avant tout usage |
| Apple SHARP (3D Gaussian splatting) | — | Recherche uniquement | ❌ | Produit une vraie vue 3D en moins d'une seconde, mais usage réservé à la recherche. À surveiller. |

### 3.3 Inpainting et segmentation

| Tâche | Modèle | Licence | Où | Commentaire |
|---|---|---|---|---|
| Inpainting | **MI-GAN 512** | MIT | 📱 | 16,3 Mo en FP16, conçu pour le mobile ; version LiteRT déjà prête |
| Inpainting | LaMa (big-lama) | Apache-2.0 | 🖥️ | Référence de qualité pour les grandes zones |
| Masque du sujet | Déduit de la profondeur | — | 📱 MVP | Seuil sur l'histogramme de disparité + adoucissement guidé |
| Masque du sujet | MobileSAM / EfficientSAM | Apache-2.0 | 📱 v0.3 | « Touche le sujet pour le sélectionner » |
| Détourage fin | BiRefNet | MIT | 🖥️ | Cheveux, feuillages |
| Agrandissement | Real-ESRGAN | BSD-3 | 🖥️ | Pour les petites images |

### 3.4 Moteurs d'inférence Android

| Runtime | Licence | Accélération | Points forts | Points faibles |
|---|---|---|---|---|
| **LiteRT 2.x** (ex-TensorFlow Lite) | Apache-2.0, mais l'accélérateur GPU est fourni en binaire, pas encore open source | CPU (XNNPACK) ; GPU (OpenCL, avec OpenGL en repli) ; NPU Qualcomm/MediaTek | API Kotlin `CompiledModel`, minSdk 23 ; modèles prêts (litert-community, Qualcomm AI Hub) ; environ 10× plus rapide sur GPU que sur CPU | Conversion PyTorch → TFLite parfois délicate ; accélérateur GPU non libre, gênant pour F-Droid |
| ONNX Runtime | MIT | CPU (XNNPACK), QNN (Snapdragon) ; NNAPI déprécié depuis Android 15 | Le plus simple : le même fichier `.onnx` sur mobile, sur le serveur et en CI | Pas de GPU générique sur Android, donc surtout du CPU |
| ExecuTorch 1.x | BSD | XNNPACK, **Vulkan (GPU libre)**, QNN, MediaTek… | Natif PyTorch ; Arm publie un DA-V2 Small INT8 prêt à l'emploi | Plus jeune, intégration plus lourde |
| NCNN | BSD-3 | **Vulkan (GPU libre)** | Léger, très utilisé dans les apps F-Droid | Conversion via PNNX, glue C++/JNI à écrire |

> **Décision Phase 0 (3 octobre 2026) : ONNX Runtime**, voir [ADR 0001](docs/adr/0001-runtime-ia.md). Sur Galaxy S26 Ultra, il a donné le meilleur rendu visuel, en 0,32 s par inférence (INT8). Il est 100 % libre, ce qui permet une seule variante de build compatible F-Droid. La recommandation initiale ci-dessous est conservée pour l'historique.

**Recommandation initiale.** LiteRT derrière une interface `InferenceBackend`, en deux variantes de build :
- **`full`** (GitHub, Google Play) : bascule automatique GPU → NPU → CPU.
- **`foss`** (F-Droid, IzzyOnDroid) : CPU uniquement, avec une build LiteRT compilée depuis les sources (par exemple `de.schliweb:tensorflow-lite-fdroid`) ou ONNX Runtime. C'est plus lent mais 100 % libre. Piste pour plus tard : un GPU libre via ExecuTorch Vulkan ou NCNN.
- Ce choix sera confirmé (ou non) par les benchmarks de la Phase 0.

### 3.5 Performances publiées

| Modèle | Appareil | Matériel | Latence |
|---|---|---|---|
| DA-V2 Small 518×518 | Galaxy S23 (Snapdragon 8 Gen 2) | NPU | 84 ms |
| DA-V2 Small 518×518 | Galaxy S24 (Snapdragon 8 Gen 3) | NPU | 62 ms |
| DA-V2 Small 518×518 | Galaxy S25 (Snapdragon 8 Elite) | NPU | 47 ms |
| DA-V2 Small (ONNX + QNN) | Snapdragon 8 Elite Gen 5 | NPU | 18 ms (12 ms quantifié) |
| MoGe-2 ViT-S 448×448 | Pixel 8a (Tensor G3) | GPU | 522 ms |
| MoGe-2 ViT-S 448×448 | Pixel 8a | CPU | 4,8 s |
| MoGe-2 ViT-S 448×448 | Galaxy S26 | NPU | 56 ms |
| MI-GAN 512×512 FP16 | Pixel 8a | GPU | ~6 ms |
| MI-GAN 512×512 FP16 | Galaxy S26 | GPU / NPU | 27 ms / 31 ms |

**En pratique.** Un ViT de cette taille met environ 0,5 s sur le GPU d'un milieu de gamme et environ 5 s en CPU. La génération n'a lieu qu'une fois par image : 10 à 15 s sur un vieux téléphone restent acceptables avec une barre de progression.

### 3.6 Options serveur

| Option | Coût | Avantages | Inconvénients |
|---|---|---|---|
| **Pas de serveur public** (local + auto-hébergement) | 0 € | Vie privée, aucune maintenance | Pas de mode HD pour qui n'a pas de GPU |
| Modal (GPU serverless) | 30 $/mois de crédits gratuits ; L4 ≈ 0,80 $/h, facturé à la seconde | Descend à zéro instance au repos, déploiement simple en Python | Hébergé aux États-Unis (RGPD à documenter) ; démarrages à froid |
| RunPod Serverless, Replicate | À l'usage | Même principe que Modal | Mêmes limites |
| Hugging Face Space (ZeroGPU ou CPU) | Gratuit, avec quotas | Démo publique très facile | Quotas, mise en veille, pas conçu pour servir l'API d'une app |
| VPS CPU (Hetzner, OVH…) | ~5–15 €/mois | Prix prévisible, hébergement en UE | Trop lent pour les gros modèles (convient aux petits) |
| PC perso avec GPU + Docker | Électricité | GPU déjà payé | Disponibilité et maintenance à ta charge |
| Notebook Colab | Gratuit | Pratique pour les utilisateurs avancés | Manuel |

**Ordre de grandeur.** Une génération HD consomme environ 3 à 10 s de GPU L4, démarrages à froid compris, soit ~0,001–0,002 $. Les 30 $ de crédits mensuels gratuits de Modal couvrent donc de l'ordre de **10 000 générations par mois**.

### 3.7 Verdict

| Étape | Sur le téléphone | Sur serveur (optionnel) |
|---|---|---|
| Carte de profondeur | ✅ DA-V2 Small (0,05 à ~5 s selon l'appareil) | ➕ DA3 Mono-Large ou Marigold, plus fins (cheveux, feuillages) |
| Masque, calques | ✅ déduits de la profondeur, puis SAM mobile | ➕ BiRefNet |
| Inpainting des zones cachées | ✅ MI-GAN | ➕ LaMa |
| Agrandir une petite image | ⚠️ lent | ✅ Real-ESRGAN |
| Étendre l'image au format de l'écran (outpainting) | ❌ | ✅ diffusion (plus tard) |
| Générer une image à partir d'un texte | ⚠️ NPU haut de gamme seulement | ✅ FLUX.1 [schnell], Apache-2.0 (plus tard) |
| Vraie 3D (Gaussian splatting) | ❌ | ⚠️ à surveiller (licences) |

**Conclusion.** Le téléphone suffit pour tout le MVP. Le serveur n'apporte qu'un gain de qualité et des fonctions génératives lourdes. À n'ajouter que si le projet trouve son public et son financement.

---

## 4. Architecture technique

### 4.1 Vue d'ensemble

```
TÉLÉPHONE
├─ Process principal (UI Jetpack Compose)
│   ├─ Bibliothèque ──────────► télécharge les paquets .dwp (CDN)
│   ├─ Créateur ─► pipeline IA local (ONNX Runtime) ─► paquet .dwp
│   │              └─ (opt-in) API « HD » ─────► serveur GPU
│   └─ Mes fonds / Réglages ──► « fond actif » (fichiers + DataStore multi-process)
└─ Process « :wallpaper » (léger : ni Compose, ni IA)
    └─ WallpaperService ─► capteurs + filtre ─► rendu OpenGL ES 3 (shader parallaxe)

EN LIGNE (optionnel)
├─ CDN statique (Cloudflare R2) : index.json + paquets .dwp
│   ▲ publié par : dépôt GitHub « library » + GitHub Actions
│     (validation, profondeur HQ, calques, miniatures, index)
└─ API « HD » : conteneur FastAPI sans état (Modal / RunPod / PC perso)
```

### 4.2 Stack

| Domaine | Choix | Pourquoi |
|---|---|---|
| Langage | Kotlin 2.x, coroutines et Flow | Standard Android |
| UI | Jetpack Compose + Material 3, Navigation Compose | Moderne, couleurs dynamiques |
| Injection de dépendances | Hilt (ou Koin, plus léger) | |
| Données | Room (catalogue local), DataStore multi-process (fond actif, réglages) | État partagé avec le process du fond d'écran |
| Tâches de fond | WorkManager (génération, téléchargement des modèles) | La génération continue même si on quitte l'écran |
| Réseau | OkHttp + Retrofit (ou Ktor) + kotlinx.serialization | |
| Images | Coil 3, ImageDecoder, ExifInterface, Photo Picker | Aucune permission de stockage nécessaire |
| Rendu | OpenGL ES 3.0 + EGL14, GLSL ES 3.00, thread de rendu dédié + Choreographer | Plus de 99 % des appareils, contrôle total, coût faible |
| IA | ONNX Runtime (CPU) derrière l'interface `DepthEstimator` | 100 % libre, même `.onnx` partout, meilleur rendu mesuré ([ADR 0001](docs/adr/0001-runtime-ia.md)) |
| Vidéo | MediaCodec (H.264) alimenté par une surface EGL | Export de boucles à partager |
| Rapports de crash | ACRA (opt-in, auto-hébergé) | Pas de Firebase ni de traceur |
| Licences tierces | AboutLibraries | Écran « licences » généré automatiquement |
| Qualité | ktlint/detekt, Android Lint, JUnit, Turbine, Robolectric, Roborazzi, Macrobenchmark, Baseline Profiles | |
| Serveur | Python 3.12, FastAPI, PyTorch, Docker | L'écosystème des modèles est en Python |

**Cibles.** `minSdk 26` (Android 8.0) et `targetSdk 36`, obligatoire sur Google Play depuis le 31/08/2026. OpenGL ES 3.0 requis.

**Outillage retenu (Phase 0).** AGP 9.4.1 avec le support Kotlin intégré, Gradle 9.6, Kotlin 2.4.20, `compileSdk 37`. Les versions actuelles d'AndroidX l'imposent (`core-ktx` 1.19 exige compileSdk 37 et AGP 9.1+). Il faut donc un Android Studio récent pour ouvrir le projet.

**Constat sur LiteRT 2.2.0.** L'AAR embarque l'accélérateur GPU sous forme de binaire précompilé (`libLiteRtClGlAccelerator.so`). Il dépend aussi de `com.google.android.play:ai-delivery`, une bibliothèque Google Play non libre. Cela confirme le besoin d'une variante `foss` sans ces composants pour F-Droid. **LiteRT a été retiré au début de la Phase 1** (3 octobre 2026), au profit d'ONNX Runtime seul : une seule variante de build, compatible F-Droid.

### 4.3 Modules Gradle

```
mobile-3d-wallpaper/
├── app/                 Compose, navigation, injection de dépendances
├── core/
│   ├── model/           Wallpaper, ParallaxParams… (Kotlin pur)
│   ├── data/            Room, DataStore multi-process, dépôts
│   ├── format/          lecture/écriture du format .dwp
│   ├── network/         client du catalogue + client de l'API HD
│   └── designsystem/    thème Material 3, composants
├── render/              moteur GLES 3 partagé (aperçu dans l'app ET fond d'écran)
├── wallpaper/           WallpaperService, capteurs, cycle de vie, batterie
├── ml/                  runtimes, téléchargement des modèles, pipeline profondeur/calques/inpainting
├── feature/
│   ├── library/         catalogue en ligne
│   ├── creator/         import → profondeur → retouche → aperçu → appliquer
│   ├── mywallpapers/    fonds locaux
│   └── settings/
├── server/              (Phase 4) FastAPI + Dockerfile
├── tools/               scripts Python : benchmarks, conversion des modèles
└── docs/                ce plan, spécification .dwp, ADR (décisions d'architecture)
```

Le contenu de la bibliothèque vit dans un **dépôt séparé** (`…-library`) qui a sa propre CI.

### 4.4 Pipeline de génération sur l'appareil

1. **Import.** Photo Picker (`PickVisualMedia`, aucune permission de stockage), rotation EXIF, décodage sous-échantillonné (≤ ~1440×3200 + marge) pour éviter de saturer la mémoire.
2. **Cadrage** au ratio de l'écran, avec une marge d'overscan de 5 à 10 %. Plus tard, une « zone de sécurité » pour les tablettes et les pliables.
3. **Profondeur.** En entrée du modèle, du 518×518 (modèles déjà prêts) ou un export « portrait » dédié (par ex. 392×868, multiples de 14), à comparer en Phase 0. Normalisation ImageNet, puis inférence GPU → NPU → CPU.
4. **Post-traitement**
   - Normalisation robuste (percentiles 1–99 %) pour obtenir une disparité entre 0 et 1.
   - **Filtre guidé** (He et al.) avec l'image couleur comme guide : on remonte à pleine résolution avec des contours nets.
   - **Dilatation** de l'avant-plan (2–4 px) et léger flou contre les halos.
   - Stockage sur 16 bits.
5. **Calques.** Dans le MVP, le masque d'avant-plan vient des discontinuités de profondeur et d'un seuil d'Otsu sur l'histogramme de disparité. Ensuite viendra la sélection par toucher (SAM mobile). Le masque est adouci par un filtre guidé.
6. **Inpainting**
   - Le masque est une bande derrière les contours du sujet. Sa largeur correspond au décalage maximal en pixels.
   - MI-GAN traite des tuiles de 512×512 centrées sur ces zones, à pleine résolution.
   - La profondeur du fond est prolongée par diffusion (*push-pull*).
7. **Finalisation.** Miniature, couleurs dominantes (`WallpaperColors`), écriture du `.dwp`, enregistrement dans Room.
8. **Exécution**
   - WorkManager (travail expédié), avec progression et annulation.
   - Les modèles sont téléchargés à la demande depuis un dépôt Hugging Face du projet (miroir sur GitHub Releases) et vérifiés par SHA-256.
   - Option « Wi-Fi uniquement » ; les modèles sont supprimables.

### 4.5 Moteur de rendu

| Approche | Coût par image | Qualité | Usage |
|---|---|---|---|
| Décalage UV simple | Très faible | Étirements aux contours | Palier « bas » |
| **Ray-marching (parallax occlusion mapping), 1 calque** | Moyen (8–32 lectures par pixel) | Occlusions correctes ; étirements dans les zones découvertes | **MVP** |
| **Ray-marching, 2 calques** (sujet + fond reconstruit) | Moyen+ | Quasi sans artefacts | **v0.2**, palier « haut » |
| Maillage 3D déformé + calque de fond | Faible (calcul par sommet) | Bon, vraie perspective | Alternative pour les GPU faibles |
| 3D Gaussian splatting | Élevé | Excellent | À surveiller |

Esquisse du shader (GLSL ES 3.00, non testée) :

```glsl
#version 300 es
precision highp float;

uniform sampler2D uColor;   // image
uniform sampler2D uDepth;   // disparité : 0 = loin, 1 = proche
uniform vec2  uTilt;        // inclinaison filtrée, dans [-1, 1]²
uniform float uStrength;    // décalage max en UV (ex. 0.03 = 3 % de l'écran)
uniform float uFocus;       // disparité du plan qui reste immobile
uniform int   uSteps;       // 8 / 16 / 32 selon le palier de qualité

in vec2 vUv;                // déjà zoomé (overscan) dans le vertex shader
out vec4 fragColor;

// Point de l'image, de disparité h, vu à travers le pixel vUv
vec2 sampleUv(float h) { return vUv - uTilt * uStrength * (h - uFocus); }

void main() {
    float stepH = 1.0 / float(uSteps);
    float h = 1.0;
    float d = texture(uDepth, sampleUv(h)).r;
    float prevH = h, prevD = d;
    // On descend du plan le plus proche vers le fond : la première surface
    // rencontrée est celle qui est visible (les occlusions sont gérées).
    for (int i = 0; i < 64 && i < uSteps && d < h; i++) {
        prevH = h; prevD = d;
        h -= stepH;
        d = texture(uDepth, sampleUv(h)).r;
    }
    // Affinage : interpolation linéaire entre les deux derniers pas
    float a = d - h, b = prevD - prevH;
    float hHit = mix(h, prevH, clamp(a / (a - b + 1e-5), 0.0, 1.0));
    fragColor = texture(uColor, sampleUv(hHit));
}
```

Avec deux calques, le même ray-marching s'applique au calque du sujet. Là où son masque est transparent, on lit le calque de fond reconstruit avec son propre décalage, plus simple car sa profondeur est lisse.

**Paliers de qualité**
- **Bas** : décalage simple, 30 i/s maximum (entrée de gamme, mode économie).
- **Moyen** : ray-marching avec 12–16 pas, plus l'affinage.
- **Haut** : 24–32 pas et 2 calques.
- Le palier est choisi automatiquement au premier lancement (micro-benchmark du GPU) et reste modifiable dans les réglages. Le nombre de pas s'adapte au décalage maximal en pixels (≈ décalage / 2).

**Modes de mouvement et effets**
- Gyroscope (par défaut).
- Animation automatique douce (boucle, sans capteur).
- Suivi du doigt.
- Défilement des pages du launcher (`onOffsetsChanged`).
- Zoom du launcher (`onZoomChanged`, API 30+) : un léger travelling quand on ouvre le tiroir d'apps ou les notifications.
- Options : flou de profondeur, vignettage, intensité, inversion du sens.

### 4.6 Service de fond d'écran

```xml
<uses-feature android:name="android.software.live_wallpaper" android:required="true" />
<uses-feature android:glEsVersion="0x00030000" android:required="true" />

<service
    android:name=".wallpaper.ParallaxWallpaperService"
    android:exported="true"
    android:permission="android.permission.BIND_WALLPAPER"
    android:process=":wallpaper">
    <intent-filter>
        <action android:name="android.service.wallpaper.WallpaperService" />
    </intent-filter>
    <meta-data
        android:name="android.service.wallpaper"
        android:resource="@xml/parallax_wallpaper" />
</service>
```

```kotlin
class ParallaxWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ParallaxEngine()

    inner class ParallaxEngine : Engine() {
        private val renderer = ParallaxRenderer()          // module :render, thread GL dédié
        private val motion = MotionController(applicationContext) { tilt ->
            renderer.requestFrame(tilt)                    // ne dessine que si ça bouge
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            renderer.attach(holder.surface)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            renderer.resize(width, height)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) motion.start() else motion.stop()   // capteurs coupés hors écran
            renderer.setVisible(visible)
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float,
            xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int,
        ) {
            renderer.setPageOffset(xOffset)                  // défilement des pages du launcher
        }

        override fun onZoomChanged(zoom: Float) {           // API 30+ : tiroir d'apps, notifications
            renderer.setLauncherZoom(zoom)
        }

        override fun onComputeColors(): WallpaperColors? =  // API 27+ : Material You
            renderer.wallpaperColors()

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            renderer.detach()
        }
    }
}
```

**Capteurs**
- On utilise `TYPE_GAME_ROTATION_VECTOR` (gyroscope + accéléromètre, sans boussole, donc sans sauts). En repli, `TYPE_ROTATION_VECTOR`, puis l'accéléromètre ou la gravité, car beaucoup de téléphones d'entrée de gamme **n'ont pas de gyroscope**. Dernier repli : l'animation automatique.
- L'orientation est mesurée par rapport à une **référence qui suit lentement** l'orientation courante (constante de temps de 2–5 s). L'image se recentre donc d'elle-même quand on change de position (debout, allongé…).
- Un **filtre One Euro** (réactif quand on bouge vite, stable à l'arrêt) et un ressort amorti ramènent l'image au centre.
- La rotation de l'écran est prise en compte (`SensorManager.remapCoordinateSystem`).

**Batterie**
- Les capteurs ne sont enregistrés que lorsque le fond est visible (`onVisibilityChanged`).
- Le rendu se fait à la demande via `Choreographer` : on ne dessine que si le décalage a changé au-delà d'un seuil. À l'arrêt, 0 image/s.
- `Surface.setFrameRate()` (API 30+) demande 60 ou 30 i/s pour ne pas forcer l'écran en 120 Hz.
- En mode économie d'énergie ou en cas de chauffe (`PowerManager`), on passe au palier inférieur à 30 i/s, voire à une image fixe.
- Le process `:wallpaper` reste séparé et léger. Les textures occupent ~30 Mo (1080×2400) à ~55 Mo (1440×3200) avec 2 calques.
- Plusieurs moteurs peuvent coexister (aperçu système + fond actif). Le décodage est partagé via un cache, et les textures sont libérées dans `onSurfaceDestroyed`.

**Appliquer un fond**
- Si notre service n'est pas déjà le fond actif (`WallpaperManager.getWallpaperInfo()`), on ouvre l'aperçu système avec `ACTION_CHANGE_LIVE_WALLPAPER` et `EXTRA_LIVE_WALLPAPER_COMPONENT`, puis l'utilisateur valide.
- Sinon, on change simplement le « fond actif » dans le DataStore. Le moteur observe ce changement et recharge à chaud, sans repasser par le sélecteur système.
- **Accueil et verrouillage**
  - Selon la version d'Android et le constructeur, un fond animé s'applique à l'accueil, au verrouillage ou aux deux.
  - Sur **Android 16+**, `WallpaperDescription` et `WallpaperInstance` permettent un contenu différent à l'accueil et au verrouillage depuis le même service.
  - En repli, on pose une **image fixe** (rendu en position neutre) sur le verrouillage via `WallpaperManager.setBitmap(…, FLAG_LOCK)`.
- `onComputeColors()` et `notifyColorsChanged()` font suivre au système les couleurs du fond (Material You, Android 12+).
- Dans l'aperçu système (`isPreview()`), une petite animation automatique montre l'effet même sans bouger le téléphone.

### 4.7 Format de fichier ouvert `.dwp` (nom provisoire)

```
mon-fond.dwp  (archive ZIP)
├── manifest.json
├── color.webp            image principale (≤ 1440×3200 + marge)
├── depth.png             disparité, niveaux de gris 16 bits (0 = loin, 65535 = proche)
├── background.webp       (optionnel) arrière-plan reconstruit
├── background_depth.png  (optionnel)
├── mask.png              (optionnel) alpha de l'avant-plan
└── thumbnail.webp        360×800
```

```json
{
  "format": "dwp",
  "formatVersion": 1,
  "id": "exemple-montagne",
  "title": "Montagne au lever du jour",
  "author": { "name": "Jane Doe", "url": "https://example.org/jane" },
  "license": "CC0-1.0",
  "sourceUrl": "https://example.org/photo/123",
  "aiGenerated": false,
  "generator": { "depthModel": "depth-anything-v2-small", "inpaintModel": "mi-gan-512" },
  "width": 1440,
  "height": 3200,
  "layers": [
    { "color": "background.webp", "depth": "background_depth.png" },
    { "color": "color.webp", "depth": "depth.png", "mask": "mask.png" }
  ],
  "defaults": { "intensity": 0.5, "focus": 0.62, "zoom": 1.06, "motion": "gyro" },
  "thumbnail": "thumbnail.webp",
  "tags": ["nature", "montagne"]
}
```

- La spécification est versionnée dans `docs/format.md` et publiée sous **CC0**, pour que d'autres apps puissent l'implémenter.
- *Version 1 fixée le 4 octobre 2026 : [docs/format.md](docs/format.md) fait foi. Par rapport à l'ébauche ci-dessus : `defaults.strength` remplace `intensity`, champs `medium`, `language` et `created` ajoutés, type MIME `application/x-dwp+zip`, carte de profondeur à une résolution libre.*
- Lecture tolérante : les champs inconnus sont ignorés.
- Validation à l'import : tailles, dimensions, protection contre les « zip bombs ».
- On peut aussi importer une image accompagnée de sa propre carte de profondeur (PNG), ou une photo « Portrait » de Google Camera dont la profondeur est embarquée (format *Dynamic Depth*).

### 4.8 Bibliothèque

**Contenus autorisés**
- **Sources sûres**
  - Wikimedia Commons (CC0, domaine public, CC BY, CC BY-SA ; vérifier chaque fichier).
  - NASA (domaine public, hors logos).
  - ESA/Hubble et ESO (CC BY 4.0).
  - Musées en Open Access CC0 (Smithsonian, Art Institute of Chicago, Rijksmuseum, The Met).
  - Photos et illustrations données par la communauté (CC0 ou CC BY 4.0).
  - Images générées par une IA à licence libre, signalées comme telles.
- **À éviter**
  - Unsplash : sa licence interdit de compiler ses photos pour reproduire un service similaire ou concurrent.
  - Pexels et Pixabay : leurs licences interdisent la redistribution sur des plateformes de fonds d'écran.
  - Toute image aux droits inconnus.
- Chaque fond porte un auteur, un lien source, une licence et, le cas échéant, une mention IA. Ces informations sont affichées dans l'app ; pour les licences CC BY, c'est obligatoire.

**Pipeline de publication**

```
Contributeur ─► Pull Request (image + fiche YAML : titre, auteur, source, licence)
                   │
                   ▼  GitHub Actions (gratuit pour un dépôt public)
            1. contrôles : licence autorisée, résolution, doublons (hash perceptuel), NSFW
            2. génération HQ en CPU : profondeur (DA3 Mono-Large), calques, inpainting (LaMa)
            3. miniature + aperçu vidéo postés en commentaire de la PR
                   │
                   ▼  revue humaine, puis fusion
            4. publication : paquets .dwp + index.json → Cloudflare R2 (CDN)
                   │
                   ▼
            App : catalogue en cache, téléchargement à la demande
```

- **Hébergement.** Cloudflare R2 (10 Go gratuits, sortie gratuite) derrière un domaine. Un `index.json` versionné avec ETag, découpé par catégorie si le catalogue grossit.
- **Contributions.** Les développeurs passent par une Pull Request. Le grand public passe par un formulaire d'Issue GitHub (image en pièce jointe + licence), qu'un bot convertit en PR.
- **Modération.** Revue humaine de l'aperçu avant fusion, bouton « signaler » dans l'app, procédure de retrait documentée (`CONTENT_POLICY.md`).
- **Sources externes dans l'app (v0.3).** L'utilisateur parcourt Wikimedia Commons, la NASA ou des musées CC0 via leurs API publiques, et génère le fond localement pour son usage personnel. La bibliothèque s'enrichit sans rien héberger.

### 4.9 Serveur HD (optionnel)

**Principe.** Un seul conteneur Docker **sans état** (FastAPI + modèles). Il se déploie sur Modal ou RunPod (GPU serverless), sur un PC équipé d'un GPU, ou en mode CPU avec les petits modèles.

**Pipeline HD**
1. Validation (≤ 12 Mpx).
2. Upscale si l'image est petite (Real-ESRGAN).
3. Profondeur avec DA3 Mono-Large, ou Marigold v1-1 en mode « ultra ».
4. Détourage BiRefNet.
5. Inpainting LaMa.
6. Production du paquet `.dwp`.

**API** (spécification OpenAPI, client Kotlin généré)

| Méthode | Route | Rôle |
|---|---|---|
| `GET` | `/v1/capabilities` | Modèles disponibles, limites (taille max, quotas) |
| `POST` | `/v1/generate` | Image + options → paquet `.dwp` (synchrone, ≤ 60 s), ou `202` + `job_id` |
| `GET` | `/v1/jobs/{id}` | Statut, progression, lien du résultat |
| `GET` | `/v1/health` | Supervision |

**Vie privée**
- Opt-in explicite pour chaque image (« envoyer cette image au serveur HD »).
- Aucune conservation : suppression immédiate, pas de logs d'images, pas de compte.
- Politique de confidentialité publiée.
- Si le service est public, préférer un hébergement en UE, ou documenter le transfert hors UE (RGPD).

**Anti-abus**
- Jeton d'installation anonyme, quotas (par ex. 20 générations par jour) et limite par IP.
- Taille d'image maximale.
- Plafond de dépenses mensuel. Une fois atteint, le serveur renvoie « quota atteint, utilise le mode local ».
- Pas de Play Integrity dans la variante libre.

**Auto-hébergement**
- Un réglage « URL du serveur » permet à chacun d'utiliser **son propre** serveur (PC de jeu, NAS avec GPU…).
- Un notebook Colab permet aussi de générer des `.dwp` HD gratuitement.

---

## 5. Fonctionnalités et priorités

**Fond d'écran**
- **v0.1** : parallaxe au gyroscope avec recentrage automatique ; repli accéléromètre ou animation automatique ; rendu à la demande et économie d'énergie ; Material You ; réglages d'intensité, focus, zoom et qualité.
- **v0.2** : rendu à 2 calques ; modes tactile, défilement des pages et zoom du launcher ; contenu distinct à l'accueil et au verrouillage (Android 16+), avec image fixe en repli.
- **v0.3** : rotation automatique (playlist de favoris toutes les X heures).

**Créateur**
- **v0.1** : galerie → cadrage → profondeur locale → aperçu 3D → focus en touchant le sujet → enregistrer et appliquer. **Partager une photo depuis une autre app** (galerie, navigateur, messagerie) vers GlimmerView pour en faire un fond 3D *(demande du 3 octobre 2026, fait)*.
- **v0.2** : inpainting ; ~~éditeur de profondeur~~ *(fait en v0.1 : écran Esquisse, pinceaux et sélection d'une zone au toucher)* ; import d'une carte de profondeur ou d'une photo « Portrait » (~~import d'un `.dwp`~~ *fait en v0.1*) ; export vidéo MP4 en boucle. **Reprendre une œuvre existante** pour l'affiner : recadrer, relancer le calcul de profondeur, retoucher la carte *(demande du 3 octobre 2026)*.
- **v0.3** : sélection du sujet en le touchant (SAM mobile). **Édition détaillée par sujets** : choisir les personnes ou les objets sur lesquels porte la profondeur, par exemple en détectant automatiquement les personnes *(demande du 3 octobre 2026)*.
- **v0.4** : mode HD via le serveur (opt-in).
- **Plus tard** : outpainting, génération à partir d'un texte, éléments animés (eau, nuages).

**Bibliothèque**
- **v0.1** : pack de démarrage de 10 à 20 fonds.
- **v0.3** : catalogue en ligne (catégories, recherche, favoris), attribution, signalement, contributions, sources libres intégrées.

**Transverse**
- **v0.1** : français et anglais, export `.dwp`, écran des licences.
- **v0.2** : rapports de crash opt-in.
- **v1.0** : audit d'accessibilité, traductions via Weblate, publication sur F-Droid et Google Play.

---

## 6. Parcours utilisateur

1. **Accueil (premier lancement).** Trois écrans : le principe, la vie privée, puis un test du gyroscope avec aperçu en direct.
2. **Navigation principale.** Onglets **Bibliothèque**, **Créer**, **Mes fonds**, plus les Réglages.
3. **Créer.** Choisir une image, la cadrer, lancer la génération (avec progression). Vient ensuite l'aperçu 3D interactif : intensité, point focal en touchant l'image, zoom, mode de mouvement, qualité. Retouche de la profondeur en option, puis enregistrer et appliquer.
4. **Détail d'un fond.** Aperçu animé, auteur et licence, boutons Appliquer, Favori, Partager (`.dwp` ou vidéo) et Signaler.
5. **Réglages**
   - Qualité et fréquence d'affichage, comportement batterie.
   - Capteur et recentrage, réduction des mouvements.
   - Modèles d'IA (télécharger, supprimer, choix CPU/GPU).
   - Serveur HD (désactivé par défaut, URL personnalisable).
   - Langue, rapports de crash (opt-in), à propos, licences, dons.

---

## 7. Feuille de route

Durées indicatives pour un développeur seul à temps partiel (~10–15 h/semaine).

### Phase 0 : prototypes et validation (2–3 semaines)
Objectif : lever les incertitudes techniques avant d'écrire l'app.
- **Banc d'essai Python** (`tools/bench`).
  - 30 images de test variées : portraits, paysages, villes, illustrations/anime, macro, nuit, ciel, reflets, vitres.
  - Modèles comparés : DA-V2 Small, DA3 Small/Base, MoGe-2 S, DA3 Mono-Large, Marigold.
  - Livrables : planches comparatives et vidéos de parallaxe.
- **Conversion** de DA-V2 Small vers LiteRT (FP16 et INT8, en 518² et en portrait), avec vérification numérique par rapport à PyTorch.
- **Spike Android « inférence ».** LiteRT GPU/CPU contre ONNX Runtime CPU, sur 3 téléphones (entrée, milieu et haut de gamme). Mesures : latence, RAM, taille de l'APK.
- **Spike Android « fond d'écran ».** `WallpaperService` + EGL + shader + gyroscope. Mesures : temps de rendu par image, batterie sur 1 h.
- **ADR** (décisions d'architecture) : runtime, modèles, approche de rendu, minSdk, licence.
- **Critères de sortie** : profondeur en moins de 3 s sur milieu de gamme, rendu fluide à 60 i/s, surcoût batterie acceptable.

### Phase 1 : MVP v0.1 (6–8 semaines)
- **Socle** : projet Gradle multi-modules, catalogue de versions, CI GitHub Actions (build, tests, lint), licence, README, CONTRIBUTING.
- **`:render`** : chargement des textures, shader v1 (ray-marching sur 1 calque), paliers de qualité, aperçu dans l'app.
- **`:wallpaper`**
  - Service et moteur.
  - Capteurs, avec repli sans gyroscope, et recentrage.
  - Rendu à la demande et économie d'énergie.
  - Material You.
  - Application du fond via le sélecteur système.
- **`:ml`** : téléchargement des modèles (vérification SHA-256), estimation de profondeur, post-traitement (normalisation, filtre guidé, anti-halo).
- **`:format`** : lecture et écriture du `.dwp`.
- **Créateur** de bout en bout. **Mes fonds** : grille, appliquer, supprimer, partager.
- 10 à 20 fonds de démonstration (NASA, musées CC0…). Langues : français et anglais.
- **Publication** : GitHub Releases (compatible Obtainium) et demande d'inclusion sur IzzyOnDroid.
- **Critères de sortie** : un utilisateur transforme sa photo en fond 3D en moins d'une minute, sur 3 appareils de test.

### Phase 2 : qualité, v0.2 (5–6 semaines)
- Calques et inpainting MI-GAN, shader à 2 calques.
- Éditeur de profondeur.
- Imports : carte de profondeur, photo « Portrait », `.dwp` (« Ouvrir avec… »).
- Modes automatique, tactile, défilement des pages et zoom du launcher.
- Accueil et verrouillage distincts (Android 16+), avec image fixe en repli.
- Export vidéo MP4 en boucle : chaque partage sur les réseaux sociaux fait de la publicité gratuite pour l'app.
- Rapports de crash opt-in (ACRA).
- **Critères de sortie** : plus d'étirement visible sur le jeu d'images de test, à intensité par défaut.

### Phase 3 : bibliothèque en ligne, v0.3 (4–5 semaines)
- Dépôt de contenu, pipeline CI, hébergement R2 et `index.json`.
- Écrans du catalogue : catégories, recherche, favoris, détail, attribution, signalement.
- Contributions (PR et formulaire d'Issue) et charte de contenu.
- Sources libres intégrées dans l'app.
- Rotation automatique.
- Sélection du sujet en le touchant, si le temps le permet.
- **Critères de sortie** : 100 fonds publiés, tous avec une licence vérifiée.

### Phase 4 : serveur HD optionnel, v0.4 (3–4 semaines)
- Conteneur FastAPI avec pipeline HD et tests ; image Docker publiée.
- Déploiement sur Modal (crédits gratuits) avec plafond de dépenses. Guide d'auto-hébergement et notebook Colab.
- Côté app : interrupteur HD (opt-in), URL personnalisée, quotas, politique de confidentialité.
- **Critères de sortie** : un rendu HD en moins de 30 s, démarrage à froid compris.

### Phase 5 : v1.0 publique (3–4 semaines)
- Passe de performances et de batterie sur toute la matrice d'appareils ; Baseline Profiles.
- Audit d'accessibilité (TalkBack, contrastes, réduction des mouvements).
- Traductions via Weblate.
- **Publication**
  - F-Droid : variante `foss`, build reproductible.
  - Google Play : un nouveau compte personnel doit d'abord passer un test fermé avec 12 testeurs pendant 14 jours.
  - Vérification de développeur Android (voir §9).
- Site (GitHub Pages), documentation et page de dons.

### Après la v1.0 (idées)
- Outpainting côté serveur, pour étendre une photo paysage au format portrait.
- Génération d'images à partir d'un texte (FLUX.1 [schnell], Apache-2.0). Google Play exige alors un moyen de signaler les contenus générés par IA dans l'app.
- Éléments animés (eau, nuages, façon *cinemagraph*) combinés à la parallaxe.
- Mode « vraie 3D » (Gaussian splatting), si un modèle à licence libre apparaît.
- Tablettes, pliables et mode paysage ; export vers Lively ou Wallpaper Engine ; collections partagées.

---

## 8. Qualité, performances, accessibilité

**Objectifs mesurables**

| Indicateur | Cible |
|---|---|
| Profondeur, haut de gamme (GPU/NPU) | < 1 s |
| Profondeur, milieu de gamme | < 3 s |
| Profondeur, entrée de gamme (CPU) | < 15 s |
| Pipeline complet (profondeur + calques + inpainting), milieu de gamme | < 5 s |
| Rendu en mouvement, milieu de gamme | 60 i/s, < 8 ms de GPU par image |
| Rendu à l'arrêt | 0 i/s |
| Surcoût batterie par rapport à un fond statique | < 1 % par heure d'écran allumé (à mesurer) |
| RAM du process du fond d'écran (textures comprises) | < 150 Mo |
| Taille de l'APK (hors modèles) | < 20 Mo |
| Sessions sans crash | > 99,5 % |

**Tests**
- **Unitaires** : calculs du pipeline (normalisation, filtre guidé), filtre des capteurs, format `.dwp`.
- **« Golden »** : sorties du pipeline sur 20 images de référence, avec une tolérance. Ils détectent les régressions de modèles et de conversion.
- **Rendu** : tests instrumentés qui dessinent hors écran et comparent les pixels.
- **UI** : captures d'écran (Roborazzi) et tests Compose.
- **Performances** : Macrobenchmark (démarrage, défilement) et micro-benchmarks d'inférence.
- **Batterie** : protocole fixe (1 h, luminosité fixe, usage scripté) avec Perfetto ou Battery Historian, en comparaison avec un fond statique.
- **Matrice d'appareils**
  - un Pixel ;
  - un Samsung (One UI) ;
  - un Xiaomi (HyperOS, gestion batterie agressive) ;
  - un MediaTek d'entrée de gamme sans gyroscope, avec 3 Go de RAM ;
  - une tablette ou un pliable.

**Accessibilité et inclusion**
- Respect du réglage système « Supprimer les animations » (`ValueAnimator.areAnimatorsEnabled()`) : mouvement réduit, voire fond fixe.
- Curseur d'intensité et mode « doux » pour les personnes sujettes au mal des transports.
- TalkBack, tailles de police, contrastes, cibles tactiles d'au moins 48 dp.
- Français et anglais au lancement, puis traductions communautaires via Weblate (hébergement gratuit pour les projets libres).
- Fonctionne sans compte et hors ligne, dès Android 8. Les modèles se téléchargent à part, ce qui garde l'APK léger.

---

## 9. Open source et distribution

**Licences**
- **App : GPL-3.0 (retenue le 2 octobre 2026).** Tout le monde peut réutiliser et forker le code, mais les dérivés restent libres. Cela empêche les clones fermés truffés de pubs. Si la variante `full` embarque l'accélérateur GPU de LiteRT (binaire non libre), ajoute une **exception de liaison** explicite à la licence. Alternative : Apache-2.0, plus permissive (réutilisation maximale, y compris propriétaire).
- **Serveur : AGPL-3.0.** Toute version hébergée et modifiée doit publier ses sources.
- **Spécification `.dwp` : CC0.**
- **Contenus** : une licence par fond (CC0, CC BY, CC BY-SA).
- **Modèles** : liste blanche documentée dans `MODELS.md` (licence, source, empreinte SHA-256) et vérifiée en CI.

**Organisation du dépôt**
- **Documents** : README (FR/EN), CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, PRIVACY, MODELS, CONTENT_POLICY, `docs/adr/`.
- **Communauté** : modèles d'Issues et de PR, étiquettes « good first issue », GitHub Discussions.
- **CI**
  - À chaque PR : build, tests et lint.
  - À chaque tag : APK et AAB signés, sommes de contrôle, release GitHub, métadonnées Fastlane (utilisées par F-Droid).
- **Maintenance** : Renovate pour les dépendances ; builds reproductibles.
- **Dons** : Liberapay, GitHub Sponsors ou Open Collective (dépenses transparentes pour le serveur HD).

**Canaux de distribution**

| Canal | Variante | Quand | Remarques |
|---|---|---|---|
| GitHub Releases (+ Obtainium) | `full` + `foss` | Dès v0.1 | |
| IzzyOnDroid | `foss` | v0.1–v0.2 | Distribue l'APK signé par toi, inclusion rapide |
| F-Droid | `foss` | v1.0 | Avec un build reproductible, F-Droid publie l'APK signé avec ta clé, ce qui simplifie la vérification Google. F-Droid 2.0 est sorti le 24/09/2026. |
| Google Play | `full` | v1.0 | 25 $ une fois ; test fermé (12 testeurs, 14 jours) pour un nouveau compte personnel ; targetSdk 36 |

**Vérification des développeurs Android**
- Depuis le 30/09/2026, au Brésil, en Indonésie, à Singapour et en Thaïlande, seules les apps d'un développeur vérifié s'installent sur les appareils Android certifiés, y compris hors Google Play. Le déploiement mondial est prévu pour 2027.
- **Action :** vérifier ton identité et enregistrer le nom de package dès que l'app devient publique.
- Le compte gratuit « distribution limitée » (sans pièce d'identité) ne couvre que 20 appareils. Il ne sert donc que pour les tests.
- Les utilisateurs peuvent aussi passer par le « parcours avancé » pour installer une app non vérifiée.

---

## 10. Budget

| Poste | Coût |
|---|---|
| Outils (Android Studio, GitHub, CI d'un dépôt public) | 0 € |
| Compte Google Play | 25 $ (une seule fois) |
| Hébergement de la bibliothèque (Cloudflare R2 : 10 Go gratuits, sortie gratuite) | 0 € au départ |
| Traductions (Hosted Weblate, gratuit pour les projets libres) | 0 € |
| Serveur HD (Modal, 30 $/mois de crédits gratuits) | 0 € jusqu'à ~10 000 générations/mois, puis ~0,001–0,002 $ par génération |
| Nom de domaine (optionnel) | ~10–15 €/an |
| Téléphones de test | Ceux que tu as, plus un modèle d'entrée de gamme d'occasion sans gyroscope (~50–80 €) |

---

## 11. Risques et parades

| Risque | Probabilité / impact | Parade |
|---|---|---|
| Consommation batterie jugée excessive | Moyenne / élevé | Rendu à la demande, 0 i/s à l'arrêt, `setFrameRate`, mode économie, mesures dès la Phase 0 |
| Lenteurs ou plantages sur téléphones modestes (RAM, GPU non compatible, pas de gyroscope) | Élevée / moyen | Paliers de qualité, repli CPU, accéléromètre ou animation automatique, décodage sous-échantillonné, serveur optionnel |
| Particularités des constructeurs (écran de verrouillage, process tués…) | Élevée / moyen | Matrice de tests, repli en image fixe, guide du type dontkillmyapp |
| Conversion des modèles (opérations non supportées sur GPU) | Moyenne / moyen | Modèles déjà convertis (litert-community, Qualcomm AI Hub), ONNX Runtime en plan B |
| Licences de modèles non commerciales | Moyenne / élevé | Liste blanche et contrôle en CI |
| Droits d'auteur des images de la bibliothèque | Moyenne / élevé | Sources sûres uniquement, métadonnées obligatoires, procédure de retrait |
| Coût ou abus du serveur | Moyenne / moyen | Local d'abord, quotas, plafond de dépenses, auto-hébergement |
| Vérification des développeurs Google, politiques Play (contenu répétitif, fonctionnalité minimale) | Moyenne / moyen | Vérification anticipée, F-Droid et IzzyOnDroid, fonctionnalité forte (la création de fonds) |
| Dérive du périmètre | Élevée / élevé | MVP strict, phases avec critères de sortie |
| Rendu « faux » sur certaines images (vitres, reflets, ciel, illustrations plates) | Élevée / faible | Éditeur de profondeur, réglages, modèle serveur plus fin |

---

## 12. Décisions à prendre

1. **Nom de l'app** : ✅ **GlimmerView**, slogan « Subtle shifts, stunning screens. ».
   **Nom de package** : ✅ **`app.glimmerview`** (3 octobre 2026). Il ne changera plus, car il est lié à la clé de signature et à la vérification Google.
2. **Licence** : ✅ **GPL-3.0**. Reste à préciser plus tard : « GPL-3.0 only » ou « GPL-3.0 or later ».
3. **Runtime IA** : ✅ **ONNX Runtime**, décidé après les mesures de la Phase 0 ([ADR 0001](docs/adr/0001-runtime-ia.md)).
4. **Serveur public.** Pas au lancement *(recommandé)* : d'abord auto-hébergeable, puis public seulement s'il est financé.
5. **Version minimale.** minSdk 26 *(recommandé)*.
6. **Canaux.** GitHub et IzzyOnDroid d'abord, puis F-Droid et Google Play à la v1.0 *(recommandé)*.
7. **Temps disponible par semaine**, pour recalibrer la feuille de route.

---

## 13. Prochaines étapes

**Fait.** Nom (GlimmerView) et licence (GPL-3.0) ; Phase 0 (rendu, fond d'écran, inférence mesurée sur appareil, [ADR 0001](docs/adr/0001-runtime-ia.md)) ; charte « Galerie » validée (Claude Design, typo Spectral) ; début de la Phase 1 (3 octobre 2026) :

- LiteRT retiré, modèles vérifiés par SHA-256 ;
- interface Compose complète selon la charte : vernissage, galerie, œuvre, accrochage, collection, préférences, atelier, tirage, mise au point ;
- parcours de bout en bout validé sur émulateur : photo → profondeur → aperçu 3D → fond d'écran ;
- œuvres de démonstration : sept scènes dessinées en plans (profondeur exacte, CC0) ;
- préférences lues par le fond d'écran (recentrage, mouvements réduits, cadence, économie d'énergie) ;
- français et anglais ;
- dépôt GitHub `cocoke/GlimmerView` (privé pour l'instant), CI GitHub Actions (compilation, tests, lint) et `CONTRIBUTING.md` ;
- partage d'une image depuis une autre app (menu « Partager » d'Android) vers le tirage ;
- titre donné à l'œuvre à la fin du tirage, renommage depuis la mise au point ;
- retouche de la profondeur (Esquisse) : pinceaux avancer, reculer, lisser, aplatir et gommer, ou sélection d'une zone au toucher (personne, objet) qu'on avance ou recule d'un geste ; annuler et rétablir ; la carte d'origine est conservée ;
- appui long sur une œuvre (galerie, collection, dernières créations) : menu pour la voir, régler l'effet, retoucher la profondeur, la renommer, l'accrocher, l'exporter, l'ajouter à la collection, l'en retirer ou la supprimer (en deux touchers) ;
- format `.dwp` (module `:format`, [spécification](docs/format.md) en CC0) : export d'une œuvre (partager, enregistrer sur le téléphone) et import depuis l'atelier, « Ouvrir avec » ou le menu Partager d'Android ; auteur et licence conservés, garde-fous contre les fichiers piégés ;
- pack de démarrage : 31 œuvres recadrées au format d'écran, profondeur précalculée sur PC avec le modèle de l'app en plus haute résolution ([tools/pack](tools/pack/README.md)) : douze œuvres du domaine public (Met en CC0, NASA) et dix-neuf photos de paysages, de villes et d'espace (Wikimedia Commons, surtout CC BY-SA), à côté des sept scènes dessinées ; galerie en sept salles ;
- bibliothèque téléchargeable ([docs/library.md](docs/library.md)) : index, aperçus et paquets servis par GitHub Pages (branche `gh-pages`) ; l'APK n'embarque que l'index, les aperçus et quatre œuvres (6 Mo au lieu de 37), les autres se téléchargent à l'ouverture, vérifiées par SHA-256 ; place libérable dans les préférences ;
- accrochage : le maintien remplit tout le bouton, visible même sous le doigt.

**Reste pour la v0.1.**

1. Tests sur trois téléphones (entrée, milieu, haut de gamme), dont l'aperçu et le fond d'écran au gyroscope.
2. Publication : GitHub Releases et IzzyOnDroid.

---

## Sources

- Depth Anything 3 (modèles et licences) : https://github.com/ByteDance-Seed/Depth-Anything-3
- Depth Anything V2 : https://github.com/DepthAnything/Depth-Anything-V2
- Depth Anything V2 optimisé mobile (Qualcomm AI Hub, benchmarks) : https://huggingface.co/qualcomm/Depth-Anything-V2
- MoGe-2 pour LiteRT (benchmarks) : https://huggingface.co/litert-community/MoGe-2-LiteRT
- MI-GAN pour LiteRT (benchmarks) : https://huggingface.co/litert-community/MI-GAN-512-Places2-LiteRT
- MI-GAN : https://github.com/Picsart-AI-Research/MI-GAN
- Marigold : https://github.com/prs-eth/marigold
- Apple SHARP : https://github.com/apple/ml-sharp
- DepthFlow : https://github.com/BrokenSource/DepthFlow
- LiteRT, annonce : https://developers.googleblog.com/en/litert-the-universal-framework-for-on-device-ai/
- LiteRT sur Android : https://developers.google.com/edge/litert/android
- LiteRT, accélérateur GPU : https://developers.google.com/edge/litert/next/gpu
- TensorFlow Lite compilé pour F-Droid : https://central.sonatype.com/artifact/de.schliweb/tensorflow-lite-fdroid
- ONNX Runtime, fournisseur d'exécution QNN : https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html
- NNAPI déprécié, guide de migration : https://developer.android.com/ndk/guides/neuralnetworks/migration-guide
- ExecuTorch sur Android : https://docs.pytorch.org/executorch/stable/using-executorch-android.html
- Arm, Depth Anything V2 sur Android avec ExecuTorch : https://learn.arm.com/learning-paths/mobile-graphics-and-gaming/run-depth-anything-v2-on-android/
- Android 16, contenus des fonds d'écran animés : https://developer.android.com/about/versions/16/features
- Google Play, exigences de targetSdk : https://developer.android.com/google/play/requirements/target-sdk
- Vérification des développeurs Android, FAQ : https://developer.android.com/developer-verification/guides/faq
- Calendrier de la vérification : https://pasqualepillitteri.it/en/news/12067/keep-android-open-google-developer-verification-sideloading
- F-Droid 2.0 : https://pinggy.io/blog/f_droid_2_0_android_developer_verification/
- Règles d'utilisation Pexels : https://help.pexels.com/hc/en-us/articles/360042332714-What-are-the-rules-for-using-Pexels-photos-or-videos
- Licence Pixabay : https://pixabay.com/en/service/license/
- Licence Unsplash : https://unsplash.com/license
- Tarifs Modal : https://modal.com/pricing
- GPU des Spaces Hugging Face : https://huggingface.co/docs/hub/main/spaces-gpus
