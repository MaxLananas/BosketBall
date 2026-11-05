# Menu « échap » (pause menu)

Le menu du lobby — celui qui remplace l'écran « Échap » sur le serveur — utilise désormais l'art du
kit `textures.zip` fourni par le propriétaire, **logo du serveur en haut**.

Aperçu : `docs/previews/pause_menu.png` (généré par `tools/preview_pause.py`).

## 1. Ce que contenait le zip

`textures.zip` → `textures/` : 17 PNG, aucune `.mcmeta`, aucun `pack.mcmeta`. C'est un **kit de
widgets d'écran pause** (pas un pack complet) :

| Fichier | Taille | Rôle |
|---|---|---|
| `back_button.png` / `_yellow` | 222×35 | « Back to Game », avec un personnage à droite |
| `quit_button.png` / `_red` | 232×36 | « Leave the server », mascotte à gauche |
| `options_button.png` / `_icon_button.png` | 100×22 | « Options… », version texte / version icône |
| `stats_button.png`, `feedback_button.png`, `rbugs_button.png`, `preport_button.png` (+ `_icon_`) | 100×46 / 100×22 | Statistiques, Feedbacks, Report bugs, Player Reporting |
| `bottom_news.png` / `_clear` | 235×78 | Carte « news » (Discord + mascottes) |
| `top_logo_1.png` + `top_logo_2.png` | 195×120 chacun | Logo **placeholder** coupé en deux tuiles : « SERVER » / « A LOGO » |

## 2. Le problème, et la solution retenue

Ces boutons sont dessinés pour un **mod client** : 222 à 232 px de large et **avec leur libellé
incrusté**. Un inventaire de serveur fait 176 px de large et ses boutons sont des items (c'est leur
nom qui sert de libellé, et l'item qui reçoit le clic). Poser ces PNG tels quels derrière une grille
de 54 slots donnerait des libellés recouverts par les items — donc illisible.

Deux livraisons, une seule source :

1. **Le menu serveur (lobby) reprend le *matériau* du kit**, mesuré au pixel sur
   `options_button.png` : contour noir 1 px, shade intérieur `#161322`, **un seul** biseau clair
   `#56506A` sur l'arête haute, corps violet plat `#262137`, marge transparente 1 px. Le panneau
   est reconstruit à la géométrie exacte du client (176 px, slots à `(8 + 18c, 25 + 18r)`,
   hauteur 137 pour 6 lignes) dans `tools/bosket_pause_kit.py`.
2. **L'écran Échap lui-même est re-skinné côté client** par le pack :
   * `assets/minecraft/textures/gui/menu_background.png` et `inworld_menu_background.png` (tuiles
     16×16 violettes, sans couture, floutées par le client) ;
   * `assets/minecraft/textures/gui/sprites/widget/button*.png` (normal / highlighted / disabled)
     aux couleurs du kit, **sans aucun texte incrusté** : c'est le client qui dessine les libellés
     vanilla par-dessus. Les `.mcmeta` nine-slice existants sont conservés.

## 3. Le logo, en haut

La bande de titre (plaque) porte maintenant le **logo du serveur** : `font/logo_header.png`
(156×22) collé à `(10, 2)`, à l'intérieur d'un cadre noir.

* Le logo est **aplati pixel à pixel** (son plateau est en alpha 232 ; le composite sur le violet
  l'aurait teinté) : la dorure garde exactement ses couleurs d'origine.
* Il est **tamponné en dernier** : rien ne peut le recouvrir.
* Conséquence assumée : la plaque n'a plus de légende runtime, donc l'appel devient
  `Glyphs.customGuiOverlayTitle(hubOverlay)` sans caption — *le logo est le titre*. C'est aussi la
  mise en page du kit d'origine (`top_logo_*` occupe la même bande).

## 4. Détails du panneau

* fond violet dégradé `#29204 8 → #17103 0` avec bandes de 8 px et dither 1 px (aucun antialiasing) ;
* carte intérieure en creux (hairline `#494560` + équerres d'angle) autour de la grille ;
* puits de slots : contour noir, creux `#100C1E`, ombre en haut, liseré `#3A3250` en bas ;
* rangée de navigation (46 / 49 / 52) : **pilules** bouton du kit au lieu des sockets orange ;
* accents violets sur les bords (or pour la variante staff `gui_overlay_hub_op`).

## 5. Régénérer

```bash
python3 tools/bosket_pause_kit.py     # écrit le panneau + le skin Échap côté client
python3 tools/preview_pause.py        # docs/previews/pause_menu.png
python3 tools/build_plugin.py         # pack rezippé + SHA-1 + jar
```

`tools/bosket_gui_kit.py::build_hub()` délègue au kit pause : une seule source de vérité pour le
panneau du lobby, les autres menus gardent leur matériau aluminium.

Tests : `pauseMenuWearsTheKitSkinAndTheServerLogo` vérifie la présence du skin côté client, la
géométrie 176×137, l'opacité totale, la **comparaison pixel à pixel avec `logo_header.png`**
(> 95 %), la position des puits et l'absence de caption sur ce menu.

## 6. Reste du kit, non utilisé pour l'instant

La carte `bottom_news` (235×78) et les mascottes des boutons ne tiennent pas dans un panneau
176 px sans écraser la grille : elles sont gardées pour un futur écran « Actualités / Discord »
(2 lignes, 235 px de large demanderaient un panneau dédié).
