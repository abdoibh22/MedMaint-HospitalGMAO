# MedMaint — GMAO hospitalier de maintenance biomédicale

**MedMaint** est une application de bureau **JavaFX** de gestion de maintenance des
équipements biomédicaux (GMAO/CMMS) destinée à un établissement de santé :
parc équipements, demandes d'intervention, ordres de travail, maintenance
préventive, pièces de rechange, fournisseurs & contrats, documents, rapports
CSV/PDF, notifications, audit et administration des comptes — le tout en **français**,
avec un contrôle d'accès par rôle et une base **H2 embarquée** (aucun serveur).

## Fonctionnalités

| Domaine | Contenu |
|---|---|
| Tableau de bord | KPI (parc, maintenance, coûts, stock, contrats), graphiques, activité récente, échéancier, demandes critiques |
| Équipements | Inventaire filtré/tournée/paginé, fiche complète (garantie, contrat, historique, pièces, documents), CRUD, plan de préventif rattaché |
| Demandes d'intervention | Saisie par le service clinique, tri/priorités, approbation, rejet motivé, assignation technicien, vue détail contextuelle |
| Ordres de travail | Cycle Demandée → Assignée → En cours → Résolue → Validée → Clôturée, diagnostic, coûts, pièces consommées, checklist |
| Maintenance préventive | Plans par fréquence, échéancier (retard / semaine / à venir), exécution avec checklist, historique, MTBF/MTTR/disponibilité |
| Pièces de rechange | Stock, seuils min/max, entrées/sorties/consommations, alertes de stock bas, fournisseurs |
| Fournisseurs & contrats | Contrats cadre, SLA, échéances (actif / expirant / expiré), rattachement d'équipements, péremptions garantie |
| Documents | Pièces jointes par entité (équipement, contrat…), stockage disque, upload contrôlé |
| Rapports | 11 rapports filtrables (inventaire, historique, coûts, disponibilité, techniciens, contrats…) avec export **CSV** et **PDF** |
| Administration | Comptes & rôles, services, catégories, checklists, paramètres système, journal d'audit, recherche globale |

**Rôles** : `ADMIN`, `MANAGER` (responsable maintenance), `ENGINEER` (ingénieur biomédical),
`TECHNICIAN`, `DEPT_MANAGER` (responsable de service), `REQUESTER` — chacun avec sa matrice
de permissions appliquée dans la couche service.

## Pile technique

- **Java 24** + **JavaFX 25** (composants de contrôle, CSS de thème maison)
- **H2 2.3** embarquée (fichier `./data/medmaint`), schéma appliqué idempotement au démarrage
- JDBC nu derrière des dépôts (SQL paramétré uniquement — pas d'injection SQL)
- **OpenPDF** (exports PDF), Logback/SLF4J, **JUnit 5** pour les tests
- Internationalisation FR par `ResourceBundle` (`src/main/resources/i18n/`)

## Démarrage

Prérequis : **JDK 24** (`JAVA_HOME` doit pointer dessus). Maven est fourni dans le dépôt
(`tools/apache-maven-3.9.9/`), les wrappers `mvnw` / `mvnw.cmd` fonctionnent aussi.

```bash
export JAVA_HOME="C:\Program Files\Java\jdk-24"   # Windows

# Compilation
./tools/apache-maven-3.9.9/bin/mvn compile        # ou : mvnw compile

# Tests (schéma, seed de démo, hachage, recherches de dépôts, écrans × rôles)
./tools/apache-maven-3.9.9/bin/mvn test           # ou : mvnw test

# Lancement de l'application
./tools/apache-maven-3.9.9/bin/mvn javafx:run     # ou : mvnw javafx:run
```

Au premier démarrage : la base est créée, le schéma appliqué, puis un **jeu de données
de démonstration** complet est inséré (12 services, 12 catégories, 40 équipements,
8 techniciens, 30 demandes, 41 ordres de travail, 28 plans, 35 pièces, 12 contrats,
12 mois d'historique, notifications, audit, documents PDF fictifs).
Les démarrages suivants détectent la base déjà peuplée et ne re-sèment rien.

## Comptes de démonstration

Mot de passe commun : **`demo2026`**

| Identifiant | E-mail | Rôle |
|---|---|---|
| `admin` | admin@medmaint.demo | Administrateur |
| `manager` | manager@medmaint.demo | Responsable maintenance |
| `engineer` | engineer@medmaint.demo | Ingénieur biomédical |
| `technician` | technician@medmaint.demo | Technicien |
| `service` | service@medmaint.demo | Responsable de service |

La connexion accepte l'identifiant **ou** l'e-mail. Données 100 % fictives,
aucune donnée patient.

## Configuration (variables d'environnement)

| Variable | Défaut | Rôle |
|---|---|---|
| `MEDMAINT_DB_URL` | `jdbc:h2:file:./data/medmaint;…` | URL JDBC H2 |
| `MEDMAINT_DB_USER` / `MEDMAINT_DB_PASSWORD` | `sa` / vide | Identifiants BDD |
| `MEDMAINT_UPLOAD_DIR` | `./uploads` | Dossier des documents joints |
| `MEDMAINT_EXPORT_DIR` | `./exports` | Dossier des exports CSV/PDF |
| `MEDMAINT_LOG_DIR` | `./logs` | Journaux |
| `MEDMAINT_MAX_UPLOAD` | `10485760` (10 Mo) | Taille max d'upload |

## Architecture

```
com.medmaint
├── MedMaintApp          # point d'entrée JavaFX (bootstrap, migration, seed)
├── config               # AppConfig (env vars)
├── domain
│   ├── enums            # statuts, priorités, fréquences…
│   └── model            # 20 entités (Equipment, WorkOrder, Request…)
├── persistence          # Db, Jdbc (SQL paramétré), SqlWhere, Page, SchemaMigrator
│   └── repo             # 16 dépôts : recherche filtrée/triée/paginée
├── security             # PasswordHasher (PBKDF2), Session, Role/Permission, validations
├── service              # Auth, Equipment, Request, WorkOrder, Preventive, Stock,
│                        # SupplierContract, Report (CSV/PDF), Analytics, Audit…
├── seed                 # DemoDataSeeder (transactionnel, idempotent)
└── ui
    ├── components       # DataTable, FormDialog, charts, toasts…
    ├── pages            # écrans par domaine
    ├── shell            # Sidebar, Topbar, MainShell
    └── i18n             # Messages (FR)
```

Flux : **ui → service (permissions + validation) → repository → H2**.
Les filtres de liste passent par `SqlWhere` : conditions littérales sans paramètre,
recherche textuelle multi-colonnes en OU, `LIKE` échappé, pagination `LIMIT/OFFSET`.

## Tests

`mvn test` exécute 38 tests :

- `PasswordHasherTest` — PBKDF2 : format, vérification, rejet d'entrées invalides, sel unique ;
- `SchemaMigratorTest` — migration idempotente + matrice rôles/permissions complète ;
- `DemoSeedTest` — volumes du jeu de démo, intégrité référentielle, scénarios métier,
  connexion des comptes de démo, documents PDF écrits sur disque ;
- `RepoSearchTest` — chaque recherche paginée de dépôt (toutes facettes littérales incluses)
  s'exécute sans erreur de binding SQL et renvoie les totaux attendus ;
- `PageConstructionTest` — chaque route du menu visible par un rôle se construit sans
  exception (6 rôles × routes = 70 pages), les KPI et la page tableau de bord
  chargent pour les 6 rôles (régression REPORT_VIEW/DASHBOARD_VIEW) ;
- `ShellLayoutTest` — fenêtre principale 1400×900 par défaut, redimensionnable avec
  bornes (min 1024×680, max écran), sidebar défilable à la molette, pop-up de
  formulaire 800×600 avec corps défilant, deep-links  « detail:<id> » (fiche
  équipement pour l'ingénieur, OT, demande) et vues détail/listes défilantes ;
- `UserFlowsTest` — parcours « nouveau client pressé » : messages de connexion en
  français, recherches hostiles (injection/`%`/emoji) sans casser les listes ni les
  données, soumission d'un formulaire vide sans effet de bord, double-clic
  approuver/démarrer idempotent, deep-links à ids corrompus, double-clic
  « Se connecter » sans listener fantôme.
