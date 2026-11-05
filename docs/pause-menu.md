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

## 2. L'écran Échap, fait avec tes PNG

Tes fichiers sont maintenant la **source** du pack : ils sont versionnés dans
`tools/pause_kit_source/` et les textures livrées en sont dérivées par
`tools/bosket_pause_kit.py`.

| Texture du pack | Dérivée de | Ce qui est fait |
|---|---|---|
| `gui/sprites/widget/button.png` | `options_button.png` (100×22) | libellé « Options… » retiré pixel par pixel (repaint au violet du corps), cadre / contour noir / biseau intacts |
| `gui/sprites/widget/button_highlighted.png` | idem | biseau supérieur repeint avec **ton** jaune `#FFBC00` échantillonné dans `back_button_yellow.png` |
| `gui/sprites/widget/button_disabled.png` | idem | corps et biseau assombris |
| `gui/menu_background.png`, `inworld_menu_background.png` | `bottom_news.png` | tuiles 16×16 sans couture, dans les deux violets exacts de ta carte news |

Les trois boutons gardent un `.mcmeta` `nine_slice` (`width/height` = 100×22, bord 3,
`stretch_inner`) : le client les étire à 200×20 sans déformer le cadre.

### Pourquoi le libellé doit être retiré

L'écran Échap vanilla n'a **qu'un seul sprite de bouton** pour ses six boutons
(`widget/button`), et il écrit lui-même « Back to Game », « Advancements », « Statistics »,
« Options… », « Report Bugs », « Leave Server » par-dessus. Six textures différentes ne peuvent
donc pas arriver par ce canal : si on laisse le texte incrusté, « Options… » s'imprime sous
« Back to Game ». On garde donc le **cadre** (le tien) et le client écrit le libellé.

## 3. Le logo du serveur

Le logo est en haut du **panneau du menu** (la plaque), à l'emplacement exact prévu par le kit
(`top_logo_*`, même bande, même hauteur) : `font/logo_header.png`, 156×22, collé à `(10, 2)`,
aplati pixel à pixel (son plateau est en alpha 232, un simple composite aurait teinté la dorure) et
tamponné en dernier pour que rien ne le recouvre.

> **Pourquoi pas sur l'écran Échap vanilla ?** Cet écran n'a aucun emplacement d'image : son fond est
> une texture 16×16 **répétée** sur tout l'écran (un logo y apparaîtrait des dizaines de fois), et
> les seuls autres éléments sont les boutons et leur texte. Dessiner une image en haut de cet écran
> demande un **mod client** ; un resource pack ne peut pas le faire. Le seul écran où une image peut
> être dessinée, c'est un menu rendu par le serveur — d'où le panneau.

## 4. Détails du panneau

* fond violet dégradé `#29204 8 → #17103 0` avec bandes de 8 px et dither 1 px (aucun antialiasing) ;
* carte intérieure en creux (hairline `#494560` + équerres d'angle) autour de la grille ;
* puits de slots : contour noir, creux `#100C1E`, ombre en haut, liseré `#3A3250` en bas ;
* rangée de navigation (46 / 49 / 52) : **pilules** bouton du kit au lieu des sockets orange ;
* accents violets sur les bords (or pour la variante staff `gui_overlay_hub_op`).

## 5. Régénérer

```bash
python3 tools/bosket_pause_kit.py     # dérive le skin Échap des PNG de tools/pause_kit_source/
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
