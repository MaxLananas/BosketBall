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

Pourquoi le header ? La tab list de Minecraft se dessine dans cet ordre : joueurs → header →
footer. Un glyphe placé dans le header passe donc **par-dessus** les lignes de joueurs : c'est ce
qui permet au cadre (dont l'intérieur n'est qu'un noir à 25 %) d'encadrer les noms sans les cacher.
Les lignes du header avancent de 9 px fixes, donc un glyphe de 292 px de haut ne décale rien.

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
`139 + 9 × (lignes − 1)` (PNG **et** provider `default.json`), que les codepoints `U+E370…U+E383`
sont réservés et que le header appelle `tabFrameComponent`.

## 5. Si un jour tu ajoutes les autres rangs

Le `config.yml` du zip déclare 13 cadres (`tabadmin`, `tabbaron`, `tabkral`, `tabkurucu`, `tablord`,
`tabmod`, `taboyuncu`, `tabpartner`, `tabrehber`, `tabsoylu`, `tabsponsor`, `tabvezir`,
`tabyetkili`) mais **seul `tabsponsor.png` est fourni**. Envoie les autres PNG et chaque rang pourra
avoir son propre cadre : il suffit d'ajouter les fichiers et une ligne dans `tools/tablist_frame.py`.
