# ONNX Runtime sur mesure

GlimmerView n'utilise pas le paquet officiel `com.microsoft.onnxruntime:onnxruntime-android`, mais
une version compilée pour elle, publiée dans le dépôt Maven du projet
(`third_party/maven/app/glimmerview/thirdparty/onnxruntime-android/`).

## Pourquoi

| | Paquet officiel 1.31 | Paquet GlimmerView 1.31 |
|---|---|---|
| Télémétrie Microsoft (1DS) | compilée et lancée au démarrage de l'app (identifiant Android, état du réseau et de la batterie) | **absente** (`--no_telemetry`) |
| Opérateurs | tous | ceux des modèles de l'app (`glimmerview_ops.config`) |
| Accélérateurs | NNAPI, XNNPACK, WebGPU | aucun : le CPU seul (MLAS, KleidiAI sur arm64), comme l'app l'utilise |
| Architectures | arm64-v8a, armeabi-v7a, x86, x86_64 | arm64-v8a, armeabi-v7a, x86_64 |

L'app reste ainsi sans traceur, et ses APK beaucoup plus légers :

| `libonnxruntime.so` | Paquet officiel 1.30 | Paquet GlimmerView 1.31 |
|---|---|---|
| arm64-v8a | 33,0 Mo | 13,9 Mo |
| armeabi-v7a | 23,3 Mo | 9,1 Mo |
| x86_64 | 39,3 Mo | 15,8 Mo |
| AAR complet | 53,0 Mo | 16,3 Mo |

AAR versionné : SHA-256 `ff7bfb39b9c220ae16fab98bdee7151a0fe1b7bc0e55841a4f6afa8bcf60c5d0`. Une
reconstruction donne le même contenu fonctionnel, pas forcément le même fichier à l'octet près.

## Reconstruire

Prérequis : Python 3.10+, JDK 17, SDK Android et NDK 27 ; Windows ou Linux. Le dossier de travail
(source d'ONNX Runtime et compilation, environ 15 Go) ne doit pas contenir d'espace.

```bash
python -m venv D:/onnxruntime-build/venv
D:/onnxruntime-build/venv/Scripts/python -m pip install "cmake>=3.28,<4" ninja
D:/onnxruntime-build/venv/Scripts/python tools/onnxruntime/build_onnxruntime.py --work_dir D:/onnxruntime-build --android_sdk <SDK> --android_ndk <SDK>/ndk/27.1.12297006
```

Le script clone ONNX Runtime à la version voulue, compile chaque architecture (environ une demi-heure
chacune), assemble l'AAR avec le Gradle des liaisons Java d'ONNX Runtime, puis le publie dans
`third_party/maven` sous le groupe `app.glimmerview.thirdparty`, avec la licence MIT d'ONNX Runtime et
les notices des bibliothèques compilées avec lui (copiées aussi dans `app/src/main/assets/licenses`,
donc dans l'APK). Les dépendances d'ONNX Runtime sont
téléchargées depuis GitHub à la première compilation de chaque architecture : en cas d'échec réseau,
relancer simplement le script (ce qui est déjà compilé n'est pas refait).

Les tests d'ONNX Runtime ne sont pas compilés, et ses avertissements ne bloquent pas la compilation :
en 1.31, sans les tests, une constante de KleidiAI devient inutilisée et `-Werror` l'arrêterait.

## Mettre à jour

1. Changer `ORT_VERSION` dans `build_onnxruntime.py` et `onnxruntime` dans `gradle/libs.versions.toml`.
2. Régénérer la liste des opérateurs si les modèles ou la version changent : enregistrer les modèles
   optimisés par ONNX Runtime (niveaux basic, extended et all, `SessionOptions.optimized_model_filepath`),
   puis `python tools/python/create_reduced_build_config.py --format ONNX <dossier des modèles> glimmerview_ops.config`
   (script du dépôt d'ONNX Runtime). Les fusions faites au chargement doivent trouver leurs noyaux.
3. Vérifier que la télémétrie reste absente : pas de `ai.onnxruntime.TelemetryInitializer` dans l'AAR,
   pas de `events.data.microsoft.com` dans `libonnxruntime.so`.
4. Tester une création sur l'émulateur et sur un téléphone arm64.
