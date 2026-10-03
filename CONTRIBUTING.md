# Contribuer à GlimmerView

Merci de ton intérêt ! Les contributions sont bienvenues, en français comme en anglais.

*Contributions are welcome in French or English. Code identifiers are in English; comments, docs and commit messages may be in either language.*

## Avant de commencer

- Pour un changement important (nouvel écran, nouvelle dépendance, nouveau modèle d'IA), ouvre d'abord une issue : on en discute avant d'écrire le code.
- Le [plan](PLAN.md) décrit la feuille de route ; les décisions d'architecture sont consignées dans [`docs/adr`](docs/adr).

## Compiler et tester

Prérequis : JDK 17 ou plus récent, SDK Android avec la plateforme 37, Android Studio récent (compatible AGP 9.4).

```bash
./gradlew assembleDebug        # APK de débogage
./gradlew testDebugUnitTest    # tests unitaires
./gradlew lintDebug            # analyse Android Lint
./gradlew connectedDebugAndroidTest  # tests sur appareil ou émulateur (module render)
```

La CI GitHub Actions lance les trois premières commandes sur chaque pull request : elles doivent passer.

## Règles du projet

**Liberté et vie privée**
- Aucune dépendance non libre : pas de Google Play Services, de Firebase ni de traceur. L'app doit rester publiable sur F-Droid.
- Les photos ne quittent jamais le téléphone sans un accord explicite (serveur HD, plus tard).
- Modèles d'IA : licences libres uniquement (pas de CC BY-NC). Chaque modèle du catalogue a son empreinte SHA-256.

**Code**
- Kotlin, style officiel (`kotlin.code.style=official`). Identifiants en anglais ; commentaires et KDoc en français, courts, pour expliquer le *pourquoi*.
- Le process du fond d'écran (`:wallpaper`) reste léger : ni Compose, ni runtime d'IA.

**Interface**
- La charte « Galerie » fait foi : murs plâtre, encre, un seul accent bleu de Prusse ; Spectral pour les titres et le texte, Hanken Grotesk en capitales espacées pour les cartels.
- Réutilise les composants de `app/.../ui/components` (cartel, tirette, interrupteur à fil, cadre vivant…) plutôt que des boutons, curseurs ou indicateurs Material standards.
- Tous les textes passent par les ressources : `values` (anglais) et `values-fr` (français). En français : apostrophe typographique (’) et espace insécable avant `?`, `!` et `:`.
- Pense à l'accessibilité : descriptions pour TalkBack, cibles d'au moins 44 dp, respect de « Réduire les mouvements ».

## Commits et pull requests

- Un commit = un changement cohérent, avec un message qui dit ce qui change et pourquoi.
- Une pull request ne mélange pas refonte et nouvelle fonctionnalité.
- En contribuant, tu acceptes que ton code soit distribué sous la [licence du projet](LICENSE) (GNU GPL v3).

## Proposer des œuvres pour la bibliothèque

- Uniquement des images au statut clair : CC0, domaine public, CC BY ou CC BY-SA (avec l'attribution demandée).
- Jamais d'images issues d'Unsplash, Pexels ou Pixabay : leurs licences interdisent ce type de réutilisation.
- Indique pour chaque image son titre, son auteur, sa source et sa licence, et signale les images générées par une IA.
