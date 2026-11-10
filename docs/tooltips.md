# Tooltips animés (style « Ultimate Tooltips »)

Le serveur n'utilise plus les tooltips vanilla : le pack embarque l'art « Ultimate Tooltips »
(cadres + fonds + rubans de rareté) et le plugin choisit le style de chaque item.

Aperçu : `docs/previews/tooltips.png` (généré par `tools/preview_tooltips.py`).

## 1. Comment ça marche (le mécanisme, pas la magie)

Depuis **Minecraft 1.21.2**, la tooltip n'est plus dessinée en dur : c'est un **GUI sprite**, et
chaque item peut choisir son style via le composant `minecraft:tooltip_style`.

Pour un style `<namespace>:<id>`, le client va chercher :

```
assets/<namespace>/textures/gui/sprites/tooltip/<id>_background.png
assets/<namespace>/textures/gui/sprites/tooltip/<id>_frame.png
```

Sans composant, il utilise les deux sprites par défaut : `tooltip/background.png` et
`tooltip/frame.png`. **C'est le levier le plus puissant** : en remplaçant ces deux fichiers, *tous*
les items (vanilla compris, y compris ceux d'autres plugins) gagnent le nouveau look. C'est ce que
fait le pack depuis cette version : `background.png` et `frame.png` sont désormais l'art « common ».

### Les `.mcmeta` : ce qui rend le tout propre

Chaque PNG est accompagné d'un `.mcmeta` qui décrit **comment** l'étirer :

```json
{
  "gui": { "scaling": { "type": "nine_slice", "width": 100, "height": 100, "border": 30, "stretch_inner": true } },
  "animation": { "frametime": 3, "interpolate": true }
}
```

* `nine_slice` + `border: 30` : les coins gardent leur taille, les bords et le centre s'étirent —
  la tooltip peut faire 60 px ou 400 px de large sans déformer les ornements.
* `stretch_inner: true` : l'intérieur est **étiré** (et pas carrelé), sinon les motifs se répètent.
* `animation` : le PNG est une **bande verticale de frames** empilées (100×1500 = 15 frames pour
  `legendary_frame`, 100×800 = 8 frames pour `artifact_frame`). `frametime` = nombre de ticks par
  frame (3 pour legendary → ça scintille vite ; 12 pour artifact → plus lent), `interpolate` fond
  les frames entre elles. **C'est exactement ça, la tooltip animée.**

## 2. Les styles livrés

| Style | Fond / cadre | Animé | Utilisé pour |
|---|---|---|---|
| *(défaut)* `tooltip/background` + `frame` | art « common » | non | tous les items sans style |
| `minecraft:uncommon` | vert | non | items à 500+ coins, rang HOOPER |
| `minecraft:rare` | bleu | non | items à 1000+ coins, PRO/ALL-STAR, portes de la hotbar |
| `minecraft:epic` | violet | non | items à 2000+ coins, SUPERSTAR |
| `minecraft:legendary` | or | **oui** (frametime 3) | items à 3500+ coins, LEGEND |
| `minecraft:artifact` | rouge | **oui** (frametime 12) | MVP, exclusivités |

Barème centralisé dans `fr.bosketball.util.TooltipStyles` :

```java
TooltipStyles.Rarity rarity = TooltipStyles.forCoins(entry.priceCoins());   // boutique
TooltipStyles.decorate(card, rarity);                                       // style + ruban
TooltipStyles.forRank(rank);                                                // guide des rangs
```

Où c'est appliqué aujourd'hui :

* **boutique** (`buildShopEntryCard`) : ballons, dunks et tags, d'après leur prix ;
* **guide des rangs** : chaque palier prend le style de son tier (MVP en artifact, LEGEND en gold) ;
* **hotbar du lobby** : les sept portes en `rare` (bleu).

## 3. Les rubans de rareté (les « nouveaux tags »)

Le pack fournit aussi des **libellés dessinés** (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY,
ARTIFACT, EXCLUSIVE, plus les catégories WEAPON / HAT / DECORATION… et des badges chiffrés 1–10).
Ce ne sont pas des textures d'items : ce sont des **glyphes de police**, hauts de 9 px.

Ils sont donc branchés dans `assets/minecraft/font/default.json` comme providers `bitmap` :

```json
{
  "type": "bitmap",
  "file": "bosketball:font/tooltips/legendary.png",
  "height": 9,
  "ascent": 8,
  "chars": ["\ue364"]
}
```

* `height: 9` = hauteur de rendu (le sprite fait 9 px de haut) ;
* `ascent: 8` = posé sur la ligne de base du texte ;
* la largeur d'avance est calculée automatiquement (57 px pour LEGENDARY).

Plage réservée : **U+E360 → U+E366** (vérifié libre : le pack utilise E100…E35D pour les overlays
GUI). Côté Java, `TooltipStyles.Rarity.ribbon()` rend le caractère, et `decorate()` l'insère en
première ligne de la lore.

> ⚠️ `font/default.json` est édité **à la main**, jamais via `json.dumps` : il contient 218
> providers et un round-trip JSON reformate tout le fichier. Le test
> `tooltipStylesAndRarityRibbonsResolveInThePack` vérifie que chaque ruban a bien son provider et
> que tous les glyphes sont distincts.

## 4. Ajouter un style

1. Créer `tooltip/<monstyle>_background.png` et `_frame.png` (100×100, ou bande verticale si animé)
   + leurs `.mcmeta` avec `nine_slice`, `border: 30`, `stretch_inner: true`.
2. Si le style est animé, ajouter `"animation": {"frametime": N, "interpolate": true}` dans le
   `.mcmeta` du cadre.
3. Ajouter la constante dans `TooltipStyles.Rarity` (id + ruban + couleur), ou réutiliser un style
   existant.
4. `python3 tools/build_plugin.py` → pack rezippé, SHA-1 changé → nouveau tag
   (voir [`resource-pack-mirrors.md`](resource-pack-mirrors.md)).

## 5. Régénérer les aperçus

```bash
python3 tools/preview_tooltips.py     # docs/previews/tooltips.png
python3 tools/preview_hotbar.py       # docs/previews/hotbar-row.png
python3 tools/preview_gui_light.py    # docs/previews/gui_v5_light_menus.png
```
