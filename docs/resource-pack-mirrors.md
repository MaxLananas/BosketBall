# Pack de ressources servi par jsDelivr (gratuit, sans limite de trafic)

**État actuel : c'est en place.** Le dépôt `MaxLananas/BosketBall` est public, le tag
`pack-476ecec8` est poussé, et jsDelivr sert le pack :

```text
https://cdn.jsdelivr.net/gh/MaxLananas/BosketBall@pack-476ecec8/releases/BosketBall-ResourcePack.zip
```

Vérifié : l'index jsDelivr du tag liste `BosketBall-ResourcePack.zip` (3,22 Mio = 3 376 838 octets),
et `…/releases/BosketBall-ResourcePack.zip.sha1` renvoie bien
`476ecec839d69d2c6ae8a505d97aa3161583642e`.

Le pack n'a qu'un seul contenu, partout : même ZIP, même SHA-1. jsDelivr et le second chemin
servent **le même fichier**, donc les joueurs ne retéléchargent jamais un second pack.

---

## 1. Les deux sources configurées

| # | Source | Ce que c'est | Trafic |
|---|---|---|---|
| 1 | `cdn.jsdelivr.net/gh/MaxLananas/BosketBall@pack-476ecec8/…` | CDN mondial, POP à Paris / Francfort / Londres / New York | **illimité**, gratuit |
| 2 | `raw.githubusercontent.com/MaxLananas/BosketBall/pack-476ecec8/…` | réseau Fastly de GitHub, chemin indépendant | gratuit |
| 3 | serveur intégré du plugin (port de jeu + 8765) | dernier recours automatique, serveur éteint ⇒ ce filet disparaît aussi | dépend de l'hôte |

Dans `config.yml` :

```yaml
resource-pack:
  mirrors:
    - "https://cdn.jsdelivr.net/gh/MaxLananas/BosketBall@pack-476ecec8/releases/BosketBall-ResourcePack.zip"
    - "https://raw.githubusercontent.com/MaxLananas/BosketBall/pack-476ecec8/releases/BosketBall-ResourcePack.zip"
  external-sha1: "476ecec839d69d2c6ae8a505d97aa3161583642e"
```

Ordre = priorité. Les miroirs sont essayés avant le serveur intégré ; une entrée vide, non
`http(s)` ou en double est ignorée et jamais envoyée au client (test
`resourcePackMirrorsKeepOrderAndStayOnTheSameZip`). `{sha1}` dans une URL est remplacé par le
SHA-1 du pack, si vous voulez un cache-busting.

Contrôle en jeu : `/bb pack test` (une ligne par route, taille + débit mesurés côté serveur),
`/bb pack push` (renvoie le pack à tous les joueurs connectés).

---

## 2. Pourquoi un tag et pas `@main`

* Une URL sur une **branche** est mise en cache environ 12 h : vous pouvez servir un ancien pack
  une demi-journée, et les clients le refusent (hash mismatch) avant de retomber sur la route
  suivante.
* Un **tag** (`pack-476ecec8`) est immuable : l'URL ne changera jamais de contenu, donc le cache
  est toujours juste, et la première requête reste rapide pour tout le monde.

Rappels jsDelivr : le dépôt doit être **public**, et un fichier ne doit pas dépasser **20 Mo**
(le pack fait 3,38 Mo, marge très large).

---

## 3. Nouveau pack ⇒ nouveau tag

À chaque `python3 tools/build_plugin.py`, le build affiche le nouveau SHA-1 et le recopie dans
`config.yml`. Procédure :

```bash
SHA=$(python3 -c "import hashlib;print(hashlib.sha1(open('releases/BosketBall-ResourcePack.zip','rb').read()).hexdigest())")
git add -A && git commit -m "Resource pack ${SHA:0:8}"
git tag "pack-${SHA:0:8}"
git push origin "pack-${SHA:0:8}"        # pousse le tag SEUL (pas besoin de pousser la branche)
```

Puis remplacer les deux occurrences du tag dans `config.yml`, redémarrer le serveur, et lancer
`/bb pack test`. Le SHA-1 de `external-sha1` est déjà synchronisé par le build ; seuls les tags
des URL changent.

Script court pour la mise à jour des deux lignes :

```bash
OLD=pack-476ecec8 NEW=pack-<nouveau>
sed -i "s/$OLD/$NEW/g" src/main/resources/config.yml
```

> Astuce : à chaque nouveau tag, ouvrez `https://cdn.jsdelivr.net/gh/MaxLananas/BosketBall@<tag>/releases/`
> pour confirmer que jsDelivr voit bien le ZIP et sa taille avant d'annoncer l'URL aux joueurs.

---

## 4. Pièges à éviter

* **Dépôt privé** → jsDelivr répond 404/403. Ici il est public, c'est réglé.
* **URL sur une branche** → cache ~12 h, pack périmé servi. Épingler un tag.
* **Fichier > 20 Mo** → jsDelivr refuse (le pack en est très loin).
* **ZIP différent d'une source à l'autre** → le client refuse le pack. Toujours pousser le ZIP
  sorti du build, jamais un ancien.
* **Ne jamais pointer un miroir vers le mux du port de jeu** : ce chemin dépend du SNI et du port
  réel du serveur, il est déjà ajouté automatiquement en dernier.
