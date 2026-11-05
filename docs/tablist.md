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
| `assets/bosketball/textures/font/tab_frame_150.png` | cadre 256×150 (≤ 4 lignes de joueurs) |
| `…/tab_frame_208.png` | 256×208 (≤ 10 lignes) |
| `…/tab_frame_240.png` | 256×240 (≤ 15 lignes) |
| `…/tab_frame_292.png` | 256×292 (≤ 20 lignes) |

Les quatre tailles sont découpées dans **les pixels du zip** (`tools/tablist_frame.py`) :
les 22 premières et 22 dernières lignes — coins + écussons — sont conservées **à l'identique**, et
seuls les deux rails verticaux et le fond plat sont étirés (`NEAREST`, donc aucun flou, et le rail
doré de 1 px reste un rail de 1 px). Aucun ornement n'est déformé.

## 3. Côté plugin

* `Glyphs.TAB_FRAME` = `U+E370…U+E373`, `TAB_FRAME_MAX_ROWS` = `4 / 10 / 15 / 20`,
  `TAB_FRAME_WIDTH = 256` ;
* `Glyphs.tabFrameComponent(rows)` choisit la taille selon le nombre de joueurs en ligne ;
* `TabListManager` l'écrit **en première ligne du header** : `frame + STATIC_LOGO_BLOCK`.

Pourquoi le header ? La tab list de Minecraft se dessine dans cet ordre : joueurs → header →
footer. Un glyphe placé dans le header passe donc **par-dessus** les lignes de joueurs : c'est ce
qui permet au cadre (dont l'intérieur n'est qu'un noir à 25 %) d'encadrer les noms sans les cacher.
Les lignes du header avancent de 9 px fixes, donc un glyphe de 292 px de haut ne décale rien.

Le provider est déclaré dans `assets/minecraft/font/default.json` (`height` = hauteur du PNG,
`ascent` = 10) : le haut du cadre tombe 2 px au-dessus de la première ligne du header.

## 4. Régénérer

```bash
python3 tools/tablist_frame.py     # recopie le ZIP + découpe les 4 tailles
python3 tools/preview_tablist.py   # docs/previews/tablist.png
python3 tools/build_plugin.py      # pack + jar
```

Test : `tabListUsesTheUploadedFrameTexture` vérifie que la texture livrée est **identique au zip**
(SHA-256), que les 4 tailles existent à 256 px de large, que les codepoints sont bien dans
`default.json` et que le header appelle `tabFrameComponent`.

## 5. Si un jour tu ajoutes les autres rangs

Le `config.yml` du zip déclare 13 cadres (`tabadmin`, `tabbaron`, `tabkral`, `tabkurucu`, `tablord`,
`tabmod`, `taboyuncu`, `tabpartner`, `tabrehber`, `tabsoylu`, `tabsponsor`, `tabvezir`,
`tabyetkili`) mais **seul `tabsponsor.png` est fourni**. Envoie les autres PNG et chaque rang pourra
avoir son propre cadre : il suffit d'ajouter les fichiers et une ligne dans `tools/tablist_frame.py`.
