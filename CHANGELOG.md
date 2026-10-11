# Journal des versions

## À venir

- ONNX Runtime 1.31, compilé pour GlimmerView depuis ses sources ([tools/onnxruntime](tools/onnxruntime/README.md)) :
  la télémétrie Microsoft n'est plus seulement désactivée, elle est absente de l'app. Seuls les
  opérateurs des modèles de profondeur et le calcul sur processeur sont gardés : 13,9 Mo de code natif
  en arm64 au lieu de 33 Mo.

## 0.1.1 — 10 octobre 2026 (pré-version)

- Correctif : à la première ouverture de l'app, l'aperçu 3D d'une œuvre restait figé sur certains téléphones (Samsung) tant qu'on n'était pas passé par « Régler l'effet ». Le moteur 3D est maintenant posé dès l'ouverture de l'écran, et la vignette s'efface à sa première image.

## 0.1.0 — 10 octobre 2026 (pré-version)

Première version publique de GlimmerView : des fonds d'écran en relief qui suivent les mouvements du
téléphone, et la création de ses propres œuvres à partir d'une photo, sans rien envoyer en ligne.

*First public release: 3D parallax live wallpapers, and on-device creation from your own photos.*

### La galerie
- 38 œuvres en sept salles (montagnes, eaux, nature, espace, villes, art) : photos de Wikimedia Commons,
  estampes et peintures du domaine public (Met, CC0), images de la NASA et du télescope James-Webb,
  et sept scènes dessinées en plans.
- Bibliothèque en ligne : seules quatre œuvres sont dans l'APK, les autres se téléchargent à
  l'ouverture (environ 1 Mo chacune, empreinte vérifiée) ; les nouvelles œuvres arrivent sans mise à jour.
- Appui long sur une œuvre : la voir, régler l'effet, retoucher la profondeur, renommer, accrocher,
  exporter, ajouter à la collection ou retirer.

### Créer ses œuvres
- Une photo de la galerie du téléphone ou partagée depuis une autre app devient une œuvre 3D : la
  profondeur est calculée sur le téléphone (Depth Anything V2 Small, 27 Mo téléchargés une seule fois).
- Mise au point : intensité de l'effet (« diaphragme ») et plan immobile, en touchant le sujet.
- Retouche de la profondeur (« Esquisse ») : pinceaux et sélection d'une personne ou d'un objet au toucher.

### Accrocher
- Fond d'écran animé sur l'accueil, image fixe sur l'écran de verrouillage, ou les deux.
- Recentrage automatique, réduction des mouvements, cadence 30 ou 60 i/s, pause en économie d'énergie.

### Partager
- Format ouvert `.dwp` (spécification CC0) : exporter une œuvre, la partager, l'enregistrer, et ouvrir
  celles qu'on reçoit.

### Vie privée
- Aucun compte, aucune publicité, aucun traceur. Les photos ne quittent pas le téléphone.
- La télémétrie que la bibliothèque ONNX Runtime embarque (vers Microsoft) est désactivée et son
  lanceur retiré de l'app. Les seules connexions servent à télécharger le modèle et les œuvres de la galerie.

### Installation
- `GlimmerView-0.1.0-arm64-v8a.apk` pour presque tous les téléphones récents ;
  `armeabi-v7a` pour les téléphones 32 bits plus anciens ; `x86_64` pour les émulateurs et Chromebooks.
- Android 8.0 ou plus récent.
- Empreinte SHA-256 du certificat de signature :
  `fd:ca:33:14:7c:80:35:4f:4c:91:f6:22:54:bb:50:2a:b5:b1:fb:0e:91:91:8f:94:e4:25:8d:92:81:60:47:ab`

### Limites connues
- Testée sur émulateur et sur un Galaxy S26 Ultra seulement : d'où le statut de pré-version.
- Les fonds sur un seul calque peuvent étirer les bords des objets quand l'effet est intense.
