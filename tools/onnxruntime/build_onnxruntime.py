"""Construit l'ONNX Runtime sur mesure de GlimmerView : un paquet Android (AAR) sans télémétrie,
réduit aux opérateurs des modèles de profondeur de l'app, sans accélérateur inutilisé.

Par rapport au paquet officiel (onnxruntime-android sur Maven Central) :
  - --no_telemetry : la télémétrie Microsoft (1DS) n'est pas compilée, ni côté natif ni côté Java ;
  - --include_ops_by_config : seulement les opérateurs de glimmerview_ops.config ;
  - --disable_ml_ops : sans les opérateurs d'apprentissage automatique classique ;
  - ni NNAPI, ni XNNPACK, ni WebGPU (l'app calcule sur le CPU, voir docs/adr/0001-runtime-ia.md).

Le script suit tools/ci_build/github/android/build_aar_package.py d'ONNX Runtime, avec deux écarts
pour Windows : les dépendances viennent de CMake (FetchContent) plutôt que du cache vcpkg interne de
Microsoft, et les bibliothèques sont copiées plutôt que liées symboliquement.

Usage (voir README.md pour les prérequis) :
  python tools/onnxruntime/build_onnxruntime.py --work_dir D:/onnxruntime-build [--abis arm64-v8a]
"""

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ORT_VERSION = "1.31.0"
ORT_REPO = "https://github.com/microsoft/onnxruntime.git"
ABIS = ["arm64-v8a", "armeabi-v7a", "x86_64"]
MIN_SDK = 26
TARGET_SDK = 34

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
# Dépôt Maven local du projet : l'AAR y est publié comme une dépendance ordinaire.
MAVEN_DIR = REPO / "third_party" / "maven"
LICENSES_DIR = REPO / "app" / "src" / "main" / "assets" / "licenses"


def run(command, cwd=None, env=None):
    print("+", " ".join(str(part) for part in command), flush=True)
    subprocess.run([str(part) for part in command], cwd=cwd, env=env, check=True)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--work_dir", type=Path, required=True, help="Dossier de travail, sans espace (source et compilation)")
    parser.add_argument("--android_sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", "")))
    parser.add_argument("--android_ndk", type=Path, default=Path(os.environ.get("ANDROID_NDK_HOME", "")))
    parser.add_argument("--abis", nargs="+", default=ABIS, choices=ABIS)
    parser.add_argument("--skip_native", action="store_true", help="Réutiliser les bibliothèques déjà compilées")
    args = parser.parse_args()

    work = args.work_dir.resolve()
    if " " in str(work):
        sys.exit("Le dossier de travail ne doit pas contenir d'espace (outils de compilation natifs).")
    ort = work / "onnxruntime"
    if not ort.exists():
        run(["git", "clone", "--depth", "1", "--branch", f"v{ORT_VERSION}", ORT_REPO, ort])
    if (ort / "VERSION_NUMBER").read_text().strip() != ORT_VERSION:
        sys.exit(f"{ort} n'est pas ONNX Runtime {ORT_VERSION}")

    # Configuration sans commentaires (le lecteur d'ONNX Runtime n'attend que de l'ASCII).
    ops = work / "glimmerview_ops.config"
    lines = (HERE / "glimmerview_ops.config").read_text(encoding="utf-8").splitlines()
    ops.write_text("\n".join(line for line in lines if line and not line.startswith("#")) + "\n", encoding="ascii")

    build_root = work / "build"
    jnilibs = build_root / "jnilibs"
    env = dict(os.environ, ANDROID_HOME=str(args.android_sdk), ANDROID_NDK_HOME=str(args.android_ndk))
    # CMake et Ninja de l'environnement Python qui lance ce script (venv conseillé).
    env["PATH"] = str(Path(sys.executable).parent) + os.pathsep + env["PATH"]

    for abi in args.abis:
        abi_dir = build_root / abi
        if not args.skip_native:
            run(
                [
                    sys.executable, ort / "tools" / "ci_build" / "build.py",
                    "--android", f"--android_abi={abi}", f"--android_api={MIN_SDK}",
                    f"--android_sdk_path={args.android_sdk}", f"--android_ndk_path={args.android_ndk}",
                    "--config=Release", f"--build_dir={abi_dir}",
                    "--parallel", "--cmake_generator=Ninja", "--enable_lto",
                    "--build_java", "--build_shared_lib", "--skip_tests", "--skip_submodule_sync",
                    "--no_telemetry", "--disable_ml_ops", f"--include_ops_by_config={ops}",
                    # Sans les tests d'ONNX Runtime (certains ne compilent pas en build réduite). Sans eux,
                    # des constantes de KleidiAI réservées aux tests restent inutilisées : avertissements
                    # laissés comme tels, pas en erreurs.
                    "--cmake_extra_defines=onnxruntime_BUILD_UNIT_TESTS=OFF", "--compile_no_warning_as_error",
                ],
                cwd=ort,
                env=env,
            )
        target = jnilibs / abi
        target.mkdir(parents=True, exist_ok=True)
        for lib in ("libonnxruntime.so", "libonnxruntime4j_jni.so"):
            shutil.copyfile(abi_dir / "Release" / lib, target / lib)

    # Assemblage de l'AAR par le Gradle des liaisons Java d'ONNX Runtime, comme le script officiel.
    headers = build_root / args.abis[0] / "Release" / "android" / "headers"
    publish = build_root / "aar_out"
    gradlew = ort / "java" / ("gradlew.bat" if os.name == "nt" else "gradlew")
    gradle = [
        gradlew, "--no-daemon", "-b=build-android.gradle", "-c=settings-android.gradle",
        f"-DjniLibsDir={jnilibs}", f"-DbuildDir={build_root / 'aar'}", f"-DheadersDir={headers}",
        f"-DpublishDir={publish}", f"-DminSdkVer={MIN_SDK}", f"-DtargetSdkVer={TARGET_SDK}",
        "-DENABLE_TRAINING_APIS=0", "-DreleaseVersionSuffix=",
    ]
    for task in ("clean", "build", "publish"):
        run([*gradle, task], cwd=ort / "java", env=env)

    aar = next(publish.rglob(f"onnxruntime-android-{ORT_VERSION}.aar"))
    pom = next(publish.rglob(f"onnxruntime-android-{ORT_VERSION}.pom"))
    # Publié sous un groupe à nous : impossible de le confondre avec le paquet officiel.
    dest = MAVEN_DIR / "app" / "glimmerview" / "thirdparty" / "onnxruntime-android" / ORT_VERSION
    dest.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(aar, dest / f"onnxruntime-android-{ORT_VERSION}.aar")
    text = pom.read_text(encoding="utf-8").replace("<groupId>com.microsoft.onnxruntime</groupId>", "<groupId>app.glimmerview.thirdparty</groupId>")
    text = re.sub(
        r"<description>.*?</description>",
        "<description>ONNX Runtime built for GlimmerView: no telemetry, CPU only, operators reduced to the "
        "app's depth models (tools/onnxruntime).</description>",
        text,
        flags=re.DOTALL,
    )
    (dest / f"onnxruntime-android-{ORT_VERSION}.pom").write_text(text, encoding="utf-8")
    # Licence MIT d'ONNX Runtime et notices des bibliothèques compilées avec lui : à côté de l'AAR
    # et dans l'APK (assets/licenses).
    for source, name in (("LICENSE", "ONNXRuntime-LICENSE.txt"), ("ThirdPartyNotices.txt", "ONNXRuntime-ThirdPartyNotices.txt")):
        for folder in (dest, LICENSES_DIR):
            shutil.copyfile(ort / source, folder / name)
    print(f"AAR : {dest} ({aar.stat().st_size / 1e6:.1f} Mo, SHA-256 {sha256(dest / f'onnxruntime-android-{ORT_VERSION}.aar')})")


if __name__ == "__main__":
    main()
