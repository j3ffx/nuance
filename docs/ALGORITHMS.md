# Algorithmes de Nuance

Ce document explique comment Nuance simule et corrige la vision des couleurs, d'où viennent les
chiffres, et pourquoi chaque choix a été fait. Les mesures détaillées sont dans
[`report/measurements.md`](report/measurements.md), régénéré par `npm run report`.

Tout le traitement des couleurs vit dans `:core` (Kotlin pur, testé sur la JVM). L'appli ne fait
qu'appliquer le résultat sur le GPU.

## 1. Le principe : une LUT 3D

Chaque mode (simuler, corriger, isoler une teinte, accentuer le contraste) est une fonction pure
« couleur → couleur ». Quand un réglage change, cette fonction est **cuite** dans une table 3D :
pour 33 × 33 × 33 couleurs d'entrée régulièrement réparties dans le cube sRGB, on calcule la
couleur de sortie. Le shader de la caméra ne fait que lire chaque pixel et chercher sa couleur dans
cette table, avec une interpolation trilinéaire faite par le GPU (`core/.../Lut3d.kt`,
`androidApp/.../render/LutProgram.kt`).

Conséquences : les algorithmes ne touchent jamais au shader, Android et iOS donnent le même rendu,
et le coût GPU ne dépend pas de la complexité de l'algorithme (0,7 à 1,3 ms par image en 1080p sur
le téléphone de test).

### Taille de la LUT

Mesuré sur 5000 couleurs aléatoires, après passage par une texture RGBA8 (8 bits par canal), ΔE2000
entre la fonction exacte et ce que le GPU calcule :

| Mode                                | 17³ (p99 / max) | 33³ (p99 / max) | 49³ (p99 / max) |
| ----------------------------------- | --------------: | --------------: | --------------: |
| Simulation deutan 100 %             |     0,31 / 0,51 |     0,28 / 0,40 |     0,26 / 0,56 |
| Correction deutan 100 % + contraste |     1,66 / 3,49 |     0,66 / 1,56 |     0,39 / 1,01 |
| Correction tritan 100 %             |     1,93 / 3,46 |     0,78 / 1,59 |     0,47 / 1,21 |
| Isoler le rouge                     |    8,88 / 13,04 |     3,98 / 7,98 |     2,21 / 5,28 |

- **33³ en RGBA8** pour la simulation et la correction : 99 % des couleurs à moins d'un écart à
  peine perceptible (ΔE ≈ 1). Le passage en 8 bits n'ajoute presque rien (0,05 en moyenne) : une
  texture en flottants 16 bits n'apporterait rien de visible.
- **Isoler une teinte** a des bords francs, que la table arrondit : la LUT de ce mode fera 49³
  (25 ms de cuisson sur le PC, contre 7 ms en 33³ ; à mesurer sur le téléphone en J3).
- **Pendant le glissement d'un curseur**, la table sera cuite en 17³ (1,4 ms sur le PC), puis en
  33³ au relâchement : c'est imperceptible en mouvement.
- **Sur le téléphone de test** (variante debug, sans compilation anticipée, en ms : premier essai puis
  essais suivants une fois le JIT chaud) : correction 33³ 176 puis 59 à 92 ; correction 17³ 15 puis
  7 à 9 ; isolement 49³ 288 puis 149. La caméra reste à 30 images/s pendant la cuisson, faite hors
  du fil de rendu. La variante publiée (R8, compilation anticipée) sera plus rapide ; si besoin, la
  cuisson se parallélise par tranches de bleu.

## 2. Espaces de couleur

- **sRGB** (IEC 61966-2-1) : fonction de transfert et matrice vers CIE XYZ (D65). **Tous les
  modèles travaillent sur des valeurs linéaires** : on décode le sRGB avant toute matrice, on
  ré-encode après. Un test couvre l'erreur classique qui l'oublie : un rouge pur vu par un
  protanope donne (94, 94, 13) en sRGB, contre (29, 29, 1) si l'on applique la matrice aux valeurs
  encodées.
- **CIELAB** relatif au blanc sRGB, et **ΔE2000** d'après Sharma, Wu et Dalal (2005), _The CIEDE2000
  color-difference formula: implementation notes, supplementary test data, and mathematical
  observations_, Color Research & Application 30(1). Les 34 paires de test publiées par les auteurs
  sont vérifiées à 10⁻⁴ près.
- **Oklab** (Björn Ottosson, 2020, <https://bottosson.github.io/posts/oklab/>) pour le contraste, la
  saturation et l'isolement de teinte : clarté, chroma et teinte y sont bien séparés.

## 3. Simulation de la vision des couleurs

| Axe            | Forme partielle                        | Forme totale |
| -------------- | -------------------------------------- | ------------ |
| Protan, deutan | Machado 2009                           | Viénot 1999  |
| Tritan         | Brettel 1997 interpolé avec l'original | Brettel 1997 |

Sources :

- **Machado, Oliveira et Fernandes (2009)**, _A Physiologically-based Model for Simulation of Color
  Vision Deficiency_, IEEE TVCG 15(6). Matrices précalculées par paliers de 10 %, appliquées en RGB
  linéaire, interpolées entre deux paliers. Valeurs de la page des auteurs
  (<https://www.inf.ufrgs.br/~oliveira/pubs_files/CVD_Simulation/CVD_Simulation.html>), identiques à
  celles de la bibliothèque colour-science (BSD-3-Clause) ; un test en vérifie plusieurs.
- **Viénot, Brettel et Mollon (1999)**, _Digital video colourmaps for checking the legibility of
  displays by dichromats_, Color Research & Application 24(4). Une projection dans l'espace des
  cônes (LMS) sur le plan qui contient le blanc et le bleu de l'écran : blancs, bleus et jaunes
  sont conservés.
- **Brettel, Viénot et Mollon (1997)**, _Computerized simulation of color appearance for
  dichromats_, JOSA A 14(10). Deux demi-plans passant par le blanc, ancrés sur des lumières
  monochromatiques que les dichromates voient comme tout le monde : 475 et 575 nm (protan,
  deutan), 485 et 660 nm (tritan).

Les matrices de Viénot et de Brettel sont **recalculées** à partir des grandeurs physiques, pas
recopiées :

- cônes de Smith et Pokorny (1975), définis dans l'espace XYZ corrigé par Judd et Vos ;
- passage du sRGB linéaire à cet espace par l'approximation de Viénot 1999 (chromaticités des
  primaires) ;
- fonctions colorimétriques Judd-Vos du CVRL (<http://www.cvrl.org>, `ciexyzjv.csv`) pour les
  longueurs d'onde d'ancrage ;
- le blanc sRGB comme neutre, et non le blanc d'égale énergie, comme DaltonLens et Vischeck : moins
  de couleurs sortent du gamut.

Les tests comparent le résultat aux valeurs précalculées de **DaltonLens** (libDaltonLens, domaine
public ; DaltonLens-Python, MIT ; <https://daltonlens.org/understanding-cvd-simulation/>) : écart
inférieur à 2·10⁻⁴ pour Viénot, à 2·10⁻³ pour Brettel.

### Pourquoi ces choix

- **Protan et deutan partiels** : Machado est le seul modèle conçu pour les trichromaties
  anormales ; interpoler une simulation de dichromatie avec l'image d'origine n'a pas de base
  physiologique.
- **Protan et deutan totaux** : Viénot plutôt que Machado à 100 %. Par définition, un dichromate
  confond toutes les couleurs d'une même ligne de confusion. Viénot les ramène exactement à une
  seule couleur (un test le vérifie sur une grille de couleurs 6 × 6 × 6), Machado à 100 % seulement à peu près. La
  comparaison de DaltonLens (<https://daltonlens.org/opensource-cvd-simulation/>) donne aussi un
  léger avantage à Viénot aux extrêmes. Pour que le curseur de sévérité n'ait pas de saut, la
  matrice passe linéairement du palier 90 % de Machado à Viénot entre 90 et 100 % (un test vérifie
  qu'aucun pas de 1 % ne déplace une couleur de plus de ΔE 3).
- **Tritan** : Machado n'a pas été validé avec des observateurs tritans, et Viénot ne convient pas
  à cet axe. Brettel 1997 est le modèle de référence ; pour les formes partielles, on interpole
  avec l'original, faute de modèle mieux fondé.

## 4. Corrections

L'interface `CorrectionStrategy` (`core/.../correction/`) en propose deux. L'intensité va de 0
(l'image d'origine) à 1. Aucune ne bouge les gris ni le blanc : une couleur neutre ne perd rien
dans la simulation, il n'y a donc rien à déplacer (vérifié par les tests, ΔE < 0,5).

- **Redistribution de l'erreur** (stratégie par défaut, intensité 100 %), d'après Fidaner, Lin et Ozguven (2005), _Analysis of Color
  Blindness_, Stanford EE368. On simule, on calcule ce que la personne perd (original − simulé), et
  on l'ajoute aux canaux qu'elle perçoit encore. Pour protan et deutan, la matrice de l'article
  (le rouge perdu va vers le vert et le bleu) ; pour tritan, l'adaptation habituelle (le bleu perdu
  va vers le rouge et le vert), que l'article ne traite pas. Calculée en RGB linéaire, alors que
  l'original travaillait sur des valeurs encodées.
- **Remappage CIELAB** (dans le panneau Avancé). Ce que la simulation retire sur l'axe opposé perdu
  (rouge-vert, a\*, pour protan et deutan ; bleu-jaune, b\*, pour tritan) est réécrit sur les axes
  que la personne perçoit encore : surtout la clarté, un peu l'autre axe opposé.

### Le critère de mesure

On prend des **paires de couleurs que le profil confond** : deux couleurs du gamut sRGB qui ne
diffèrent que par la réponse du cône atteint (elles sont sur une même ligne de confusion), bien
distinctes pour une vision typique (ΔE ≥ 8), et à moins de ΔE 5 l'une de l'autre à travers la
simulation du profil. On mesure ensuite leur écart **vu à travers la simulation**, avant et après
correction. C'est la question qui compte : la personne les distingue-t-elle maintenant ?

Comparer deux stratégies à intensité égale n'a pas de sens : celle qui modifie le plus l'image
sépare forcément plus. On les compare donc **à coût égal**, chacune réglée pour modifier l'image
d'un ΔE moyen de 8, et l'on compte la part des paires confondues qui deviennent distinctes
(ΔE ≥ 5) :

| Profil       | Redistribution d'erreur | Remappage CIELAB |
| ------------ | ----------------------: | ---------------: |
| Protan 60 %  |                    76 % |             90 % |
| Protan 100 % |                    80 % |             87 % |
| Deutan 60 %  |                    45 % |             77 % |
| Deutan 100 % |                    80 % |             86 % |
| Tritan 60 %  |                    33 % |             65 % |
| Tritan 100 % |                    33 % |             38 % |

Selon ce critère, le remappage l'emporte partout.

Les variantes du remappage, mesurées de la même façon, montrent que mettre surtout la différence
perdue dans la **clarté**, que tous les axes perçoivent, sépare le mieux et décale le moins la
teinte des tons chair. D'où les gains retenus : 0,65 sur la clarté, 0,2 sur l'autre axe opposé,
calibrés pour qu'une intensité de 100 % reste supportable (ΔE moyen de l'ordre de 13 à 15).

### Le choix final : à l'œil, à travers la simulation

Le critère dit si deux couleurs deviennent _différentes_, pas si l'image reste lisible. Sur le
téléphone, en comparant pour les trois axes l'image corrigée **vue à travers la simulation** (le
seul moyen pour une personne à la vision typique de juger), la redistribution d'erreur a été
préférée partout. Explication probable : elle **recolore** (les rouges perdus partent vers des
teintes que la personne perçoit), et garde des couleurs franches et cohérentes d'un objet à
l'autre ; le remappage sépare surtout par la clarté, ce qui se lit comme des ombres ou des reflets
plutôt que comme des couleurs. Elle est donc la stratégie par défaut, à intensité 100 %, et le
remappage reste dans le panneau Avancé.

Ce jugement reste celui d'un œil typique regardant une simulation : le test avec une personne
daltonienne tranchera.

### À intensité 100 % (extrait)

| Profil       | Stratégie      | Distinctes | Avant → après | Coût | Teinte des tons chair |
| ------------ | -------------- | ---------: | ------------: | ---: | --------------------: |
| Deutan 100 % | redistribution |       79 % |    0,0 → 11,1 |  7,6 |                   39° |
| Deutan 100 % | remappage      |       97 % |    0,0 → 18,2 | 14,6 |                   49° |
| Protan 100 % | remappage      |       98 % |    0,0 → 16,0 | 13,1 |                   43° |
| Tritan 100 % | remappage      |       89 % |     0,0 → 9,1 | 14,8 |                   16° |

### Limites connues

- **Tons chair** : à intensité et sévérité maximales, certains tons chair clairs changent nettement
  de teinte (jusqu'à 39° avec la redistribution, 49° avec le remappage, le plus souvent bien moins).
  À 70 % et 60 % de sévérité, le décalage reste sous 20° et les tons chair bougent moins que le reste
  de l'image (testé).
- **Tritan** : ses lignes de confusion sont courtes dans CIELAB ; à coût égal, la correction en
  sauve moins (environ 40 % des paires à 100 %). Dans les couleurs très claires, le gain de clarté
  bute sur le blanc.
- **Le critère est un modèle** : il dit ce que la simulation prévoit, pas ce qu'un daltonien voit.
  Le jugement final reviendra au test réel avec une personne concernée.

## 5. Isoler une teinte, contraste et saturation

- **Isoler une teinte** (`ColorFunction.isolation`) : en Oklch, les couleurs dont la teinte est à
  moins de la largeur réglable (30° par défaut) de la cible gardent leur couleur, renforcée de
  30 % ; les autres passent en gris **à clarté égale**. Le bord est progressif (sur les derniers
  40 % de la largeur), et les quasi-gris, dont la teinte n'a pas de sens, restent gris. Les teintes
  proposées sont définies par un échantillon sRGB (rouge, orange, jaune, vert, bleu, violet, rose),
  dont on prend la teinte Oklch ; la pipette donnera la sienne de la même façon.
- **Contraste et saturation** (`ColorFunction.enhancement`) : en Oklab, la clarté est étirée autour
  du gris moyen et le chroma multiplié. À 0 et 0, c'est l'identité ; les gris restent neutres.
  Ils s'appliquent après la correction.

## 6. Tests et références

- `core/src/commonTest` : espaces de couleur (jeu de Sharma), modèles (DaltonLens, page de Machado),
  propriétés (identité à 0 %, gris préservés, lignes de confusion confondues, continuité de la
  sévérité, erreur du gamma), corrections (seuils de séparation, gris, tons chair), modes, LUT
  (cuisson puis interpolation fidèles à la fonction exacte).
- `core/src/jvmTest/.../GoldenTest.kt` : chaque mode appliqué à une mire synthétique de 340 couleurs
  (cube 6×6×6, gris, teintes, tons chair), comparé à `core/src/jvmTest/resources/golden/`. Un
  changement voulu se régénère avec `npm run golden`, puis se relit dans le diff.
- Toutes les images de test et du rapport sont **générées par le code** (`core/.../charts/`).

## 7. Choix du pipeline caméra (Android)

Deux façons d'appliquer la LUT au flux de CameraX ont été prototypées et mesurées sur le téléphone
de test (1080p, 30 images/s, intérieur) :

|                               | Effet CameraX (`SurfaceProcessor`) | Rendu direct (`SurfaceTexture` → `SurfaceView`) |
| ----------------------------- | ---------------------------------: | ----------------------------------------------: |
| Images/s                      |                               29,9 |                                            30,0 |
| Temps GPU par image           |                             1,1 ms |                                    0,7 à 1,3 ms |
| Capture → affichage (médiane) |                         93 à 98 ms |                                      93 à 96 ms |

Les performances sont équivalentes. Le **rendu direct** a été retenu : c'est l'appli qui gère la
rotation et le cadrage à l'écran, si bien que les vues du mode Comparer sont de simples zones de
l'écran. Avec l'effet, il aurait fallu les découper dans une image que l'aperçu fait ensuite
tourner. La photo et la vidéo filtrées réutiliseront le même contexte OpenGL.

L'aperçu brut de la caméra, sans filtre, est estimé à environ 78 ms de la capture à l'affichage :
il n'est pas mesurable directement, la caméra lui donnant des horodatages calés sur l'écran. Nuance
ajoute donc environ 17 ms, soit une demi-image à 30 images/s. Méthode : `npm run phone:latency`
lit, pour chaque image, l'heure souhaitée et l'heure réelle d'affichage tenues par SurfaceFlinger.

## 8. Test intégré (« Trouver mon profil »)

Trois étapes, dans `core/.../screening/`. Le résultat est un profil proposé (axe et sévérité), avec
un niveau de confiance, gardé sur le téléphone. Ce n'est pas un diagnostic : l'écran n'est pas
étalonné, et un test de quelques minutes ne remplace pas un examen.

### Planches de points

Des planches pseudo-isochromatiques **générées par le code**, jamais copiées (celles d'Ishihara sont
protégées). Un disque de points de tailles aléatoires ; ceux qui tombent sur le chiffre (1 à 9,
dessiné par des traits épais, sans police) ont une couleur, les autres une autre. Les deux couleurs
ne diffèrent **que par la réponse d'un cône** : la réponse du cône de l'axe est multipliée par
1 + δ et 1 − δ autour d'une teinte de base. Elles sont donc sur une même ligne de confusion :
quelqu'un à qui ce cône manque voit un champ uniforme. La clarté de chaque point est ensuite
tirée au hasard (×0,75 à ×1,15), de la même façon dedans et dehors, pour que la luminosité ne
trahisse pas le chiffre.

- Une planche de démonstration (chiffre clair sur fond sombre) que tout le monde voit ;
- par axe, deux planches **fortes** (différence de ΔE2000 20 pour une vision typique) et deux
  **subtiles** (ΔE2000 9 ; 7 pour le tritan, dont les formes partielles sont simulées en atténuant
  la forme totale) : seules les formes marquées ratent les premières, les formes plus légères ratent
  aussi les secondes ;
- 13 planches, dans un ordre aléatoire, avec toujours « Je ne vois rien ».

Une planche ratée compte 2 (forte) ou 1 (subtile) ; un axe est « échoué » à partir de 2.
Vérifié en simulation (`PlatesTest`) : à 100 %, l'écart entre les deux couleurs tombe à 0 pour
l'axe visé ; une forme à 50 % voit encore les planches fortes (écart ≥ 7) mais plus les subtiles
(≤ 4,7, sous le bruit de clarté) ; les axes rouge-vert et bleu-jaune ne se masquent pas leurs
planches. Protan et deutan, eux, se confondent souvent sur ces planches (leurs lignes de
confusion sont proches) : c'est le classement de teintes qui les sépare.

### Classement de teintes (inspiré du Farnsworth D-15)

Les 15 pastilles et la pastille de départ du D-15 (Farnsworth 1947, valeur Munsell 5, chroma 4).
Leurs coordonnées CIE 1976 u\*v\* sont celles publiées par **Vingrys et King-Smith (1988)**,
_A quantitative scoring technique for panel tests of color vision_, Investigative Ophthalmology and
Visual Science 29(1), reprises par le paquet R CVD (Jose Gama). Elles sont relatives à
l'illuminant C : on les convertit en XYZ avec L\* = 51,6 (valeur Munsell 5, Y = 19,77), puis en
sRGB après une adaptation chromatique de Bradford vers le blanc D65. Un test vérifie qu'elles sont
dans le gamut et de luminance 0,198.

Sur un écran de 360 dp, glisser 15 pastilles est pénible : on applique plutôt la consigne même du
D-15, « prends la pastille la plus proche de la dernière posée », en touchant les pastilles l'une
après l'autre (avec « Reprendre la dernière »).

L'analyse est la **méthode du moment d'inertie** de Vingrys et King-Smith : les vecteurs de
différence entre pastilles successives donnent un grand et un petit rayon et un angle. L'indice
C (grand rayon / celui d'un rangement parfait) dit si le rangement est anormal (> 1,78), l'indice
S s'il est sélectif (≥ 2), l'angle donne l'axe : on prend l'axe de référence le plus proche
(protan +8°, deutan −7°, tritan −82°, d'après l'article). Vérifié :

- un rangement parfait donne C = 1,00 ;
- les rangements classiques d'un protan, d'un deutan et d'un tritan tombent sur leur axe ;
- un daltonien simulé qui prend toujours la pastille la plus proche **telle qu'il la voit**
  obtient : protan +6°, deutan −13°, tritan −86° (C de 3,1 à 4,1).

### Sévérité : un escalier adaptatif

Quatre carrés gris moyen, dont un diffère le long d'une ligne de confusion de l'axe trouvé
(réponse du cône × (1 ± δ)) : « touche le carré différent », ou « je ne vois pas de différence ».
Deux bonnes réponses de suite rendent δ plus petit, une erreur le rend plus grand (escalier
2-bas 1-haut, qui se stabilise là où l'on répond juste 71 % du temps) ; jusqu'au premier
changement de sens, une seule bonne réponse suffit pour descendre plus vite. δ va de 0,4 % à ce
que permet le gamut, par pas de ×1,4. On s'arrête après 8 changements de sens ou 40 essais ; le
seuil est la moyenne géométrique de δ aux 4 derniers changements de sens. Trois erreurs au plus
grand δ : la personne ne voit jamais la différence, c'est une forme totale.

Le seuil devient une sévérité **par les modèles de simulation** : c'est la sévérité à laquelle la
simulation prévoit que les deux couleurs, au seuil, diffèrent de ΔE2000 2,5 (une différence juste
visible entre deux carrés côte à côte sur un téléphone). Si même une vision typique les
distinguerait à peine, la sévérité est 0.

Si les planches rouge-vert échouent autant en protan qu'en deutan et que le classement ne
tranche pas, on mesure les deux escaliers et l'on garde l'axe où la sévérité est la plus forte.

### Décision et confiance

- Un axe échoué aux planches et confirmé par le classement : confiance **élevée**.
- Un axe échoué aux planches, classement normal (une forme légère, que le D-15 laisse passer) :
  **moyenne**. Le classement indique un autre axe : **faible**.
- Rien aux planches mais un classement anormal : l'axe du classement, confiance **faible**.
- Rien nulle part : aucune difficulté repérée.
- La confiance baisse d'un cran si l'escalier a été irrégulier (rapport > 3 entre les derniers
  changements de sens) ou si la sévérité mesurée est presque nulle alors que les planches ont
  échoué ; elle est faible si la planche de démonstration a été ratée.

### Le test du test

- **En simulation** (`ScreeningTest`) : un observateur qui voit à travers un modèle passe tout le
  test (il lit une planche si l'écart simulé atteint 5, range les pastilles par plus proche voisin,
  repère le carré si l'écart simulé atteint 2,5). Vision typique : rien de repéré, confiance élevée.
  Chaque axe à 40, 60, 80 et 100 % : le bon axe, et une sévérité à 6 % près.
- **Sur le téléphone** : en variante dev, l'intro du test propose « passer le test comme » un
  protan, un deutan ou un tritan (100 % ou 60 %) ; toutes les couleurs du test sont alors montrées à
  travers la simulation, pour qu'une personne à la vision typique le passe « comme » ce profil.

Limites : ce test suppose que les modèles de simulation sont justes, y compris pour la sévérité ;
l'écran, sa luminosité et l'éclairage ambiant déplacent les couleurs ; le seuil de 2,5 est un
choix raisonnable, pas une mesure. D'où le niveau de confiance et l'avertissement à chaque résultat.

## 9. La pipette et le nom des couleurs

- **Lecture** : le fil de rendu redessine la vue touchée **sans sa LUT** (l'image d'origine) dans
  une toute petite image de 20 × 20 pixels centrée sur le point, puis la relit. La couleur est la
  moyenne de ces 400 pixels **en lumière linéaire** (moyenner les valeurs encodées assombrirait un
  mélange de clair et de foncé). Cela ne se fait qu'à la demande, trois fois par seconde tant que
  la pipette est ouverte : la boucle de rendu ne copie jamais une image entière vers le processeur.
- **Nom** : des règles propres à Nuance, sur Oklch (clarté, chroma, teinte), sans liste de noms
  sous licence : 14 termes de base (noir, blanc, gris, rouge, orange, marron, beige, jaune, olive,
  vert, turquoise, bleu, violet, rose) et les modificateurs « clair », « foncé », « grisâtre »,
  « vif ». Oklch plutôt que le LCh de CIELAB : CIELAB tord les bleus vers le violet (le bleu pur y
  est à 306°, un violet à 312°), alors qu'Oklch les sépare (264° et 294°) ; c'est aussi l'espace du
  mode « Isoler », si bien que la pipette et « Isoler » s'accordent sur ce qu'est une teinte.
  « Clair » et « foncé » sont relatifs à la clarté habituelle du terme (un jaune est plus clair
  qu'un bleu). Les seuils ont été réglés sur des couleurs de référence (`ColorNamingTest`).
- **Telle que perçue** : la couleur passée par la simulation du profil, et son nom.
