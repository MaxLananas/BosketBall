# Disposition des menus et badges de hotbar

Contrat de mise en page, vérifié par les tests (`lobbyHotbarIsSevenCentredBadgesBackedByThePack`).
Toute modification d'un slot doit se faire ici **et** dans le test.

## 1. La hotbar du lobby

Sept portes, une icône chacune, **centrées sur les slots 1 à 7**. Les deux bords restent libres :
le slot 8 accueille le badge « Leave Queue » pendant la recherche de partie, le slot 0 est vide.

```
slot   0      1      2      3         4          5         6              7
       ──   play   shop  clothing  catalogue  member  medal_gold     cup_gold
```

| Slot | Sprite (`ref_*`) | Clic gauche | Clic droit | Shift |
|---|---|---|---|---|
| 1 | `ref_play` ▶ | Modes de jeu / files | — | — |
| 2 | `ref_shop` 🛍 | Boutique | Collection | — |
| 3 | `ref_clothing` 👕 | Cosmetics & locker | Tags de chat | — |
| 4 | `ref_catalogue` 📕 | Hub principal | — | — |
| 5 | `ref_member` 🧑 | Amis | Groupe (party) | Guilde |
| 6 | `ref_medal_gold` 🎖 | Guide des rangs | — | — |
| 7 | `ref_cup_gold` 🏆 | Classements | — | — |

Règles :

* **Aucun émoji** dans les noms d'objets : petites capitales unicode, comme les pilules de
  navigation. (Les émojis du tableau ci-dessus ne servent qu'à décrire le sprite dans cette doc.)
* Les 7 slots sont **symétriques** : le slot `s` a toujours un jumeau en `8 - s`, sinon la
  rangée paraît de travers. C'est le test qui l'exige.
* Les sprites viennent de la **bibliothèque d'icônes du pack** (`textures/item/icons/`, 167
  sprites `ref_*` importés depuis `assets/ref-icons`), pas d'un jeu d'icônes maison : la hotbar,
  le hub et les menus parlent donc la même langue visuelle. Sept silhouettes bien distinctes
  (flèche verte, sac coloré, maillot blanc, livre rouge, personnage, médaille, coupe dorée).
* Chaque sprite doit exister en **trois fichiers** dans le pack : texture, modèle, définition
  d'item. Le test échoue si l'un manque ou si l'id ne commence pas par `ref_`.

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

## 3. Regénérer l'aperçu

```bash
python3 tools/preview_hotbar.py        # docs/previews/hotbar-row.png (lit le contrat dans BallSkin)
python3 tools/preview_gui_light.py     # docs/previews/gui_v5_light_menus.png (six menus)
```

`preview_hotbar.py` relit `LOBBY_HOTBAR_SLOTS` / `LOBBY_HOTBAR_MODELS` dans `BallSkin.java` et
échoue si la rangée n'est plus exactement 1..7, si les deux listes divergent, ou si un sprite
manque dans le pack : impossible de livrer un aperçu qui ne correspond pas au code.

Après tout changement de pack : `python3 tools/build_plugin.py` (le pack est rezippé, le SHA-1
change → nouveau tag de pack, voir `docs/resource-pack-mirrors.md`).
