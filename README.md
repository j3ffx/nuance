# Nuance

Nuance filtre en temps réel l'image de la caméra pour aider les personnes daltoniennes (protan,
deutan, tritan, formes totales ou partielles) à distinguer les couleurs et les contrastes au
quotidien. Tu pointes le téléphone vers une scène, tu vois à l'écran une version corrigée, et tu
touches un point pour connaître sa couleur.

> **En construction.** Rien n'est encore utilisable : les premières versions arriveront sur la page
> [Releases](https://github.com/j3ffx/nuance/releases).

## Ce que fait Nuance

- **Corriger** les couleurs selon ton profil (type et sévérité), avec une intensité réglable.
- **Isoler une teinte** : « montre-moi seulement le rouge », le reste passe en gris.
- **Pour les proches** : voir ce que perçoit une personne daltonienne, et ce qu'elle perçoit de
  l'image corrigée.
- **Trouver mon profil** : un test d'orientation intégré, généré par l'appli.
- **Pipette** : le nom de la couleur touchée, son code, et comment ton profil la perçoit.

## Ce que Nuance ne fait pas

- **Rien ne quitte le téléphone.** Aucun traitement dans le cloud, aucune statistique d'usage,
  aucun compte. Le seul accès réseau, désactivé par défaut, vérifie s'il existe une nouvelle version.
- **Ce n'est pas un dispositif médical.** Le test donne une orientation, pas un diagnostic : seul un
  examen chez un ophtalmologiste ou un orthoptiste en donne un. Nuance ne « guérit » rien.

## Plateformes

Android 10 ou plus récent. iOS 17 ou plus récent viendra ensuite : le code est partagé (Kotlin
Multiplatform, Compose Multiplatform), seuls la caméra et le rendu GPU sont propres à chaque système.

## Contribuer

Les notes de contribution (en anglais) sont dans [`CLAUDE.md`](CLAUDE.md), l'installation des outils
dans [`docs/SETUP.md`](docs/SETUP.md).

## Licence

[GPL-3.0-only](LICENSE).
