# Icône du serveur — ballon de basket 16×16

> Générée par `tools/generate_server_icon.py` (art **natif 16×16**, alpha dur, agrandi ×4 en
> 64×64 au plus proche voisin — Minecraft exige 64×64 pour `server-icon.png`).

| Fichier | Contenu |
|---|---|
| `src/main/resources/server-icon.png` | l'icône livrée (64×64, style `classic`) |
| `releases/server-icon.png` | copie livrée à côté du jar |
| `docs/server-icon-16x16-*.png` | l'art natif des 3 styles |
| `docs/server-icon-64x64-*.png` | aperçu 64×64 des 3 styles |

## Changer de style

```bash
python3 tools/generate_server_icon.py --style classic   # (défaut) contours sombres
python3 tools/generate_server_icon.py --style modern    # plus lumineux, moins contrasté
python3 tools/generate_server_icon.py --style retro     # plat, aplats
```

Le plugin réinstalle `server-icon.png` à la racine du serveur au démarrage
(`motd.auto-install-server-icon: true`), sinon copie le fichier à la main puis redémarre.

## Dessin

* sphère centrée, 4 tons de dégradé (lumière en haut à gauche) ;
* contour 1 px pour rester lisible sur n'importe quel fond ;
* coutures : équateur, méridien et deux arcs qui se rejoignent aux pôles.
