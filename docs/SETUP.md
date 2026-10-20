# Installation et outils

Tout se fait en ligne de commande, sans Android Studio. Sous Windows, les outils s'installent avec
[Scoop](https://scoop.sh).

## Outils

| Outil                                     | Pourquoi                      | Installation (Windows)                                     |
| ----------------------------------------- | ----------------------------- | ---------------------------------------------------------- |
| JDK 21 (Temurin)                          | Gradle et Kotlin              | `scoop bucket add java` puis `scoop install temurin21-jdk` |
| SDK Android (outils en ligne de commande) | compiler et installer l'appli | voir ci-dessous                                            |
| Node 22                                   | scripts du dépôt, hooks git   | `scoop install nodejs-lts`                                 |
| gitleaks                                  | refuse de commiter un secret  | `scoop install gitleaks`                                   |
| GitHub CLI                                | dépôt, releases               | `scoop install gh`                                         |

Gradle n'a pas besoin d'être installé : le dépôt fournit son wrapper (`gradlew`).

### SDK Android

1. Télécharge les « Command line tools only » sur
   [developer.android.com/studio](https://developer.android.com/studio#command-line-tools-only) et
   décompresse-les dans `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`.
2. Définis la variable d'environnement utilisateur `ANDROID_HOME` vers `%LOCALAPPDATA%\Android\Sdk`
   (la valeur doit être un vrai chemin, `C:\…`, pas le texte `%LOCALAPPDATA%`).
3. Installe la plateforme, les build-tools et ADB :
   `sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"`, puis accepte les
   licences avec `sdkmanager --licenses`.

Si `ANDROID_HOME` manque, crée un fichier `local.properties` à la racine du dépôt (il n'est jamais
versionné) avec `sdk.dir=C:/chemin/vers/Android/Sdk`.

### Premier lancement du dépôt

```
npm install
npm test
```

## Téléphone : ADB sans fil

L'appli s'installe sur le téléphone par le Wi-Fi, sans câble. L'appairage se fait **une seule fois**
par téléphone.

1. Sur le téléphone : **Paramètres → À propos du téléphone → Informations sur le logiciel**, appuie
   7 fois sur **Numéro de version** pour activer les Options de développement.
2. **Paramètres → Options de développement → Débogage sans fil** : active-le (téléphone et PC sur le
   même Wi-Fi).
3. Appuie sur **Associer l'appareil avec un code d'association**, puis sur le PC :
   `adb pair <ip>:<port> <code>` avec l'adresse et le code affichés.

Ensuite, `npm run phone` retrouve le téléphone tout seul (mDNS), compile la variante dev, l'installe
et la lance. Si plusieurs téléphones sont associés, ajoute dans `local.properties` la ligne
`nuance.phone=<numéro de série>` (`adb devices -l` ou `adb shell getprop ro.serialno`) pour choisir
celui des tests. Si le téléphone a changé de réseau et n'est pas trouvé, passe l'adresse affichée
sous **Débogage sans fil** : `npm run phone -- --connect <ip>:<port>`.

Le débogage sans fil se coupe parfois tout seul (redémarrage, changement de Wi-Fi) : il suffit de le
réactiver, sans nouvel appairage.

## Variantes

- **Nuance dev** (`io.github.j3ffx.nuance.dev`) : celle que `npm run phone` installe, avec les outils
  de développement. Icône sur fond clair.
- **Nuance** (`io.github.j3ffx.nuance`) : la version publiée, installée à côté. Icône sur fond sombre.

## Clé de signature des versions publiées

À venir (jalon J6) : création de la clé, sauvegarde, secrets GitHub.

## iOS (plus tard, avec un Mac)

À venir (jalon J7) : Xcode, compte Apple, installation sur un iPhone. En attendant, la CI compile le
côté iOS sur un Mac de GitHub à chaque push.
