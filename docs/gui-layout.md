# Disposition des menus et badges de hotbar

Contrat de mise en page, vérifié par les tests (`lobbyHotbarIsSevenCentredBadgesBackedByThePack`).
Toute modification d'un slot doit se faire ici **et** dans le test.

## 1. La hotbar du lobby

Sept portes, une icône chacune, **centrées sur les slots 1 à 7**. Les deux bords restent libres :
le slot 8 accueille le badge « Leave Queue » pendant la recherche de partie, le slot 0 est vide.

```
slot   0      1       2      3        4      5        6       7      8
       ──   play    shop  locker    menu  social   ranks    top   (leave)
```

| Slot | Badge | Clic gauche | Clic droit | Shift |
|---|---|---|---|---|
| 1 | `hub_modes` ᴘʟᴀʏ | Modes de jeu / files | — | — |
| 2 | `hub_shop` ꜱʜᴏᴘ | Boutique | Collection | — |
| 3 | `hub_locker` ʟᴏᴄᴋᴇʀ | Cosmetics & locker | Tags de chat | — |
| 4 | `hub_menu` ᴍᴇɴᴜ | Hub principal | — | — |
| 5 | `hub_friends` ꜱᴏᴄɪᴀʟ | Amis | Groupe (party) | Guilde |
| 6 | `hub_ranks` ʀᴀɴᴋꜱ | Guide des rangs | — | — |
| 7 | `hub_top` ᴛᴏᴘ | Classements | — | — |

Règles :

* **Aucun émoji** dans les noms d'objets (⚡ 👕 🛒 🏷 👥 📖 🏆 ont été retirés) : les noms
  utilisent les petites capitales unicode, comme les pilules de navigation.
* Les 7 slots sont **symétriques** : le slot `s` a toujours un jumeau en `8 - s`, sinon la
  rangée paraît de travers. C'est le test qui le garantit.
* Chaque badge existe en **trois fichiers** dans le pack : texture 32×32, modèle, définition
  d'item. Le test échoue si l'un manque.

## 2. Le hub : une grille, pas un tas

`GuiMenuManager.openMainHub()` suit une grille dont chaque rangée est centrée sur la colonne 4 :

```
rangée 1   [13] profil
rangée 2   [20] play     [22] shop      [24] locker
rangée 3   [29] social   [31] ranks     [33] top
rangée 4   [37] practice [39] progress  [41] history [43] settings
rangée 5   [45] leave    [46] home      [49] fermer  [52] season   [53] coin
```

* 3 items → colonnes 2, 4, 6 ; 4 items → colonnes 1, 3, 5, 7. Jamais 3 items sur une rangée
  en laissant un trou au milieu.
* Le slot 53 est la « porte de coin » : studio d'arène pour le staff, invitation Discord pour
  les joueurs. Un seul slot, deux publics, aucun menu vide.
* Season reste accessible par le rail standard (`GuiChrome.SLOT_SEASON = 52`), il n'a plus
  besoin de voler un slot de la grille.
* `PLAY_MODES` : la porte « Training & AI » est passée du slot 38 (bord gauche) au slot 40
  (colonne centrale) — c'était la seule rangée asymétrique du menu des modes.

## 3. Les badges eux-mêmes

`tools/hotbar_icons.py` fabrique la famille entière (aucune dépendance réseau, Pillow seulement) :

* 32×32, plaque arrondie, dégradé vertical, liseré sombre, reflet en haut, glyphe blanc avec
  une ombre douce ;
* une couleur par porte (orange = jouer, or = boutique, ciel = locker, violet = menu,
  émeraude = social, rose = rangs, jaune = classements) ;
* dessinés en supersampling ×8 puis réduits : nets en 16×16 comme en 32×32.

```bash
python3 tools/hotbar_icons.py --preview
#   -> resourcepack/assets/bosketball/textures/item/hub/hub_*.png
#   -> models/item/hub_*.json + items/hub_*.json
#   -> docs/previews/hotbar-icons.png (la planche) et docs/previews/hotbar-row.png (la rangée)
python3 tools/preview_gui_light.py     # docs/previews/gui_v5_light_menus.png
```

Après toute régénération d'icônes : `python3 tools/build_plugin.py` (le pack est rezippé, le
SHA-1 change → nouveau tag de pack, voir `docs/resource-pack-mirrors.md`).
