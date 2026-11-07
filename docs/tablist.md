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
