# Servir le pack de ressources depuis jsDelivr (gratuit, sans limite de trafic)

Objectif : le pack `BosketBall-ResourcePack.zip` (3 376 838 octets, SHA-1
`476ecec839d69d2c6ae8a505d97aa3161583642e`) doit pouvoir être téléchargé depuis **plusieurs
endroits dans le monde**, rapidement, pour que les joueurs ne téléchargent plus à 12–14 ko/s
depuis un hôte de Los Angeles (voir [`network-diagnostics.md`](network-diagnostics.md), §5).

jsDelivr est un CDN gratuit, sans inscription et **sans limite de bande passante**, avec un POP à
Paris, Francfort, Londres, New York… Il ne sert que des fichiers de **dépôts GitHub publics** ou de
paquets npm.

---

## 1. Le point bloquant : jsDelivr ne lit pas un dépôt privé

`MaxLananas/BosketBall` est **privé**. jsDelivr répondra donc `403`/`404` dessus. Deux solutions :

| Solution | Effet | Recommandation |
|---|---|---|
| Rendre `BosketBall` public | Le code source devient public | seulement si vous l'acceptez |
| **Dépôt public dédié au pack** (`BosketBall-Pack`) | Seul le ZIP est public, le code reste privé | ✅ recommandé |

Un pack de ressources n'a rien de secret : chaque joueur le télécharge déjà. Un dépôt qui ne
contient **que le zip** est donc la meilleure option.

---

## 2. Créer le dépôt public du pack (une seule fois)

Le ZIP est déjà versionné ici dans `releases/`. On le recopie dans un petit dépôt dédié :

```bash
mkdir -p ~/BosketBall-Pack && cd ~/BosketBall-Pack
cp ~/BosketBall/releases/BosketBall-ResourcePack.zip .
cp ~/BosketBall/releases/BosketBall-ResourcePack.zip.sha1 .
git init -b main
git add .
git commit -m "Resource pack 476ecec8 (3.38 MB)"
# crée le dépôt public ET pousse le commit en une commande
gh repo create MaxLananas/BosketBall-Pack --public --source=. --push \
  --description "BosketBall resource pack (served via jsDelivr)"
```

Puis on pose un **tag** : c'est lui qui rend l'URL immuable (et donc jamais périmée en cache) :

```bash
git tag pack-476ecec8
git push origin pack-476ecec8
```

L'URL jsDelivr est alors :

```text
https://cdn.jsdelivr.net/gh/MaxLananas/BosketBall-Pack@pack-476ecec8/BosketBall-ResourcePack.zip
```

Contrôle rapide (le contenu doit faire exactement 3 376 838 octets) :

```bash
curl -sI https://cdn.jsdelivr.net/gh/MaxLananas/BosketBall-Pack@pack-476ecec8/BosketBall-ResourcePack.zip \
  | grep -i -E "HTTP/|content-length|content-type"
```

> La toute première requête sur un tag inédit peut prendre quelques secondes (jsDelivr va chercher
> le fichier chez GitHub, puis le garde en cache permanent). Les suivantes partent du POP le plus
> proche.

---

## 3. Deuxième source (facultatif mais conseillé)

Le même dépôt public donne aussi une URL **GitHub Releases / Fastly**, indépendante de jsDelivr :

```text
https://github.com/MaxLananas/BosketBall-Pack/releases/download/pack-476ecec8/BosketBall-ResourcePack.zip
```

Pour la créer : `gh release create pack-476ecec8 BosketBall-ResourcePack.zip`. Le réseau Fastly de
GitHub a des POP européens et américains : c'est un vrai second chemin, pas un doublon.

Le serveur intégré du plugin (port de jeu + port 8765) reste le **dernier recours automatique**.

---

## 4. Configurer le plugin

```yaml
resource-pack:
  # 1) jsDelivr : CDN mondial (POP Paris), gratuit, trafic illimité
  # 2) GitHub Releases : second chemin (Fastly)
  # 3) serveur intégré du plugin = filet de sécurité automatique
  mirrors:
    - "https://cdn.jsdelivr.net/gh/MaxLananas/BosketBall-Pack@pack-476ecec8/BosketBall-ResourcePack.zip"
    - "https://github.com/MaxLananas/BosketBall-Pack/releases/download/pack-476ecec8/BosketBall-ResourcePack.zip"
  external-sha1: "476ecec839d69d2c6ae8a505d97aa3161583642e"
```

Comportement :

* les miroirs sont essayés **dans l'ordre**, avant le serveur intégré ;
* `{sha1}` dans une URL est remplacé par le SHA-1 du pack (utile pour un cache-busting) ;
* une entrée vide, non `http(s)` ou en double est **ignorée** et jamais envoyée au client ;
* `external-url` n'est utilisé que si `mirrors` est vide (compatibilité ascendante).

Vérification en jeu :

```text
/bb pack test     # une ligne par route : taille, débit mesuré depuis le serveur
/bb pack push     # renvoie le pack à tous les joueurs connectés
```

Sur un serveur bien connecté, jsDelivr doit sortir **plusieurs Mo/s** là où l'ancien chemin
faisait 12–14 ko/s.

---

## 5. À chaque nouveau pack : nouveau tag

Le client vérifie le SHA-1 : un miroir qui sert un autre ZIP est **refusé**, et le plugin retombe
sur la route suivante. Donc après chaque `python3 tools/build_plugin.py` :

```bash
# le build affiche le nouveau SHA-1, par exemple a1b2c3d4…
cp ~/BosketBall/releases/BosketBall-ResourcePack.zip ~/BosketBall-Pack/
cp ~/BosketBall/releases/BosketBall-ResourcePack.zip.sha1 ~/BosketBall-Pack/
cd ~/BosketBall-Pack
git add -A && git commit -m "Resource pack <sha1 court>"
git tag pack-<sha1 court> && git push origin main --tags
gh release create pack-<sha1 court> BosketBall-ResourcePack.zip
```

puis mettre à jour les deux URL (tag) dans `config.yml` et redémarrer. Le SHA-1 de `external-sha1`
est déjà synchronisé par le build.

---

## 6. Pièges à éviter

* **Dépôt privé** → jsDelivr refuse. C'est le piège n°1, et c'est le cas du dépôt de jeu.
* **URL sur une branche** (`@main`) → cache de ~12 h : vous pouvez servir un ancien pack pendant
  une demi-journée. Toujours épingler un **tag** (ou un SHA de commit).
* **Fichier > 20 Mo** → jsDelivr refuse. Le pack fait 3,38 Mo, marge très large.
* **ZIP différent sur un miroir** → hash mismatch côté client, pack refusé.
* **Ne jamais pointer un miroir vers le mux du port de jeu** : ce chemin dépend du SNI et du port
  réel du serveur, il est déjà ajouté automatiquement en dernier.
