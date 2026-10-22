# Nuance

Nuance filtre en temps réel l'image de la caméra pour aider les personnes daltoniennes (protan,
deutan, tritan, formes totales ou partielles) à distinguer les couleurs et les contrastes au
quotidien. Tu pointes le téléphone vers une scène, tu vois à l'écran une version corrigée, et tu
touches un point pour connaître sa couleur.

## Installer

Sur un téléphone **Android 10 ou plus récent** :

1. Ouvre [la dernière version](https://github.com/j3ffx/nuance/releases/latest) et télécharge le
   fichier `nuance-x.y.z.apk` (dans « Assets »).
2. Ouvre-le. Android demande d'autoriser ton navigateur à installer des applis : accepte, puis
   « Installer ». L'appli ne vient pas du Play Store : Play Protect peut afficher un avertissement.
3. Au premier lancement, choisis ton type de daltonisme, ou fais le test « Trouver mon profil ».

Pour les versions suivantes, active « Vérifier les mises à jour » (proposé au premier lancement, ou
dans les réglages) : l'appli te propose la nouvelle version et l'installe. Le fichier `.sha256` à
côté de l'APK permet de vérifier qu'il n'a pas été altéré.

## Ce que fait Nuance

- **Corriger** les couleurs selon ton profil (type et sévérité), avec une intensité réglable.
- **Isoler une teinte** : « montre-moi seulement le rouge », le reste passe en gris.
- **Simuler** et **Comparer** : voir ce que perçoit une personne daltonienne, et ce qu'elle perçoit
  de l'image corrigée, en quatre ou deux vues côte à côte. Utile pour les proches.
- **Trouver mon profil** : un test d'orientation de 3 minutes (planches de points générées par
  l'appli, rangement de pastilles, carrés à comparer).
- **Pipette** : touche un point de l'image pour connaître le nom de sa couleur, son code, et comment
  ton profil la perçoit.
- **Figer l'image** pour la regarder tranquillement, zoomer et se déplacer dedans ; zoom de la
  caméra, lampe, caméra avant.
- **Photos et vidéos** filtrées dans l'album « Nuance » de la galerie (avec l'original si tu veux),
  et les mêmes filtres sur une **photo de ta galerie**.
- **Profils** enregistrés et partageables en texte court, interface en français et en anglais,
  thème sombre ou clair. Aucune information n'est donnée par la couleur seule.

## Ce que Nuance ne fait pas

- **Rien ne quitte le téléphone.** Aucun traitement dans le cloud, aucune statistique d'usage,
  aucun compte. Les photos et vidéos n'ont pas de position. Le seul accès réseau, désactivé par
  défaut, vérifie s'il existe une nouvelle version sur GitHub.
- **Ce n'est pas un dispositif médical.** Le test donne une orientation, pas un diagnostic : seul un
  examen chez un ophtalmologiste ou un orthoptiste en donne un. Nuance ne « guérit » rien.

## Plateformes

Android 10 ou plus récent. iOS 17 ou plus récent viendra ensuite : le code est partagé (Kotlin
Multiplatform, Compose Multiplatform), seuls la caméra et le rendu GPU sont propres à chaque système.

Les nouveautés de chaque version sont dans [`CHANGELOG.md`](CHANGELOG.md), le fonctionnement des
filtres et du test (modèles, sources, mesures) dans [`docs/ALGORITHMS.md`](docs/ALGORITHMS.md).

## Contribuer

Les notes de contribution (en anglais) sont dans [`CLAUDE.md`](CLAUDE.md), l'installation des outils
dans [`docs/SETUP.md`](docs/SETUP.md).

## Licence

[GPL-3.0-only](LICENSE).
