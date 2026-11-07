# Fond de tab list (cadre doré)

Le fond du `[TAB]` vient de `tablist.zip`, le bundle fourni par le propriétaire. C'est un pack de
config pour un plugin de « font images » : il ne contient qu'**une** texture utile,
`textures/tablist/tabsponsor.png` (256×256), les autres fichiers du zip n'étant que des entrées de
config (`configs/config.yml`) pointant vers des PNG absents du zip.

Aperçu : `docs/previews/tablist.png` (généré par `tools/preview_tablist.py`).

## 1. Ce que dit le zip

```yaml
info:
  namespace: "tablist"
font_images:
  tabsponsor:
    path: "tablist/tabsponsor.png"
    y_position: 660        # ascent du glyphe
    scale_ratio: 940       # hauteur du glyphe
```

Autrement dit : **une image dessinée comme un glyphe de police géant**, placée dans le header de la
tab list. C'est exactement la technique utilisée ici, mais implémentée dans le plugin du serveur
(pas besoin du plugin tiers).

La texture elle-même : un **cadre doré** (ornements aux 4 coins, écussons en haut et en bas au
centre) rempli d'un **noir à 25 %** (`(0,0,0,64)`), la bande visible occupant les lignes 107→205 du
canevas carré.

> **Round 29 — ce voile à 25 % n'est plus livré.** `tools/tablist_frame.py` repeint chaque pixel de
> ce voile en un **dégradé opaque** (voir §3 bis) : c'est lui qui faisait apparaître le mur clair du
> lobby à travers le haut du cadre et le sol sombre à travers le bas, donc un cadre « moitié plus
> foncé ». Le PNG source du zip reste, lui, intact et versionné.

## 2. Ce que le pack contient

| Fichier | Rôle |
|---|---|
| `assets/tablist/textures/tablist/tabsponsor.png` | **le PNG du zip, octet pour octet**, sous son propre namespace |
| `assets/bosketball/textures/font/tab_frame_r01.png` … `tab_frame_r20.png` | un cadre 384×H **par nombre de joueurs** (1 → 20 lignes) |

Les quatre tailles sont découpées dans **les pixels du zip** (`tools/tablist_frame.py`) en
**5 colonnes × 3 lignes** : les 4 coins, l'écusson du haut et celui du bas sont conservés
**à l'identique**, et seuls les quatre segments de rail droits (haut, bas, gauche, droite) sont
étirés (`NEAREST`, donc aucun flou, et le rail doré de 1 px reste un rail de 1 px). Aucun ornement
n'est jamais déformé, et le cadre s'adapte à la largeur comme à la hauteur.

Largeur : **384 px** — c'est la largeur qui englobe la plus large des lignes (logo 184, lignes de
joueurs, pied de page avec le domaine).

**Hauteur (Round 28) : une taille par nombre de joueurs, plus de paliers.** La tab list est posée
sur une grille de **9 px par ligne** (1 ligne de cadre + le bloc logo + la ligne des compteurs, puis
une ligne par joueur, puis les deux lignes du pied de page). Une capture d'écran 2× d'une tab list à
1 joueur a permis de mesurer la fin réelle du contenu : **119,5 px** sous le haut du cadre, alors que
l'ancien cadre de 200 px descendait sa barre dorée à 189 px — soit **69 px de cadre vide** sous le
pied de page.

La hauteur est donc calculée, pas devinée :

```
H(lignes de joueurs) = 139 + 9 × (lignes − 1)        # 139 px pour 1 joueur … 310 px pour 20
```

139 = 119,5 (dernière ligne de texte) + 8 px de marge + 11 px (barre dorée + écusson bas). Les
anciens paliers (200/250/292/332) utilisaient un pas de 8,3 px par ligne et une base ~65 px trop
haute : d'où la bande vide que le propriétaire voyait sous le pied de page.

## 3. Côté plugin

* `Glyphs.TAB_FRAME` = `U+E370…U+E383` (20 glyphes, un par nombre de joueurs),
  `TAB_FRAME_MAX_ROWS = 20`, `TAB_FRAME_WIDTH = 384`, `TAB_FRAME_FIRST_ROW_HEIGHT = 139`,
  `TAB_FRAME_LINE_HEIGHT = 9` ;
* `Glyphs.tabFrameComponent(rows)` prend directement le glyphe du nombre de joueurs (1…20),
  `Glyphs.tabFrameHeight(rows)` donne la hauteur correspondante ;
* `TabListManager` l'écrit **en première ligne du header** : `frame + STATIC_LOGO_BLOCK`.

Pourquoi le header ? Et surtout : **dans quel ordre le client dessine-t-il ces trois blocs ?**

Round 28 affirmait « joueurs → header → footer », donc un cadre *au-dessus* des noms. **C'est faux,
et c'était le cœur du bug.** Preuve mesurée sur la capture du propriétaire : le pseudo s'affiche en
`#F1F5F9` (241,245,249). Si un voile noir à 25 % passait par-dessus, il serait tombé à ~(181,184,187).
Le nom est intact → **le header (donc notre cadre) est peint AVANT les lignes de joueurs**. L'ordre
réel est : **header → lignes de joueurs → footer**.

Conséquence : le cadre est *derrière* tout le contenu. Il peut donc être **totalement opaque** sans
jamais assombrir un nom, une ligne ou le pied de page. C'est ce que fait le Round 29.

Les lignes du header avancent de 9 px fixes, donc un glyphe de 220 px de haut ne décale rien.

## 3 bis. Round 29 — le vrai correctif du fond

Les deux défauts signalés venaient du même endroit : **le remplissage du cadre n'était pas une
couleur, c'était un voile**, et le cadre laissait 10 px transparents au-dessus de sa barre dorée.

| Symptôme vu en jeu | Cause réelle | Correctif |
|---|---|---|
| « une partie de foncé tout en haut » | le glyphe commence 10 px **au-dessus** du rail doré : ces lignes étaient transparentes, donc le monde (mur clair du lobby) passait à travers et tranchait avec le reste | le remplissage démarre **exactement sous le rail** ; au-dessus il ne reste que l'écusson central, volontairement posé en badge |
| « la moitié inférieure plus foncée » | le voile `(0,0,0,64)` laissait voir le décor : sol sombre en bas, mur clair en haut → dégradé involontaire. Les cartes de ligne (`tab_row_*`, alpha 232) et le header/footer n'arrangeaient rien | le voile est **repeint en opaque** : `#12222C → #081016`, smoothstep, plus un liseré clair sous le rail haut et une ligne d'ombre au-dessus du rail bas |

Le repaint ne touche **que** les pixels du voile (`alpha ≤ 96` et noir) : rails, écussons et ornements
de coin restent les pixels du zip, à l'identique. Résultat : le panneau est identique quel que soit
ce que le joueur regarde, et plus aucun texte n'est assombri.

Régénération : `python3 tools/tablist_frame.py` (les 20 tailles), puis rebuild du pack.

Le provider est déclaré dans `assets/minecraft/font/default.json` (`height` = hauteur du PNG,
`ascent` = 9) : le haut du cadre tombe 2 px au-dessus de la première ligne du header.

## 4. Régénérer

```bash
python3 tools/tablist_frame.py     # recopie le ZIP + découpe les 20 tailles (1…20 joueurs)
python3 tools/preview_tablist.py   # docs/previews/tablist.png
python3 tools/build_plugin.py      # pack + jar
```

Test : `tabListUsesTheUploadedFrameTexture` vérifie que la texture livrée est **identique au zip**
(SHA-256), que les **20** tailles existent en 384 px de large, que chaque hauteur suit la formule
`135 + 9 × (lignes − 1)` (PNG **et** provider `default.json`), que les codepoints `U+E370…U+E383`
sont réservés et que le header appelle `tabFrameComponent`.

## 5. Si un jour tu ajoutes les autres rangs

Le `config.yml` du zip déclare 13 cadres (`tabadmin`, `tabbaron`, `tabkral`, `tabkurucu`, `tablord`,
`tabmod`, `taboyuncu`, `tabpartner`, `tabrehber`, `tabsoylu`, `tabsponsor`, `tabvezir`,
`tabyetkili`) mais **seul `tabsponsor.png` est fourni**. Envoie les autres PNG et chaque rang pourra
avoir son propre cadre : il suffit d'ajouter les fichiers et une ligne dans `tools/tablist_frame.py`.

## 6. Round 32 — cadre nu, hauteur exacte

Deux reproches du propriétaire : « pourquoi maintenant il y a un fond foncé ? enlève la partie
toujours sombre » et « il faut que ça aille tout en haut parce qu'il manque une partie encore ».

* **Plus aucun remplissage.** Le dégradé opaque du Round 29 (18,34,44 → 8,16,22) est supprimé :
  l'intérieur du cadre est **transparent**, comme la tab list vanilla. Il ne reste que les rails,
  les ornements d'angle et les deux blasons, plus un liseré de 1 px à 35 % sous le rail haut et
  au-dessus du rail bas pour le relief.
* **La hauteur du cadre = la hauteur du contenu.** La capture du propriétaire (échelle GUI 2× :
  rails à x=31/797, panneau de y=2 à y=258) donne **128 px visibles** alors que le cadre mesurait
  139 px : le client rognait donc les 11 px du bas, ce qui expliquait exactement « il manque une
  partie » (rail + blason jamais affichés). Le contrat est maintenant explicite :
  le header commence par une ligne vide qui porte le blason haut, le footer se termine par **deux**
  lignes vides qui portent le rail bas et son blason, donc 15 lignes client = **135 px** pour un
  joueur, `+9 px` par joueur (306 px à 20 joueurs).
* Le `height` des 20 providers de `font/default.json` est régénéré en même temps que les PNG.

Aperçu : `docs/previews/tablist-1row.png` (situation du propriétaire, 1 joueur) et
`docs/previews/tablist.png` (10 joueurs), tous deux rendus sur un faux lobby pour montrer la
transparence.

## 7. Round 34 — l'ordre des lignes (les pseudos qui se croisaient)

Symptôme signalé par le propriétaire : dans le `[TAB]`, deux joueurs **échangeaient leur place**
toutes les secondes, en entraînement comme en match.

Cause : chaque entrée de tab list a un **`list order`** (`Player#setPlayerListOrder`, Minecraft
1.21.2+, exposé par Paper). Tout le monde restait à `0`, donc **toutes les lignes étaient à égalité**
et le client les disposait dans l'ordre que lui rendait sa map interne — un ordre qu'il ne garantit
pas. Rien dans le plugin ne « bougeait » les pseudos : c'est le client qui réordonnait des lignes
ex aequo.

Correctif : `TabListManager` calcule un **ordre total** et l'applique (`setPlayerListOrder(i)`) :

1. en match : `IGNIS` (0) → `FROST` (1) → spectateurs (2) ;
2. puis points marqués, décroissant ;
3. puis nom (insensible à la casse) ;
4. puis UUID — **aucune égalité possible**, donc plus aucun ordre alternatif.

Le paquet `UPDATE_LIST_ORDER` n'est envoyé que lorsqu'une ligne change réellement de rang
(`lastListOrder`), et l'entrée est oubliée au départ du joueur.

## 8. Round 34 — le panier décide, pas le tireur

Un panier marqué dans **son propre** panier créditait le tireur en entraînement (`creditedTeam =
shooterTeam`), ce qui rendait la règle incohérente avec le reste du jeu. Désormais
`creditedTeam = hoopScoringSide` **dans tous les modes** : le panier désigne l'équipe qui marque, et
si le tireur appartient à l'autre équipe c'est un **own basket** (bannière `OWN BASKET +n`, sous-ligne
`FOR <équipe>`, aucun point de statistique, aucune pièce pour le tireur).

## 9. Round 36 — la « responsive » du panneau (audit de 40+ cas)

Le cadre doré n'est pas une image fixe : le pack livre **un glyphe par nombre de lignes** (`\uE370`
pour 1 joueur … `\uE383` pour 20). Le contrat est arithmétique, pas approximatif :

```
en-tête  : 1 ligne de cadre + (bloc logo : vide + 5 bandes + vide) + 1 ligne compteurs =  9 lignes
lignes   : un joueur = une ligne client                                               =  N lignes
pied     : vide + rang + saison + 2 lignes réservées (rail + écusson)                 =  5 lignes
total    : (9 + N + 5) x 9 px = 126 + 9N px  ==  135 + 9 x (N - 1)
```

C'est **exactement** ce que génère `tools/tablist_frame.py` et ce que renvoie
`Glyphs.tabFrameHeight(rows)`. À 1 joueur le cadre fait 135 px, à 20 joueurs 306 px : il colle au
contenu au pixel près, et les deux lignes vides du pied sont ce qui empêche le rail du bas et son
écusson de passer sur le texte.

### Les cas testés (suite JUnit, 154 tests au total)

| Groupe | Cas | Ce qui est vérifié |
|---|---|---|
| Cadre 1→20 joueurs | 20 | hauteur = 135 + 9(N-1) = hauteur réelle du PNG, largeur 384, glyphe `\uE370+N-1` |
| Ornements | 20 x 2 | l'écusson haut et l'écusson bas sont **identiques** à ceux du cadre 1 ligne (jamais étirés) |
| Rails | 20 | aucun pixel transparent sur le rail gauche/droit entre y=12 et y=h-12 (un nine-slice qui oublie de répéter le rail laisse un trou) |
| Intérieur | 20 | entre x=8 et x=376 le cadre ne peint **rien** (il ne peut pas recouvrir un pseudo) |
| Lignes vides | 20 | exactement 3 lignes vides : les deux écartements d'écusson + celle du bas |
| Comptage en ligne | 27 | 0, -12, 1…20, 21, 34, 50, 100, 999 joueurs -> glyphe borné 1…20, jamais de caractère hors pack |
| Marges | 20 | marge basse identique pour N=1…20 (le pied ne peut pas être coupé à une seule taille) |
| Largeur | 44 | noms d'équipe de 16 caractères, saison de 46 caractères, 9 999 999 pièces, 999 ms : la ligne reste dans 384 - 24 px |

### Ce qui débordait vraiment

La largeur, elle, n'était pas protégée : les noms d'équipe de party vont jusqu'à 16 caractères, le
libellé de saison vient de la config et le compteur de pièces n'est pas borné. Deux noms longs
poussaient la ligne d'en-tête hors du cadre doré (le texte dépassait du panneau des deux côtés).

Correctif : `TabListManager.fitLine(...)` + `Glyphs.fitTextToWidth(...)` bornent désormais **chaque
libellé variable** — les deux noms d'équipe (chacun reçoit la moitié de la place restante), le
libellé de rang/saison et le nom de saison — avec des points de suspension quand ça ne rentre pas.
Un libellé de saison / rang trop long est aussi ajouté à la clé de cache de l'en-tête, sinon le
panneau ne se rafraîchissait pas quand la saison changeait en cours de session.

### Aperçus

`docs/previews/tablist-responsive.png` (1, 2, 5, 10, 15, 20 joueurs) et les fichiers
`tablist-{N}row.png`. `python3 tools/preview_tablist.py N` en génère un nouveau.

---

## 10. Round 38 : le fond du [TAB] disparaît

Le propriétaire a demandé de retirer le « tab background ». Ce qui restait à l'écran était le
**cadre doré nine-slice** que le serveur peignait derrière tout le panneau : son intérieur est
transparent, donc on ne voyait au travers que le fond du client — un rectangle gris avec un liseré
doré flottant autour. Résultat : un panneau qui a l'air cassé, deux fois plus de « fond » que
nécessaire, et un bas de cadre qui tombe sur la dernière ligne.

Correctif : le cadre n'est plus dessiné par défaut.

```yaml
tab-list:
  frame: false      # plus de cadre, plus de fond serveur : le [TAB] ressemble au [TAB] vanilla
  rail-lines: 2     # lignes vides réservées sous le pied, uniquement quand frame: true
```

* `frame: false` (défaut) : l'en-tête ne contient plus que le logo et les stats, le pied que les
  infos du joueur — exactement ce qu'on voit sur la capture « vanilla » du propriétaire.
* `frame: true` : l'ancien panneau doré revient à l'identique, avec ses lignes de rail réservées
  (`rail-lines`, 0 à 6) pour que le bas du cadre ne coupe jamais la dernière ligne.
* `TabListManager.setPanel(frame, railLines)` est branché sur la config au démarrage
  (`BosketBallPlugin`), et la clé de cache de l'en-tête/pied est vidée pour que le changement
  s'applique immédiatement avec `/bb reload`.

Le reste du panneau (logo, ligne de score, pastilles de rang, tri total des lignes, largeur bornée
à 384 - 24 px) ne change pas : seuls le cadre et ses lignes réservées dépendent du réglage.
