# La balle : une règle, quatre garanties

Un `ItemDisplay` peut devenir invisible pour des raisons qu'**aucun serveur ne peut observer** :
un ensemble caché côté client (`Player#hideEntity`), une boîte de culling que le client a calculée
vide, un paquet de spawn qu'il a jeté. Le score, lui, ne remarque rien : il est du pur calcul
physique sur la position de la balle. C'est exactement comme ça qu'un joueur finit par regarder un
terrain vide pendant que le panier compte.

La réponse n'est donc pas un énième correctif dans la logique de masquage. C'est une règle :

> **Le plugin ne cache jamais la balle à personne.**

Tout ce qui cachait la balle a été supprimé (`BallVisibilityRules`, `syncGatherVisibility`,
`makeVisibleForLaunch`, `hiddenGatherViewerId`, tous les `hideEntity` de `BasketballEntity`). Quatre
garanties remplacent cette logique.

## 1. Le tir démarre sur une entité neuve

`presentBallForLaunch()` est la **première instruction** de chaque départ de balle — `launchShot`,
`launchPass`, `launchShotVirtual`, `launchPassVirtual`, `launchDunkVirtual`, `startSlamDunk` — et
avant le garde-fou : un tir refusé (mauvais monde, vecteur non fini) laisse quand même la balle à
l'écran.

Elle **reconstruit** l'affichage (`respawnVisualBall()` : ancienne entité retirée, nouvelle
spawnée), elle ne le réutilise pas. Une entité neuve, c'est un paquet de spawn neuf : aucun ensemble
caché n'y survit, aucun état d'interpolation ne la suit. `settleFreshDisplay()` la place ensuite à
la position de largage du tick même.

## 2. La balle ne se cache plus pendant la charge

L'ancien masquage existait pour une raison visuelle : pendant la charge, la balle était plaquée
devant l'œil du tireur et ressemblait à une balle coincée dans l'arc. Le **geste** a donc bougé au
lieu de la balle : elle est armée à hauteur d'épaule, côté main de tir, décalée de la ligne de visée
(`pocketTarget`), et dessinée 20 % plus petite tant que la corde est tirée (`gatherScale`). Visible
par le tireur, hors du réticule.

## 3. Le client ne peut pas la culler

`spawnVisualBall()` fixe `setDisplayWidth` / `setDisplayHeight` à **1,10 bloc**
(`VISUAL_BALL_CULL_SIZE`). La valeur par défaut est `0` : une boîte de taille nulle est un **point**
à la position de la balle, et un point qui sort du frustum emporte tout le modèle avec lui. La
quatrième rotation (`spinRotation`) est en plus normalisée à chaque `spawnVisualBall` et à chaque
`syncEntities` (`normalizeSpin()`) : un quaternion qui dérive met le modèle à l'échelle au lieu de
le faire tourner.

## 4. Elle se redéclare visible, et le dit si elle est démolie

`revealToEveryone()` tourne toutes les **10 ticks** (`VISIBLE_REDECLARE_TICKS`), toutes les 30 ticks
quand la balle dribble. Le plugin n'ayant plus rien caché, c'est un filet : le seul cache restant
est celui de la caméra de replay, qui re-cache la balle vivante **à chaque image**
(`CinematicCameraManager.keepLiveBallHidden`) et rend au joueur, à la fin du replay, l'affichage
que la balle porte *à ce moment-là* (`toShow = current`).

Enfin, `noteRenderRebuild()` compte les reconstructions. **16 reconstructions en moins de 3
secondes** (`RENDER_REBUILD_STORM`) est une boucle, pas une réparation : personne ne voit une balle
effacée et respawnée à cette cadence. La console le dit une fois, avec la position et le mode :

```
[BosketBall] The ball display was rebuilt 16 times in under 3s at 0.00/65.00/12.40
(mode IN_SHOT_FLIGHT). Something is removing it every tick; the ball is probably
invisible to the players while this lasts.
```

## 5. La balle clignotait : une entité détruite plus vite qu'une image (la vidéo de Medal, 8 s)

La vidéo `MedalTVMinecraft20261108190336439.mp4` (branche `main`) a été dépouillée image par image
(60 i/s, extraction ffmpeg, seuillage sur la balle rouge). Ce qu'elle montre, en une phrase :
**la balle est visible quand elle est dans la main, et seulement là.** Elle clignote pendant la
charge (visible 1 image, absente 2, revient énorme, disparaît), elle n'est jamais dessinée pendant
le vol, et elle ne revient jamais après le panier — alors que le panier compte (« OWN BASKET +3 »)
et que le replay du cinématique, lui, montre une trajectoire parfaitement normale.

Un clignotement de une à deux images n'est pas un problème de position, ni de masquage : c'est une
entité **détruite et recréée plus vite que le client ne dessine une image**. C'est exactement ce que
faisait la boucle de réparation :

```java
if (visualBall == null || !visualBall.isValid() || hitbox == null || !hitbox.isValid() || drift) {
    spawnEntities();   // supprime la balle ET le hitbox, puis recrée les deux
}
```

Trois défauts en un :

1. **Le hitbox pouvait condamner l'affichage.** Il était posé à `position − BALL_RADIUS`, c'est-à-dire
   *dans* le bloc de sol dès que la balle reposait au sol. Un `Interaction` qui ne « prend » pas, ou
   que quelque chose retire, laissait `hitbox == null` : la balle était alors supprimée et recréée
   **à chaque tick**, donc jamais dessinée. La physique continuait, le panier comptait.
2. **Aucune limite de cadence.** Une réparation qui échoue se réparait 20 fois par seconde : c'est un
   stroboscope, pas un affichage.
3. **Aucun nom sur la panne.** Impossible de savoir *laquelle* des quatre conditions déclenchait la
   boucle ; d'où trois rounds de correctifs à l'aveugle.

Les correctifs :

* `repairRenderIfBroken()` **diagnostique séparément** l'affichage (`displayFault()`) et le hitbox
  (`hitboxFault()`), et ne répare que ce qui est cassé : un hitbox manquant ne touche plus jamais à
  l'entité visuelle.
* `REPAIR_MIN_INTERVAL_TICKS = 4` : aucune reconstruction d'affichage deux fois en quatre ticks. Ce
  qui est retenu est compté (`displayRepairsSuppressed`).
* ~~`FLIGHT_REFRESH_TICKS = 6` : une balle en l'air remplace son affichage sur une horloge, six
  ticks.~~ **Retiré au round suivant, et c'était une erreur de ma part** : voir §6. Le
  rafraîchissement périodique était censé être « invisible à l'œil » ; la vidéo montre l'inverse —
  c'est précisément le vol du tir qui n'a plus de balle du tout.
* le hitbox est posé **au-dessus** du sol (`clearOfFloor`), plus jamais dans le bloc.
* `noteRepair()` écrit **une ligne par panne distincte**, avec la position, le mode et le motif
  exact : `display missing`, `display invalid`, `display in another world`,
  `display drifted X blocks`, `hitbox missing`, `hitbox invalid`.

## 6. La balle qui vole n'existait pas à l'écran (même vidéo, 60 images/s)

Le §5 expliquait le clignotement d'avant le lâcher. Il n'expliquait pas *le vol* : dans la vidéo,
après 4,00 s, il n'y a plus de balle nulle part — ni en l'air, ni sur l'arceau, ni au sol ensuite —
alors que le score s'inscrit et que la caméra de replay fait un arc normal. La vidéo a donc été
repassée image par image, avec une détection de taches rouges (le ballon est la seule chose rouge
saturée du terrain) sur toute la zone de jeu, hors HUD :

| temps | ce que la détection trouve |
|---|---|
| 3,30 → 3,85 s | **une tache rouge de 400 × 280 px** : le ballon tenu, plein cadre |
| 3,90 → 4,60 s | **aucun pixel rouge pendant quarante-deux images d'affilée** |
| 5,17 → 5,25 s | la balle est **dessinée** — c'est le replay |

Deux faits, et ils vont dans le même sens :

1. Le ballon est **dessiné** exactement pendant les phases où **rien ne reconstruit son entité** : le
   dribble avant le tir, et le replay (qui a sa propre entité, jamais reconstruite).
2. Le ballon est **absent pendant toute la phase où son entité est reconstruite sur une horloge** :
   `FLIGHT_REFRESH_TICKS` (§5), une entité neuve trois fois par seconde, en plein vol.

**Une entité de display n'est dessinée que tant que personne ne la lui reprend.** Le
rafraîchissement roulant était la maladie déguisée en remède ; il est supprimé. Une balle saine garde
son entité du lâcher jusqu'à l'arceau, et seuls les défauts que le serveur peut réellement observer
(`display missing`, `display invalid`, `display drifted`) la remplacent, derrière
`REPAIR_MIN_INTERVAL_TICKS`.

**Et de quoi le prouver sans deviner.** `/bb ball` (staff, en match ou sur un terrain d'entraînement)
imprime l'état de rendu de la balle : l'entité d'affichage et son UUID court, si elle est valide, son
monde, sa position, sa distance à la balle simulée, son âge en ticks, **combien de fois elle a été
reconstruite depuis la création de la balle**, combien de réparations ont été retenues, et un verdict
en clair (`healthy` / `DISPLAY FAULT` / `hitbox fault` / `rebuilt N time(s)`). C'est la seule façon
de distinguer « le client ne la dessine pas » de « le serveur la remplace », qui est exactement le
piège dans lequel nous sommes tombés trois rounds de suite.

Trois autres corrections du même passage :

* **La balle dans la main était énorme.** 400 × 280 px, soit 39 % de la hauteur de l'image, et
  centrée à 20 % à droite de l'axe : à 0,64 bloc de l'œil, un ballon de 0,42 bloc couvre 33° d'un
  champ de 70°. Elle est maintenant tenue à 0,88–1,03 bloc de l'œil, à 23–30° hors de l'axe, et
  dessinée à 0,34–0,40 au lieu de 0,42 : **≈ 20 % de la hauteur d'image**, la taille d'un ballon
  tenu. Les chiffres sont dans `BallHandPose`, et son test tient les angles.
* **Le « perfect green » repeignait l'écran.** La gerbe de `HAPPY_VILLAGER` partait à 2,05 blocs
  au-dessus des pieds, c'est-à-dire à 0,43 bloc de la caméra du tireur : chaque sprite faisait 200 px
  et douze d'entre eux restaient une seconde. Elle part maintenant du point de lâcher, une bloc et
  demi devant, huit sprites, 0,10 d'écartement — une gerbe verte près de la balle, pas un mur vert.
* **Le replay avait sa propre balle sans boîte de culling**, sans `view range` et avec la
  transformation plate d'un cadre d'item. Elle a les trois, plus une fin de clip honnête : la balle
  tombe au lieu de rester figée en l'air (la chute était écrasée, sur le même tick, par le téléport
  vers la dernière image enregistrée).

## 7. Le retour au cube : un seul élément, huit panneaux peints

La version précédente assemblait une icosphère de 320 facettes. Elle était ronde, et elle a été
refusée : le propriétaire du projet préférait le cube. Le ballon est donc **un seul élément cube**,
et tout le travail est passé dans sa texture — c'est un choix assumé, pas un repli, parce qu'un cube
texturé correctement est moins cher à envoyer, impossible à démembrer, et lit mieux à 32 px qu'un
maillage fin.

Ce qui fait qu'un cube lit comme un ballon :

* **Les huit panneaux sont tracés dans l'espace des directions, pas dans les pixels d'une face.** Le
  réseau est celui d'un vrai ballon — deux panneaux d'extrémité (deux cercles de latitude à ±38°) et
  six panneaux latéraux (six méridiens, pliés de 7,5° pour que les coutures soient courbes). Un texel
  est classé par la direction qu'il représente : deux texels qui se rejoignent sur une des douze
  arêtes du cube sont la même direction, donc la même couture. Le réseau ne peut pas se contredire
  d'une face à l'autre, et les coutures traversent les arêtes sans le moindre raccord.
* **Le cuir est une seule peau.** Le grain (bruit de valeur + champ de Worley, échantillonné sur la
  même direction) continue lui aussi par-dessus les arêtes. Les huit octants ont chacun une teinte
  légèrement différente : le cube a huit panneaux, comme le ballon qu'il remplace.
* **La lumière du ballon est cuite dans la texture.** Terme de Lambert sphérique calculé sur la
  direction du texel, occlusion et chanfrein sur les arêtes du cube, lèvre éclairée le long de chaque
  couture, reflet de Wetting léger : c'est ce qui donne la rondeur sur des faces planes. Le client
  éclaire déjà le modèle, donc cette lumière reste volontairement faible.
* **Six tuiles de 128 px dans un atlas de 384 × 256 par skin.** Les UV du modèle restent dans la
  grille **0..16** de Minecraft, qui normalise *toutes* les textures quelle que soit leur taille :
  tuile (colonne, ligne) = `[colonne*16/3, ligne*8, (colonne+1)*16/3, (ligne+1)*8]`. C'est le piège
  du dossier : `texture_size`, le champ qui a l'air de changer cette grille, n'est **lu que par
  Blockbench** — l'écrire en pixels d'atlas donne un ballon d'une seule couleur, et le wiki qualifie
  d'« incohérent » tout UV hors de 0..16. Le test `theSixFaceTilesTileTheAtlasWithNothingLeftOver`
  relit les six rectangles du modèle, les remet à l'échelle de l'image livrée et exige qu'ils
  couvrent l'atlas exactement, sans recouvrement.
* **Le PNG livré est quantifié à 128 couleurs** : ~70 Ko par skin, invisible à l'œil sur le grain,
  contre 152 Ko en couleur vraie. Fire est une bande verticale de 4 images (287 Ko) et c'est la
  seule animée.

Chiffres tenus par les tests, pas par la mémoire :

| ce qui | valeur | où |
| --- | --- | --- |
| éléments du modèle | **1** | `BallModelContractTest#everySkinShipsOneCubeAtTheRadiusTheSimulationCollidesAt` |
| boîte de l'élément | −0,75 … 16,75 | demi-extension 8,75 unités = 0,547 bloc |
| échelle d'affichage de l'entité | 0,52 | d'où **0,285 bloc** = `PhysicsMath.BALL_RADIUS` |
| arêtes du cube | 1,41 × le rayon | le prix du cube, identique à l'ancien ballon |
| atlas | 384 × 256, 6 tuiles, UV en 0..16 | `theSixFaceTilesTileTheAtlasWithNothingLeftOver` |
| pack | 1 653 461 octets | `releases/BosketBall-ResourcePack.zip` |

L'orientation des UV suit la table de Blockbench (nord/sud/est/ouest : `v` inversé par rapport à y,
dessus : `v` suit +z, dessous : `v` suit −z). C'est la table que `_face_uv_from_point` implémente
aussi pour l'aperçu : ce que la planche de contact montre est ce que le client dessine, y compris
l'inversion du nord.

À vérifier à l'œil sur `docs/previews/ball_contact_sheet.png` : à 132 px, les coutures doivent être
noires et profondes avec une lèvre claire en haut à gauche ; à 32 px (la taille réelle d'une icône
d'inventaire) le ballon doit encore se lire comme un ballon et pas comme un cube orange.

## Couverture

`src/test/java/fr/bosketball/BallVisibilityTest.java` — 23 tests : absence de tout
`hideEntity`/`hiddenGatherViewerId` dans la balle, le geste de charge qui reste visible, la boîte de
culling dimensionnée, les six départs qui commencent par `presentBallForLaunch()` avant leur
garde-fou, la reconstruction = retrait + spawn + `revealToEveryone`, l'intervalle de redéclaration,
le compteur de tempête, le quaternion normalisé, le replay qui re-cache à chaque image, la
réparation découplée (un hitbox cassé ne touche pas à la balle), le garde-fou anti-strobe, le
rafraîchissement de vol, la ligne de diagnostic et le hitbox hors du sol, et les invariants déjà
tenus (tag `bb_live_ball`, tick cassé, skin cassé, centre inutilisable, dérive de
l'affichage).

`src/test/java/fr/bosketball/BallModelContractTest.java` — 9 tests sur les octets livrés : un seul
élément et son rayon simulé, la même silhouette pour les huit skins, les six tuiles qui couvrent
l'atlas exactement et sans recouvrement, un atlas par skin et Fire seule animée, l'orientation des
transformations d'affichage, les 32 codes des vignettes, la cohérence du texte de boutique avec la
planche d'aperçu, la rampe de prix, et le fait que seule Two-Tone coupe son cuir en deux couleurs.

## À retester en jeu

1. Tirer : la balle reste à l'écran du tireur du lâcher jusqu'au panier.
2. Charger sans tirer (relâcher le clic dans le vide) : la balle ne disparaît pas non plus.
3. Passer, dunker, balle libre : toujours une balle visible.
4. Regarder un replay de panier : la balle vivante ne réapparaît pas dans le plan, et elle est bien
   revenue à la fin du replay.
5. `grep "rebuilt\|render repair" logs/latest.log` après une session : aucune ligne de
   tempête, et si une ligne `Ball render repair` apparaît, elle dit **quoi** était cassé.
6. Charger un tir : le ballon tenu doit faire **environ un cinquième de la hauteur de l'écran**, pas
   la moitié, et rester à droite du réticule, jamais devant.
7. Tirer et **suivre le ballon des yeux** jusqu'à l'arceau : il doit être dessiné sans interruption,
   et exister encore au sol après le panier.
8. Un « perfect green » : une petite gerbe verte devant soi, pas un mur de croix vertes.
9. Regarder le replay jusqu'à la fin : le ballon tombe et s'immobilise, il ne reste pas suspendu.
10. Regarder le ballon de près : les coutures doivent traverser les arêtes du cube sans cassure, et le
    grain ne doit pas changer d'échelle d'une face à l'autre. Si une couture s'arrête net sur une
    arête, c'est le classement par direction qui est cassé, pas la texture.
11. Si la balle disparaît encore : `/bb ball` **pendant** qu'elle est invisible (ou juste après), et
    m'envoyer les lignes. `rebuilt 0 time(s)` + `healthy` signifie que le serveur livre bien une
    entité : le problème est côté client, et la ligne le dit. `rebuilt N` nomme le coupable.
