# ORDRE DE MISSION — BosketBall · casser les bugs, pas le produit

Tu es l'ingénieur Java/Paper le plus impitoyable du dépôt. Tu as le shell, git, le clone. Pas de
serveur Minecraft, pas de client. **Tu ne poses aucune question, tu ne résumes pas le repo, tu ne
fais pas de plan : ton premier message contient un commit.**

Objectif, dans cet ordre : (1) tuer un maximum de bugs **réels**, (2) optimiser les hot paths
avec une mesure avant/après, (3) ne rien casser. Tu as **carte blanche** pour refactorer,
découper les classes-monstres, supprimer le code mort, changer les signatures privées, ajouter
des tests. Tu n'as pas le droit de t'arrêter tant que les 4 scripts de §5 ne sortent pas propres.

---

## 1. TERRAIN (vérifie, ne discute pas)

- Branche : **`LeVraiMain`** (commit `2f31cfa`). `main` ne contient **que des PNG** — piège, n'y touche pas.
- Paper/Purpur **26.2**, bytecode Java 21. Plugin unique tout-en-un, v `3.7.0`.
- **96 fichiers, 47 969 lignes**, 88 `@EventHandler`, 26 `MenuType`, 39 commandes, 9 nœuds de perm, 253 clés de config, **69 tests**, 2 tâches async.

```
4165 ui/GuiMenuManager        3591 game/MatchSession        2131 command/BosketCommand
2117 ui/LockerRoom3dMenuManager 1158 physics/BasketballEntity 1043 stats/StatsManager
1040 game/GameManager          986 server/ServerProtectionManager  1008 model/PlayerProfile
```

## 2. BUILD — la seule boucle qui compte

```bash
python3 tools/provision_build_env.py   # 1× par session (~2 min) : JRE+ECJ+paper-api depuis GitHub
python3 tools/build_plugin.py          # compile, teste, repack le pack, signe, package
```
Vert = `0 errors` · `OK (69 tests)` minimum · `git status` vide après **deux** builds consécutifs.
`/tmp/tools` absent ⇒ provisioning.

Analyse statique (ta meilleure source gratuite) :
```bash
CP=$(cat /tmp/tools/dep-classpath.txt); JAVA=$(python3 -c "from jdk4py import JAVA; print(JAVA)")
ECJ=$(ls /tmp/tools/jdtls/server/plugins/org.eclipse.jdt.core.compiler.batch_*.jar|tail -1)
find src/main/java -name '*.java' > /tmp/s.txt
$JAVA -jar $ECJ -21 -encoding UTF-8 -proc:none -cp "$CP" -d /tmp/o -warn:all @/tmp/s.txt > /tmp/ecj.log 2>&1
grep -B4 -E "^Null pointer access|^Potential null|^Resource leak|^The value of the local|^Dead code" /tmp/ecj.log|grep 'WARNING in'
```

## 3. LOI (viol = build rouge ou prod cassée)

1. **Zéro commentaire.** Tout a été strippé (≈3 880 lignes) ; des tests font `assertFalse(src.contains(...))`. N'en écris pas.
2. **Build reproductible.** Entrées ZIP via `add_file()` (timestamp figé). Jamais `zf.write()`.
3. `config.yml → external-sha1` == SHA du pack. Jamais de `.sha1` édité à la main.
4. `Locale.ROOT` sur tout `toLowerCase/format` de comparaison ou parsing.
5. `Msg.escapeMini()` sur **toute** chaîne joueur/staff avant MiniMessage (sinon `<click:run_command:…>` = RCE).
6. Écriture disque **uniquement** via `AtomicYamlWriter`.
7. Permission via `util/Perms`. Un `isOp()` nu sur une garde de capacité = bug (plugin.yml promet `bosketball.admin|staff|map|build`).
8. Lockdown : `NON_OP_ALLOWED_COMMANDS` doit couvrir toute commande joueur sans nœud de perm.
9. Aucune API Bukkit hors thread principal (2 async + 6 threads maison : ArenaIO, StatsIO, Storage, LeaderboardIndex, Discord, PackHost).
10. Interdit en 20 Hz : `.getConfig()`, I/O, `toLowerCase()` sans locale, allocation par tick.

## 4. DÉJÀ TUÉ — ne rejoue pas ce film

`docs/bug-audit-14.md` = 62 items. Audit #15 : lockdown (11 commandes joueurs bloquées), staff/build non-OP, `/mail` inexistante, `/giveelo`+`/addelo`+`/afk` non routées, 7 gardes admin `isOp()`→`Perms`, fuite zlib `CourtSchematic`, NPE `BotAvatar`, `general.language` mort, build reproductible, 3 tests anti-régression.
**Faux positifs ECJ, ignore-les :** `NametagManager:130,154`, `TabListManager:156`, `BotBrain:128`, `GameManager:304`, `new Inflater()` de `CourtSchematic`.
**Déjà optimisé, ne touche pas :** `NametagManager` / `TabListManager` diffent par signature ; `getSessionOf()` a un scan de secours volontaire ; `removePlayer` restaure l'inventaire au quit.

## 5. CIBLES, par rendement décroissant

**a) Commandes — 4 chemins (déclarée / exécuteur / routée / `case`).** Source n°1 des bugs trouvés. Doit sortir `PROBLEMS: none` :
```python
import re,pathlib
s=pathlib.Path('src/main/java/fr/bosketball/command/BosketCommand.java').read_text()
p=pathlib.Path('src/main/java/fr/bosketball/BosketBallPlugin.java').read_text()
r=re.findall(r'"([a-z0-9_-]+)"',re.search(r'for \(String shortcut : new String\[\]\{(.*?)\}\)',p,re.S).group(1))
n=s[s.index('normalizeShortcutArgs(Command command'):s.index('private final BosketBallPlugin plugin;')]
m=set(x for c in re.findall(r'case ([^\-]+) -> "',n) for x in re.findall(r'"([a-z0-9_-]+)"',c))
k={x for c in re.findall(r'case ((?:"[a-z0-9_-]+"(?:, )?)+) ->',s) for x in re.findall(r'"([a-z0-9_-]+)"',c)}
print([c for c in r if c!='bosketball' and not(c in m and c in k)] or "none")
```

**b) GUI — 26 menus, ~54 slots : peint mais non routé = bouton mort.** Pour chaque `openXxx()` → `inv.setItem(N,…)`, vérifier un `case N ->` (ou `if (slot == N)`) dans le bloc `case <MENU_TYPE> ->` de `onInventoryClick`. Vérifier aussi `setCancelled(true)` **avant** l'action et `getClickedInventory() != holder.getInventory()` (sinon vol d'items).

**c) Clés de config mortes** = options qui ne font rien. Doit sortir `[]` (faux positifs de lecture relative déjà filtrés) :
```python
import re,pathlib
L=pathlib.Path('src/main/resources/config.yml').read_text().split('\n'); st=[];lv=[]
for l in L:
    if not l.strip() or l.lstrip().startswith('#'):continue
    x=re.match(r'^(\s*)([A-Za-z0-9_.-]+):(\s*)(.*)$',l)
    if not x:continue
    i,k,rs=len(x.group(1)),x.group(2),x.group(4)
    while st and st[-1][0]>=i:st.pop()
    st.append((i,k))
    if rs.strip():lv.append('.'.join(y for _,y in st))
S=''.join(q.read_text() for q in pathlib.Path('src/main/java').rglob('*.java'))
R=set(re.findall(r'get(?:String|Boolean|Int|Double|Long|StringList|IntegerList|ConfigurationSection|List|MapList)\("([^"]+)"',S))
d=[k for k in lv if k not in R]
print([k for k in d if not k.startswith(('lobby.spawn','lobby.stations','progression.daily-login.streak-rewards'))])
```

**d) Threads / concurrence.** Traquer toute API Bukkit dans les 2 async + 6 threads ; itérations de `sessionsByArena` pendant mutation ; double flush.

**e) Mémoire.** `grep -rn "new ConcurrentHashMap\|new HashMap"` → chaque map indexée `UUID` doit avoir une purge (`onQuit`/`removePlayer`/`removeIf`). Pas de purge = fuite à 3 semaines d'uptime.

**f) Tick loop** (`BosketBallPlugin.onEnable`, 7 sous-systèmes à 20 Hz + travaux `%2/%10/%20/%30/%100`) : chaque appel doit avoir un `return` rapide quand personne n'est concerné. Compter les `teleport()`/packets/allocs par tick avant de toucher.

**g) Économie/jeu** (`MatchSession`) : double cérémonie MVP, `addCoins` vs `addEarnedCoins`, forfait qui course le buzzer, tie-break MVP, ELO zéro-somme, contest partagé. Toute modif ⇒ test.

**h) Resource pack** : port occupé, fallback `port+8`, mux Netty, `external-url`, `shutdown()` de l'executor.

**i) Persistance** : `markDirty()` **avant** `saveProfileAsync()` ; flush en `onDisable` ; fenêtre de crash.

## 6. RÈGLES DE FER

- **Un bug = une preuve = un test = un commit.** Pas de « il se peut que ». Si tu ne peux pas démontrer l'enchaînement exact (fichier/méthode/ligne/valeurs), tu ne codes pas.
- **Zéro bug inventé.** Un faux positif ajouté au rapport te coûte plus qu'un bug manqué.
- **Opti** : mesure avant, **un** changement, mesure après, les deux nombres dans le commit. Gain < 5 % et code plus complexe ⇒ `git revert`.
- **Diff minimal** : pas de reformatage, pas de renommage gratuit, pas de déplacement de code non lié. Tu peux découper `GuiMenuManager`/`MatchSession` **si et seulement si** les 69 tests restent verts sans être modifiés.
- **Interdits** : ajouter une dépendance ; Maven Central/Gradle/CI ; supprimer ou affaiblir un test ; changer `3.7.0` ; toucher `main` ou `releases/` à la main ; réécrire « pour la propreté ».
- Tu ne prétends **jamais** avoir vérifié ce qui exige un client Minecraft (rendu, packets, physique en monde, protections effectives). Tu dis « corrigé, à valider en jeu » + le pas-à-pas.

## 7. LIVRABLE FINAL

`docs/audit-16.md` : tableau `# | zone | symptôme joueur | cause exacte | correctif | test`, puis §« faux positifs écartés » et §« non vérifiable sans serveur ».
Commit type :
```
Corrige <symptôme vu par le joueur>

<enchaînement exact>
<pourquoi ce correctif>

Build: 0 errors, 69 tests OK, hash stable.
```

## 8. FIN DE MISSION

- [ ] `build_plugin.py` vert, tests ≥ 69, deux builds ⇒ `git status` vide.
- [ ] Les 4 scripts §5 sans nouveau problème ; aucun warning ECJ hors faux positifs §4.
- [ ] Chaque correctif testé ou justifié ; aucun test affaibli ; `audit-16.md` écrit.
- [ ] Checklist de recette livrée : joueur non-OP (`/quests /crate /guild /mail /afk` répondent, `/gamemode` refusé) · non-OP `bosketball.admin` (slot 44, `/setspawn`, `/perm`, `/giveelo`) · `bosketball.build` (`/build`, `//wand`) · match complet (score/ELO/coins/MVP une seule fois) · quit+relog en match (inventaire/gamemode/vitesse restaurés) · redémarrage en match (aucune perte de profil).

**Commence. Premier message = premier commit, pas un plan.**
