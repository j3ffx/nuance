# Nouveautés de Nuance

## Depuis la dernière version

### Nouveautés

- Le panneau sous l’image est bien plus court : le curseur et sa valeur tiennent sur une ligne, et
  les choix (méthode, nombre de vues, voir sans correction, plus de réglages) sur une rangée.
- La caméra se voit derrière les boutons Galerie, Photo et Vidéo : plus de bande noire.
- Des noms plus clairs et moins de texte : « Une couleur » et « Ma vision » pour les modes, « Force »,
  et sous l’image des boutons à icône : ◐ Clarté / Teintes (la méthode), ⊞ 4 / 2 vues, 👁 Original, et
  l’icône de réglages pour « Plus de réglages ».
- Les boutons ne se touchent plus, dans toute l’appli.
- En paysage, comme une appli photo : Galerie, Photo et Vidéo posés sur l’image, contre le bord
  droit, et tous les réglages sur une seule ligne fine sous l’image.

## 0.1.2 — 2026-10-23

### Nouveautés

- Profils mixtes : un curseur par type (protan, deutan, tritan), à mélanger librement, par exemple
  protan et un peu de tritan. C’est une approximation : aucun modèle n’a été validé pour plusieurs
  types à la fois.
- Le profil utilisé est toujours coché, et la puce dit « modifié » quand tu as changé un réglage :
  un toucher pour l’enregistrer, en créer un nouveau ou revenir au profil.
- La caméra s’ouvre sur le dernier profil utilisé ; l’écran Profils devient une liste simple, avec
  Modifier, Partager et Supprimer sous « ⋮ ».
- Galerie, Photo et Vidéo dans une barre sous l’image, toujours nommés ; à droite, seulement figer,
  lampe, caméra et zoom.
- « Régler la correction » (ou l’isolation, la comparaison) remplace « Avancé », et ne cache plus
  l’image ; « Réglages » devient « Paramètres ».
- Une ligne sous les modes dit à quoi sert chacun ; les vues s’appellent « Original », « Vu par un
  deutan », « Corrigé ».
- Une astuce sur l’image explique la pipette et l’appui long, et « Voir l’original » le fait d’un
  toucher.
- La pipette dit « Couleur réelle » et « Tu la vois sans doute comme… », et ne cache plus le point
  visé.
- Le test « Trouver mon profil » revient d’où il a été ouvert, demande avant de tout perdre, et
  remplace le précédent « Mon test » au lieu d’en ajouter un.
- L’aide, en sections, s’ouvre avec « ? » depuis la caméra, et dit où vont les photos.
- La méthode de correction se change d’un toucher, sous le curseur d’intensité.
- « Trouver mon profil » se refait depuis les réglages et la liste des profils.
- Les photos gardent aussi l’original par défaut, pour pouvoir le retravailler dans un autre mode.
- Pendant une mise à jour, l’appli montre où elle en est : téléchargement (en pourcentage),
  vérification, puis l’attente de la confirmation d’Android ; et à la fin, installée ou annulée.
- Nuance prévient qu’elle va se fermer pendant l’installation, et dit « À jour » quand on la rouvre.

### Corrections

- La correction par défaut devient « Clair et foncé » : avec « Recoloration », les rouges pâlissaient
  jusqu’à paraître blancs pour un protan. Tes profils enregistrés passent à la nouvelle méthode ; tu
  peux revenir à l’ancienne d’un toucher.
- Depuis une photo de la galerie, la touche Retour et un bouton « Revenir à la caméra » ramènent à la
  caméra (il fallait forcer l’arrêt de l’appli) ; Retour quitte aussi une image figée.
- Une photo de la galerie ne se met plus en pause au bout d’une minute.

## 0.1.1 — 2026-10-22

### Corrections

- « Installer » une mise à jour ouvre bien la confirmation d’Android (elle restait bloquée sans rien
  dire sur Android 14 et plus).

## 0.1.0 — 2026-10-22

### Nouveautés

La première version : de quoi la faire essayer à une première personne daltonienne.

- La caméra filtrée en temps réel, à 30 images par seconde.
- Quatre modes : corriger les couleurs selon ton profil, isoler une couleur, simuler ce que voit une
  personne daltonienne, et comparer quatre vues côte à côte (ou deux, au choix).
- Reste appuyé sur l’image pour voir l’original.
- Les trois types de daltonisme (protan, deutan, tritan), de 0 à 100 % de sévérité.
- Un panneau « Avancé » : méthode de correction, contraste, saturation, plage de teinte.
- Des profils : six préréglages, les tiens, à partager en texte court avec un proche et à importer.
- Une page de premier lancement, des réglages (langue, thème sombre ou clair, pause automatique,
  photos, mises à jour), l’aide et les avertissements.
- Un logo : le nuancier.
- La pipette : touche un point de l’image pour connaître le nom de sa couleur, son code, et la
  couleur telle que la perçoit ton profil. En mode « Isoler », elle choisit la couleur à garder.
- « Trouver mon profil » : un test de 3 minutes (planches de points, rangement de pastilles,
  carrés à comparer) qui propose un type et une sévérité, avec un niveau de confiance. Ce n’est pas
  un diagnostic.
- La langue de l’appli se choisit dans les réglages : comme le téléphone, français ou anglais.
- Figer l’image, puis zoomer et se déplacer dedans ; la pipette et les modes marchent sur l’image figée.
- Le zoom de la caméra (en pinçant, ou un bouton qui passe d’un objectif à l’autre), la lampe et la
  caméra avant.
- Photos et vidéos filtrées, enregistrées dans l’album « Nuance » de la galerie, sans position ; au
  choix, l’original en plus.
- Ouvrir une photo de la galerie et lui appliquer les mêmes filtres.
- Pause automatique de la caméra (téléphone posé écran contre la table, ou sans toucher ni bouger) ;
  un toucher la relance.
- Quand le téléphone chauffe, l’image ralentit pour le laisser refroidir, et c’est écrit à l’écran.
- La mise à jour depuis l’appli, à partir des versions publiées sur GitHub (désactivée par défaut).
