# Court Console v5 — GUI claires, détaillées, iconisées

> Livré le 4 novembre 2026 sur `arena/a1145fcf-bosketball`.
> Build de référence : **224 classes, 0 erreur · 88 tests OK · pack 3 796 641 B · jar 4 574 793 B**.

![Six menus en v5](previews/gui_v5_light_menus.png)

La v4 était une coque bleu marine. La v5 renverse la matière : **une plaque d'aluminium
claire, brossée, sertie de vis**, et des puits sombres qui font ressortir les icônes.

---

## 1. Ce qui change par rapport à la v4

| | v4 (navy) | v5 (light) |
|---|---|---|
| Corps | Navy `#10161F` | Aluminium `#E8EFF7 → #ACB9CB` avec grain de usinage |
| Cadre | 1 bevel | Cadre 4 couches (clé sombre, jonc, clé, chrome) + **vis 3×3** aux 4 coins + **ouïes** de chaque côté du titre |
| Titre | Ruban blanc | **Plaque ardoise** rivetée, bande de légende encrée (le texte blanc reste lisible), soulignement accent |
| Slots | Puits à anneau sombre (fusionnaient verticalement) | **Douille métallique 18×18** + creux 16×16 : deux slots voisins ne fusionnent plus jamais |
| Boutons | Accent dessiné autour du puits | Douille accent + face de touche, **échoppe d'épaule** dans les marges |
| Barre de navigation | Bandeau cousu | **Seam ombré + douilles accent** sur 45/49/53 |
| Détail | ~plat | Vis, ouïes, grain, double bevel, gousset, échoppes, puces d'accent |

**Contrat de géométrie inchangé** (rien à retoucher côté Java ni côté client) :

```
largeur 176 · haut de conteneur y=7 · puits (r,c) → texture (8+18c, 18+18r)
glyphe (8+18c, 25+18r) 16×16 · hauteur = 7 + 18 + 18×lignes + 4 (6→137, 5→119, 4→101, 3→83)
provider bitmap ascent 20 · avance d'overlay 177 px
```

L'overlay doit rester **100 % opaque** (`assert_opaque_cover`) : il est peint par-dessus
`textures/gui/container/<menu>.png` à Δy = −7.

## 2. Règles de la planche

1. **Une seule plaque claire** par menu, un seul creux par slot, **un accent par menu**.
2. **Rien ne traverse une gouttière** : le détail vit dans la plaque, les marges et la douille.
3. L'accent ne sert qu'à trois choses : soulignement de plaque, puces de navigation, douilles de porte.

Accents utilisés : orange (hub/play/shop), or (saison/classements/rangs), glace (réglages/quêtes/partie/bot),
menthe (guilde/confirm), violet (tags/historique/carte joueur/studio), rose (cosmétiques/mail),
rouge (admin/reports/warnings, hub opérateur).

## 3. Les icônes de référence

Le dépôt contient **207 sprites 16×16** (planche d'icônes GUI classique : contour 1 px, aplats saturés,
reflet en haut à gauche). `tools/import_ref_icons.py` en importe **167** — le reste étant des cadres
256×256 ou des captures — et génère, pour chacun :

* `textures/item/icons/ref_<nom>.png` (alpha **binarisé** 0/255, sinon le test de pixel-art échoue),
* `models/item/ref_<nom>.json` (même bloc `display` que les icônes maison),
* `items/ref_<nom>.json` (définition `minecraft:model`, obligatoire en 1.21.4+),
* `docs/ref-icons.md` (catalogue source → nom).

Côté Java, tout menu appelle simplement `customIcon("ref_<nom>", …)`. Les anciens noms
(`icon_podium`, `icon_history`, …) ont été remplacés partout : **15 fichiers**, 89 références
dans `GuiMenuManager` seul. Exemples de correspondances :

| Ancien | Nouveau | Où |
|---|---|---|
| `icon_arena` | `ref_play` / `ref_arrow_big_right` | porte PLAY du hub |
| `icon_podium` | `ref_cup_gold` | classements |
| `icon_history` | `ref_clock` | historique des matchs |
| `icon_tags` | `ref_bag` | collection |
| `icon_friends` | `ref_friends` | social |
| `icon_training` | `ref_dice` | practice |
| `icon_settings` | `ref_gear` | réglages |
| `icon_book` | `ref_medal_gold` | guide des rangs |
| `icon_leave` | `ref_exit` | quitter |
| `icon_back` | `ref_arrow_left` | rail de navigation |

Le rail (`GuiChrome`) et les menus de progression/guilde/tutoriel utilisent désormais
`ref_home`, `ref_arrow_left`, `ref_cross`, `ref_trophy`, `ref_check`, `ref_lock`, `ref_gem`,
`ref_coins`, `ref_star`, `ref_skull`, `ref_compass`, `ref_upgrade`, `ref_rewards`, etc.

## 4. Outils

| Commande | Effet |
|---|---|
| `python3 tools/import_ref_icons.py` | (ré)importe les sprites de référence + modèles + définitions + catalogue |
| `python3 tools/generate_resourcepack.py` | régénère les 26 overlays, les conteneurs vanilla, le pack et son SHA-1 |
| `python3 tools/preview_gui_light.py` | compose `docs/previews/gui_v5_light_menus.png` : overlay + icônes réelles aux positions clients |
| `python3 tools/build_plugin.py` | compile, resynchronise le SHA-1, lance les 88 tests, empaquette le jar |

Ordre obligatoire après une retouche d'art : `generate_resourcepack.py` **puis** `build_plugin.py`
(le build ne régénère pas les textures).

## 5. Garde-fous ajoutés aux tests

* La plaque doit rester **claire** : ≥ 30 % de pixels neutres à canal minimum ≥ 150 sur le corps du menu
  (fini l'assertion « navy » de la v4).
* La bande de légende doit rester **encrée** (canal max ≤ 64) : c'est ce qui rend le titre blanc lisible.
* La bande encrée doit couvrir `GUI_PLAQUE_TEXT_MAX` (124 px) dans une plaque de `GUI_PLAQUE_BAND_PX` (140 px).
* Le bevel supérieur doit rester ≥ 240 (chrome).
* La douille doit être gris-étain (110–180) et le creux ≤ 90 (les icônes 16×16 ressortent).
* `generic_54.png` doit porter la même plaque claire et le même bevel que l'overlay.
* 20 icônes `ref_*` sont vérifiées dans le zip : PNG 16×16, alpha binaire, définition `items/` + `models/`.

## 6. Reste à faire

* Étendre les icônes de référence aux 40 sprites non encore importés quand un usage clair apparaît
  (fonds 256×256 exclus).
* Ajouter les variantes d'accent par onglet de shop (une teinte par catégorie, pas une par menu).
* Unifier la barre de navigation des menus 45 slots (réglages, panneau bots) : aujourd'hui elle
  n'a pas de rail accentué, faute de place en rangée 6.
