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

## 7. Le retour au cube : seize pixels de cuir par face

La version d'avant assemblait une icosphère de 320 facettes. Elle était ronde, et elle a été
refusée : le propriétaire du projet préférait le cube. Le ballon est donc **un seul élément cube**,
et tout le travail est passé dans sa texture — c'est un choix assumé, pas un repli, parce qu'un cube
texturé correctement est moins cher à envoyer, impossible à démembrer, et lit mieux à 32 px qu'un
maillage fin.

Ce qui change dans cette passe, c'est la **résolution de la peinture** : le cuir était peint à
128 px par face, avec du grain, du bruit de Worley et un flou que personne ne voit à 32 px et que le
client ré-échantillonne en bouillie. Chaque face fait maintenant **16 × 16 pixels**, comme les
57 icônes du pack. Le mémoire de recherche est dans `docs/agents/research/ball-icon.md`.

Ce qui fait qu'un cube de 16 px lit comme un ballon :

* **Trois grands cercles, tracés dans l'espace des directions.** Un équateur et quatre méridiens —
  la découpe d'un vrai ballon. Un texel est classé par la *direction* qu'il représente, pas par ses
  coordonnées sur sa face : deux texels qui se rejoignent sur une arête sont la même direction, donc
  la même couture. Les six faces portent la même croix et les douze arêtes ne montrent aucun
  raccord. Huit méridiens donnaient un mur de briques, un anneau polaire à ±56° donnait une couronne
  de cinq texels : les deux ont été essayés et jetés.
* **La couture fait un texel, et elle est posée sur la grille.** Deux pièges, rencontrés tous les
  deux : une ligne qui tombe *entre* deux colonnes disparaît quand on l'échantillonne au centre des
  texels ; une ligne qui tombe *entre* deux lignes couvre la moitié de chacune et se peint sur deux
  pixels. Le champ de couture est donc évalué sur une grille **4 × suréchantillonnée** (seuil de
  couverture 0,45) et le réseau est décalé d'un **demi-texel** pour tomber sur les centres. Mesuré
  sur la face nord : 31 texels de couture sur 256, une colonne, une ligne.
* **Six tons plats, ancrés sur la peau du skin.** Une interpolation directe du plus sombre au plus
  clair passe *sous* la couleur autour de laquelle le skin a été dessiné, et laisse le ballon dans la
  moitié basse de la ligne. La rampe passe donc **par** `panel_base`, décale chaque ton de −5° à +5°
  (ombres vers le rouge, lumières vers le jaune) et remonte la saturation de 12 % : les icônes sont
  vives, une face éclairée par le client ne l'est pas.
* **La lumière est cuite, mais seulement ce que le client ne sait pas faire** : un terme de
  **limbe** (la face s'assombrit en s'éloignant de son centre — c'est ce qui donne une peau de
  sphère à une face plane), un **creux** d'un ton sur deux texels au bord de chaque face, et **un
  reflet de 2 × 2 texels** sur le texel qui regarde le plus la lumière. Écrit comme un seuil, le
  reflet allumait un quadrant entier de la face éclairée : un reflet se dessine, il ne s'allume pas.
* **Huit panneaux, huit tons.** Chaque octant garde sa propre teinte à ±0,05 : le cube a huit
  panneaux comme le ballon qu'il remplace, et les quatre quadrants d'une face ne sont jamais quatre
  aplats identiques.
* **Six tuiles de 16 px dans un atlas de 48 × 32** (Fire : `48 × 128`, quatre images). Les UV du
  modèle restent dans la grille **0..16** de Minecraft, qui normalise *toutes* les textures quelle
  que soit leur taille : tuile (colonne, ligne) = `[colonne*16/3, ligne*8, (colonne+1)*16/3,
  (ligne+1)*8]`. C'est le piège du dossier : `texture_size`, le champ qui a l'air de changer cette
  grille, n'est **lu que par Blockbench** — l'écrire en pixels d'atlas donne un ballon d'une seule
  couleur, et le wiki qualifie d'« incohérent » tout UV hors de 0..16.
  `theSixFaceTilesTileTheAtlasWithNothingLeftOver` relit les six rectangles du modèle, les remet à
  l'échelle de l'image livrée et exige qu'ils couvrent l'atlas exactement.
* **L'icône d'inventaire est peinte avec les six mêmes tons.** `icon_ball_classic` garde sa
  silhouette de ballon (une icône d'objet se lit comme un objet) mais prend sa rampe dans
  `ball_model.skin_ramp("classic")`, et `icon_language._check_ball_ramp()` fait échouer la
  génération si les deux divergent de plus d'un cran.

Chiffres tenus par les tests, pas par la mémoire :

| ce qui | valeur | où |
| --- | --- | --- |
| éléments du modèle | **1** | `BallModelContractTest#everySkinShipsOneCubeAtTheRadiusTheSimulationCollidesAt` |
| boîte de l'élément | −0,75 … 16,75 | demi-extension 8,75 unités = 0,547 bloc |
| échelle d'affichage de l'entité | 0,52 | d'où **0,285 bloc** = `PhysicsMath.BALL_RADIUS` |
| arêtes du cube | 1,41 × le rayon | le prix du cube, identique à l'ancien ballon |
| atlas | **48 × 32**, 6 tuiles de 16 px, UV en 0..16 | `theSixFaceTilesTileTheAtlasWithNothingLeftOver` |
| couture | **1 texel** (31 texels sur la face nord) | mesuré par `_seam_mask`, relevé dans la recherche |
| couleurs par face | 6 tons + la couture + le reflet | `docs/agents/research/ball-icon.md` §2 |
| pack | **880 252 octets** | `releases/BosketBall-ResourcePack.zip` |

L'orientation des UV suit la table de Blockbench (nord/sud/est/ouest : `v` inversé par rapport à y,
dessus : `v` suit +z, dessous : `v` suit −z). C'est la table que `_face_uv_from_point` implémente
aussi pour l'aperçu : ce que la planche de contact montre est ce que le client dessine, y compris
l'inversion du nord.

À vérifier à l'œil sur `docs/previews/ball_contact_sheet.png` : à 132 px, les coutures doivent être
noires, pleines, **d'un seul texel**, et la croix de chaque face doit être droite — un zigzag d'un
pixel est le signe qu'une couture est retombée entre deux colonnes. À 32 px, la taille réelle d'une
icône d'inventaire, le ballon doit encore se lire comme un ballon et pas comme une caisse en bois.
`docs/previews/ball_icon_sheet.png` place le ballon à 16, 24, 32, 48, 64, 96 et 128 px au-dessus des
57 icônes : c'est là que se juge la question qui compte, « est-ce qu'il a l'air d'appartenir au
pack ». La réponse doit être oui aux trois tailles du haut, pas seulement à celle du bas.

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
10. Regarder le ballon de près : les coutures doivent traverser les arêtes du cube sans cassure,
    faire un texel de large et rester **droites** — un zigzag d'un pixel veut dire qu'une couture est
    tombée entre deux colonnes de la grille de 16. Si une couture s'arrête net sur une arête, c'est
    le classement par direction qui est cassé, pas la texture.
11. Si la balle disparaît encore : `/bb ball` **pendant** qu'elle est invisible (ou juste après), et
    m'envoyer les lignes. `rebuilt 0 time(s)` + `healthy` signifie que le serveur livre bien une
    entité : le problème est côté client, et la ligne le dit. `rebuilt N` nomme le coupable.

## La preuve hors-ligne : la balle est-elle à l'écran, tick par tick ?

Un test de trajectoire ne dit rien de l'image. Tous les autres tests de balle construisent un
`BasketballEntity` avec un monde `null` : ils prouvent la physique d'un tir et restent muets sur le
fait qu'une entité soit réellement livrée au client. C'est précisément l'écart dans lequel vit le
bug « le ballon disparaît quand je tire ».

`src/test/java/fr/bosketball/BallDisplayHarnessTest.java` comble cet écart sans serveur :
`FakeBukkitWorld` est un `Proxy` sur `org.bukkit.World` qui **enregistre** chaque entité spawnée et
rejoue les setters de `Display` (item, transformation, boîte de culling, `viewRange`, télépérations,
`remove()`). Le test appelle le vrai code de production (`launchShotVirtual` puis `tick(...)`) et
vérifie, **à chaque tick**, les cinq seules façons dont une balle peut disparaître alors que le
serveur la simule parfaitement :

| Défaut | Ce que le test lit |
|---|---|
| le display n'a jamais été spawné | `ball.visualDisplayEntity()` non nul et spawné par le monde |
| le display a été supprimé | `valid` toujours vrai, aucun `remove()` en vol |
| le display est vide | un `ItemStack` non nul a bien été passé à `setItemStack` |
| le display est réduit à rien | échelle `x/y/z > 0,20` à chaque tick |
| le client peut le culler | boîte `≥ 1,0 × 1,0` et `viewRange ≥ 1` |
| le display est ailleurs | moins de 2,5 blocs de `position()` |

Trois scénarios sont couverts : un tir complet (60 ticks), une suppression du display **en plein
vol** (il doit revenir en moins de 10 ticks), et un changement de skin en plein vol (il ne doit
jamais supprimer l'entité).

### Ce que le harness a demandé pour exister

`ItemStack` a besoin du registre d'items, et `RegistryAccessHolder` se remplit par `ServiceLoader`.
Sans serveur, ce `ServiceLoader` était vide : le premier `new ItemStack(...)` de la suite tuait
`org.bukkit.Registry` **pour tout le JVM** (`NoClassDefFoundError: Could not initialize class`), et
plus aucun test ne pouvait toucher un item. D'où
`src/test/java/fr/bosketball/TestRegistryAccess.java`, déclaré dans
`src/test/resources/META-INF/services/io.papermc.paper.registry.RegistryAccess` : le registre
d'items rend un jeton (c'est tout ce que le constructeur d'`ItemStack` vérifie) et le registre de
blocs **lève**, exactement comme avant, pour que les centaines d'assertions de résolution de
matériaux gardent leur sens.

### Trace de référence (tir de 6 blocs, 61 ticks)

Extrait de la trace produite avec le harness — `display alive` est la colonne qui compte :

```
| tick | ball (x/y/z) | display alive | item | scale x/y/z | cull box | view | drift | hitboxes |
| 0  | 0.00/65.60/-6.00 | yes | item | 0.52/0.52/0.52 | 1.10x1.10 | 2.5 | 0.00 | 1 |
| 21 | 0.00/76.90/4.66  | yes | item | 0.52/0.52/0.52 | 1.10x1.10 | 2.5 | 0.00 | 1 |
| 44 | 0.00/66.62/13.95 | yes | item | 0.52/0.52/0.52 | 1.10x1.10 | 2.5 | 0.00 | 1 |
| 60 | 0.00/65.52/14.51 | yes | item | 0.52/0.52/0.52 | 1.10x1.10 | 2.5 | 0.00 | 1 |
```

Aucun tick sans display : **dans ce modèle**, le serveur livre une entité valide, avec un item, une
échelle et une boîte de culling correctes, du lâcher jusqu'à l'immobilisation au sol.

### Ce que cette preuve ne couvre pas

Le harness prouve **ce que le serveur envoie**, pas ce qu'un client en dessine. Trois choses restent
hors de sa portée, et aucune ne peut être vérifiée dans cet environnement :

1. le rendu lui-même (il faudrait un client graphique) ;
2. le contenu réel de l'item — hors serveur, `ItemStack#getType()` passe par un délégué CraftBukkit
   absent, donc le harness ne peut vérifier que « un item a été fourni » ;
3. ce que l'`item_model` du pack résout chez le joueur (pack absent ou périmé côté client).

Le bac à sable n'a **aucun accès réseau** à `api.papermc.io`, `repo.papermc.io` ni
`piston-meta.mojang.com` (connexion refusée, HTTP 000) : aucun jar serveur ne peut y être téléchargé,
et un client Minecraft graphique n'y tourne pas. La vérification en jeu reste donc à faire côté
serveur réel ; les points à regarder sont listés en fin de document.

## Deux correctifs de cette passe

1. **L'item de la balle ne peut plus coûter la balle.** `safeBallItem()` retombait sur le skin
   `CLASSIC` — et si *celui-là* échouait aussi, l'exception sortait du `Consumer` de spawn,
   `spawnVisualBall` la rattrapait et laissait `visualBall = null` : plus aucune entité, pour tout le
   reste de la possession. Il y a désormais un troisième recours (une tête simple, sans modèle de
   pack), et le changement de skin en vol passe par le même chemin au lieu d'appeler `createBallItem`
   sans garde.
2. **Un replay qui ne démarre pas ne prend plus la balle.** `playInstantGoalReplay` cachait la balle
   vivante au spectateur **avant** de construire le replay (ballon de replay, mannequins, caméra) et
   n'enregistrait son état qu'à la fin. Une exception au milieu — un item impossible à construire, un
   `spawn` refusé, un téléport annulé — laissait la balle masquée **sans aucun état sur aucune
   horloge pour la rendre** : la balle disparaissait exactement au moment où le panier entrait, et
   pour de bon. L'état est maintenant enregistré avant le premier spawn, et tout échec passe par
   `stopWatching(viewer, true)` : pose, mode de jeu et balle rendus, une ligne de log, une seule.
