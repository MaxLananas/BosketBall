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

## Couverture

`src/test/java/fr/bosketball/BallVisibilityTest.java` — 14 tests : absence de tout
`hideEntity`/`hiddenGatherViewerId` dans la balle, le geste de charge qui reste visible, la boîte de
culling dimensionnée, les six départs qui commencent par `presentBallForLaunch()` avant leur
garde-fou, la reconstruction = retrait + spawn + `revealToEveryone`, l'intervalle de redéclaration,
le compteur de tempête, le quaternion normalisé, le replay qui re-cache à chaque image, et les
invariants déjà tenus (tag `bb_live_ball`, tick cassé, skin cassé, centre inutilisable, dérive de
l'affichage).

## À retester en jeu

1. Tirer : la balle reste à l'écran du tireur du lâcher jusqu'au panier.
2. Charger sans tirer (relâcher le clic dans le vide) : la balle ne disparaît pas non plus.
3. Passer, dunker, balle libre : toujours une balle visible.
4. Regarder un replay de panier : la balle vivante ne réapparaît pas dans le plan, et elle est bien
   revenue à la fin du replay.
5. `grep "rebuilt" logs/latest.log` après une session : aucune ligne de tempête.
