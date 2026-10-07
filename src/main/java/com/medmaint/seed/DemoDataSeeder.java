package com.medmaint.seed;

import com.medmaint.config.AppConfig;
import com.medmaint.persistence.Db;
import com.medmaint.persistence.DbException;
import com.medmaint.persistence.Jdbc;
import com.medmaint.security.PasswordHasher;
import org.openpdf.text.Document;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Jeu de données de démonstration (entièrement fictif, aucune donnée patient).
 * Idempotent : n'exécute rien si des utilisateurs existent déjà.
 * Toute l'insertion est transactionnelle — un échec annule tout.
 *
 * <p>Comptes de démonstration (mot de passe : {@code demo2026}) :
 * admin, manager, engineer, technician, service.</p>
 */
public final class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_PASSWORD = "demo2026";
    private static final Random RND = new Random(20261006L);

    private DemoDataSeeder() {
    }

    /** @return true si les données ont été insérées, false si la base était déjà peuplée. */
    public static boolean runIfNeeded() {
        if (Jdbc.count("SELECT COUNT(*) FROM users") > 0) {
            return false;
        }
        DemoDataSeeder seeder = new DemoDataSeeder();
        Connection conn = Db.get();
        try {
            conn.setAutoCommit(false);
            seeder.seedAll();
            conn.commit();
            log.info("Données de démonstration insérées (12 services, 40 équipements, "
                    + "30 demandes, 41 ordres de travail, 28 plans, 35 pièces…).");
            return true;
        } catch (SQLException e) {
            try {
                conn.rollback();
            } catch (SQLException rb) {
                log.error("Rollback impossible.", rb);
            }
            throw new DbException("Échec du seed des données de démonstration.", e);
        } catch (RuntimeException e) {
            try {
                conn.rollback();
                log.error("Seed annulé (rollback effectué).", e);
            } catch (SQLException rb) {
                log.error("Rollback impossible.", rb);
            }
            throw e;
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // la connexion reste utilisable pour l'application
            }
        }
    }

    // ------------------------------------------------------------------
    // État partagé pendant le seed
    // ------------------------------------------------------------------

    private long adminId;
    private long managerId;
    private long engineerId;
    private long techUserId;
    private long serviceId;

    private final List<Long> deptIds = new ArrayList<>();
    private final List<Long> catIds = new ArrayList<>();
    private final List<Long> supplierIds = new ArrayList<>();
    private final List<Long> contractIds = new ArrayList<>();
    private final List<Long> techIds = new ArrayList<>();
    private final List<Long> equipIds = new ArrayList<>();
    private final List<Long> equipDeptIds = new ArrayList<>();
    private final Map<Long, Long> equipContract = new HashMap<>();
    private final Map<Long, Long> equipCat = new HashMap<>();
    private final Map<Long, Long> catChecklist = new HashMap<>();
    private final Map<Long, List<long[]>> checklistItems = new HashMap<>();
    private final List<Long> planIds = new ArrayList<>();
    private final List<Long> partIds = new ArrayList<>();
    private final List<Long> requestIds = new ArrayList<>();
    private final List<WoInfo> workOrders = new ArrayList<>();
    private final List<Long> expiringContractIds = new ArrayList<>();

    private record WoInfo(long id, long equipmentId, long openedDayOffset, String status,
                          LocalDateTime completedAt) {
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int ri(int min, int max) {
        return RND.nextInt(max - min + 1) + min;
    }

    private static <T> T pick(T... options) {
        return options[ri(0, options.length - 1)];
    }

    private static boolean chance(double p) {
        return RND.nextDouble() < p;
    }

    private static LocalDate day(int offsetDays) {
        return LocalDate.now().minusDays(offsetDays);
    }

    private static LocalDateTime dateTime(int offsetDays, int hour) {
        return LocalDateTime.of(day(offsetDays), LocalTime.of(hour, ri(0, 59)));
    }

    private static BigDecimal money(long amount) {
        return BigDecimal.valueOf(amount);
    }

    // ------------------------------------------------------------------
    // Orchestration
    // ------------------------------------------------------------------

    private void seedAll() {
        seedDepartments();
        seedCategories();
        seedManufacturers();
        seedSuppliers();
        seedUsers();
        seedTechnicians();
        seedContracts();
        seedEquipment();
        seedChecklists();
        seedPlans();
        seedSpareParts();
        seedRequests();
        seedWorkOrders();
        seedMaintenanceRecords();
        seedStockTransactions();
        seedNotifications();
        seedAuditLogs();
        seedDocuments();
    }

    // ------------------------------------------------------------------
    // Référentiels
    // ------------------------------------------------------------------

    private void seedDepartments() {
        String[][] data = {
                {"REA", "Réanimation polyvalente", "Dr. Claire Fontaine", "Bâtiment A — niveau 2", "01 44 55 61 01", "rea@chu-exemple.fr"},
                {"CARD", "Cardiologie", "Dr. Marc Delaunay", "Bâtiment A — niveau 3", "01 44 55 61 02", "cardiologie@chu-exemple.fr"},
                {"URG", "Urgences", "Dr. Sophie Vidal", "Bâtiment B — RDC", "01 44 55 61 03", "urgences@chu-exemple.fr"},
                {"IMAG", "Imagerie médicale", "Dr. Antoine Reyes", "Bâtiment C — niveau 1", "01 44 55 61 04", "imagerie@chu-exemple.fr"},
                {"BLOC", "Bloc opératoire", "Dr. Nadia Chevalier", "Bâtiment B — niveau 1", "01 44 55 61 05", "bloc@chu-exemple.fr"},
                {"PED", "Pédiatrie", "Dr. Isabelle Marchand", "Bâtiment A — niveau 4", "01 44 55 61 06", "pediatrie@chu-exemple.fr"},
                {"MAT", "Maternité", "Dr. Émilie Bonnet", "Bâtiment D — niveau 2", "01 44 55 61 07", "maternite@chu-exemple.fr"},
                {"LABO", "Laboratoire d'analyses", "Pr. Julien Faure", "Bâtiment C — niveau 2", "01 44 55 61 08", "laboratoire@chu-exemple.fr"},
                {"PHAR", "Pharmacie", "Dr. Karim Haddad", "Bâtiment C — RDC", "01 44 55 61 09", "pharmacie@chu-exemple.fr"},
                {"DIAL", "Unité de dialyse", "Dr. Laurent Pires", "Bâtiment A — niveau 1", "01 44 55 61 10", "dialyse@chu-exemple.fr"},
                {"PNEU", "Pneumologie", "Dr. Amélie Roux", "Bâtiment A — niveau 5", "01 44 55 61 11", "pneumologie@chu-exemple.fr"},
                {"STER", "Stérilisation centrale", "Mme Frédérique Guyot", "Bâtiment B — niveau 0", "01 44 55 61 12", "sterilisation@chu-exemple.fr"}
        };
        String[] floors = {"RDC", "Niveau 1", "Niveau 2", "Niveau 3", "Niveau 4", "Niveau 5"};
        for (String[] d : data) {
            long id = Jdbc.insert("INSERT INTO departments(code, name, head_name, location, phone, email) VALUES (?,?,?,?,?,?)",
                    d[0], d[1], d[2], d[3], d[4], d[5]);
            deptIds.add(id);
            Jdbc.insert("INSERT INTO locations(department_id, code, name, floor) VALUES (?,?,?,?)",
                    id, d[0] + "-01", "Salle principale", floors[RND.nextInt(floors.length)]);
            Jdbc.insert("INSERT INTO locations(department_id, code, name, floor) VALUES (?,?,?,?)",
                    id, d[0] + "-02", pick("Box technique", "Salle de soins 2", "Réserve équipements", "Salle de surveillance"), floors[RND.nextInt(floors.length)]);
        }
    }

    private void seedCategories() {
        String[][] data = {
                {"RESP", "Respirateur", "Ventilateurs et respirateurs d'anesthésie", "MENSUELLE"},
                {"MONI", "Moniteur patient", "Monitrages multiparamétriques", "TRIMESTRIELLE"},
                {"ECG", "Électrocardiographe", "ECG 12 dérivations et holters", "SEMESTRIELLE"},
                {"ECHG", "Échographe", "Échographes et appareils duplex", "ANNUELLE"},
                {"RADI", "Radiologie", "Radiologues mobiles et fixes", "ANNUELLE"},
                {"DEFIB", "Défibrillateur", "Défibrillateurs et monitorers", "MENSUELLE"},
                {"POMP", "Pompe à perfusion", "Pompes à seringue et perfusion", "TRIMESTRIELLE"},
                {"AUTO", "Autoclave", "Stérilisateurs à vapeur d'eau", "HEBDOMADAIRE"},
                {"INCU", "Couveuse", "Cuvettes et couveuses néonatales", "MENSUELLE"},
                {"DIAL", "Dialyse", "Moniteurs d'hémodialyse", "TRIMESTRIELLE"},
                {"ANAL", "Analyseur", "Automates d'analyses médicales", "MENSUELLE"},
                {"ANES", "Anesthésieur", "Appareils d'anesthésie", "TRIMESTRIELLE"}
        };
        for (String[] c : data) {
            catIds.add(Jdbc.insert(
                    "INSERT INTO equipment_categories(code, name, description, default_frequency) VALUES (?,?,?,?)",
                    c[0], c[1], c[2], c[3]));
        }
    }

    private void seedManufacturers() {
        String[][] data = {
                {"MediVent", "France", "www.medivent-exemple.fr", "support@medivent-exemple.fr"},
                {"CardioTech", "Allemagne", "www.cardiatech-exemple.de", "support@cardiatech-exemple.de"},
                {"PulmoSys", "Pays-Bas", "www.pulmosys-exemple.nl", "support@pulmosys-exemple.nl"},
                {"ImageX", "États-Unis", "www.imagex-exemple.com", "support@imagex-exemple.com"},
                {"SteriLab", "Italie", "www.sterilab-exemple.it", "support@sterilab-exemple.it"},
                {"DialyNova", "Suisse", "www.dialynova-exemple.ch", "support@dialynova-exemple.ch"},
                {"LaboFlux", "France", "www.laboflux-exemple.fr", "support@laboflux-exemple.fr"},
                {"Oxymed", "Belgique", "www.oxymed-exemple.be", "support@oxymed-exemple.be"}
        };
        for (String[] m : data) {
            Jdbc.insert("INSERT INTO manufacturers(name, country, website, support_email) VALUES (?,?,?,?)",
                    m[0], m[1], m[2], m[3]);
        }
    }

    private void seedSuppliers() {
        String[][] data = {
                {"MedTech Distribution SAS", "Julien Barbier", "12 rue des Entrepôts, 69007 Lyon", "04 72 10 20 30", "contact@medtech-exemple.fr", "www.medtech-exemple.fr", "Équipements", "Respirateurs, moniteurs, défibrillateurs"},
                {"Biomed Services Lyon", "Fatima Cherif", "45 av. Jean Jaurès, 69007 Lyon", "04 78 40 50 60", "contact@biomedservices-exemple.fr", "www.biomedservices-exemple.fr", "Maintenance", "Maintenance préventive et curative"},
                {"Hospitech Distribution", "Pierre Lemoine", "8 zone industrielle, 69800 Saint-Priest", "04 72 90 11 12", "contact@hospitech-exemple.fr", "www.hospitech-exemple.fr", "Équipements", "Imagerie, échographes"},
                {"Santéq Maintenance", "Inès Morel", "3 bd de la Libération, 69100 Villeurbanne", "04 76 33 44 55", "contact@saniteq-exemple.fr", "www.santeq-exemple.fr", "Maintenance", "Contrats cadre multi-services"},
                {"CardioPlus Distribution", "Marc Treville", "21 rue de la République, 69002 Lyon", "04 78 66 77 88", "contact@cardioplus-exemple.fr", "www.cardioplus-exemple.fr", "Équipements", "Cardiologie, ECG, holters"},
                {"LaboSolutions France", "Sarah Benkirane", "6 allée des Sciences, 69100 Villeurbanne", "04 72 15 25 35", "contact@labosolutions-exemple.fr", "www.labosolutions-exemple.fr", "Consommables", "Réactifs, analyseurs"},
                {"DialyCare Services", "Olivier Petit", "17 av. des Alouettes, 69100 Villeurbanne", "04 78 55 66 77", "contact@dialycare-exemple.fr", "www.dialycare-exemple.fr", "Maintenance", "Hémodialyse, membranes"},
                {"SteriTech Équipements", "Nathalie Roussel", "2 rue de l'Industrie, 69620 Villeurbanne", "04 74 88 99 00", "contact@steritech-exemple.fr", "www.steritech-exemple.fr", "Équipements", "Autoclaves, stérilisation"},
                {"OxygenMed", "Hugo Fontaine", "30 chemin des Vignes, 69130 Écully", "04 71 21 31 41", "contact@oxygenmed-exemple.fr", "www.oxygenmed-exemple.fr", "Pneumatique", "Détendeurs, O₂ médical"},
                {"Praxis Import", "Laura Nguyen", "9 rue Pasteur, 69100 Villeurbanne", "04 76 12 23 34", "contact@praxisimport-exemple.fr", "www.praxisimport-exemple.fr", "Import", "Dispositifs médicaux divers"},
                {"NovaBio Instruments", "Anton Meyer", "14 rue du Bois, 69130 Écully", "04 71 44 55 66", "contact@novabio-exemple.fr", "www.novabio-exemple.fr", "Équipements", "Laboratoire, analyseurs"},
                {"ControlMed Pièces", "Aïcha Diallo", "5 impasse des Ateliers, 69800 Saint-Priest", "04 72 66 77 88", "contact@controlmed-exemple.fr", "www.controlmed-exemple.fr", "Pièces détachées", "Pièces et rechanges"}
        };
        for (String[] s : data) {
            supplierIds.add(Jdbc.insert(
                    "INSERT INTO suppliers(company, contact_person, address, phone, email, website, category, products, contract_status, notes) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], "ACTIF",
                    "Fournisseur référencé — dossier complet archivé."));
        }
    }

    private void seedUsers() {
        String hash = PasswordHasher.hash(DEMO_PASSWORD);
        String[][] data = {
                {"admin", "admin@medmaint.demo", "Karim Benali", "ADMIN", "+33 6 11 22 33 44", null},
                {"manager", "manager@medmaint.demo", "Claire Fontaine", "MANAGER", "+33 6 11 22 33 45", null},
                {"engineer", "engineer@medmaint.demo", "Julie Moreau", "ENGINEER", "+33 6 11 22 33 46", null},
                {"technician", "technician@medmaint.demo", "Thomas Leroy", "TECHNICIAN", "+33 6 11 22 33 47", null},
                {"service", "service@medmaint.demo", "Sophie Garnier", "DEPT_MANAGER", "+33 6 11 22 33 48", "0"}
        };
        List<Long> ids = new ArrayList<>();
        for (String[] u : data) {
            Long deptId = u[5] == null ? null : deptIds.get(Integer.parseInt(u[5]));
            long id = Jdbc.insert(
                    "INSERT INTO users(username, email, password_hash, full_name, role, department_id, phone, status, is_demo, notification_prefs) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    u[0], u[1], hash, u[2], u[3], deptId, u[4], "ACTIF", Boolean.TRUE, "ALL");
            ids.add(id);
            Jdbc.update("UPDATE users SET last_login=? WHERE id=?",
                    LocalDateTime.now().minusHours(ri(1, 72)), id);
        }
        adminId = ids.get(0);
        managerId = ids.get(1);
        engineerId = ids.get(2);
        techUserId = ids.get(3);
        serviceId = ids.get(4);
    }

    private void seedTechnicians() {
        String[][] data = {
                {"Thomas Leroy", "3", "Électronique médicale", "Habilitation électrique B2V, Certificat respirateur", "ACTIF", "DISPONIBLE"},
                {"Nadia Petit", "0", "Respiratoire", "Certificat ventilateur, PSC1", "ACTIF", "DISPONIBLE"},
                {"Marc Dubois", "4", "Mécanique / hydraulique", "Formation bloc opératoire", "ACTIF", "OCCUPE"},
                {"Élodie Rousseau", "3", "Imagerie", "Formation fabricant imagerie", "ACTIF", "DISPONIBLE"},
                {"Hugo Lambert", "7", "Stérilisation", "AFNOR NF EN 285", "ACTIF", "DISPONIBLE"},
                {"Sarah Nguyen", "7", "Laboratoire", "Formation étalonnage", "ACTIF", "ABSENT"},
                {"Paul Girard", "9", "Dialyse", "Hémodialyse, électricité médicale", "ACTIF", "DISPONIBLE"},
                {"Anaïs Mercier", "5", "Électricité médicale", "Habilitation B1V, thermique", "EN_CONGE", "ABSENT"}
        };
        int n = 1;
        for (String[] t : data) {
            Long userId = "Thomas Leroy".equals(t[0]) ? techUserId : null;
            long id = Jdbc.insert(
                    "INSERT INTO technicians(user_id, employee_no, full_name, department_id, specialty, phone, email, certifications, status, availability) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    userId, String.format("TEC-%03d", n), t[0], deptIds.get(Integer.parseInt(t[1])),
                    t[2], "+33 6 20 30 40 " + String.format("%02d", n),
                    "technicien" + n + "@medmaint.demo", t[3], t[4], t[5]);
            techIds.add(id);
            Jdbc.insert("INSERT INTO technician_skills(technician_id, skill, level) VALUES (?,?,?)",
                    id, t[2], pick("DEBUTANT", "INTERMEDIAIRE", "EXPERT"));
            if (n % 2 == 0) {
                Jdbc.insert("INSERT INTO technician_skills(technician_id, skill, level) VALUES (?,?,?)",
                        id, "Maintenance préventive", "EXPERT");
            }
            n++;
        }
    }

    private void seedContracts() {
        String[][] types = {{"MAINTENANCE", "Contrat de maintenance préventive — Parc respiratoire"},
                {"MAINTENANCE", "Contrat de maintenance — Imagerie"},
                {"LOCATION", "Location d'échographes mobiles"},
                {"PIECES", "Approvisionnement en pièces détachées"},
                {"MAINTENANCE", "Contrat cadre maintenance multi-services"},
                {"MAINTENANCE", "Maintenance autoclaves et stérilisation"},
                {"FORMATION", "Formation techniciens biomédicaux"},
                {"PIECES", "Fourniture consommables laboratoire"},
                {"MAINTENANCE", "Support hémodialyse 24/7"},
                {"AUDIT", "Audit de conformité dispositifs médicaux"},
                {"MAINTENANCE", "Maintenance moniteurs patients"},
                {"LOCATION", "Location défibrillateurs semi-automatiques"}};
        int sla = 0;
        String[] responseTimes = {"4 h ouvrées", "8 h ouvrées", "24 h ouvrées", "2 h — astreinte"};
        for (int i = 0; i < types.length; i++) {
            LocalDate start;
            LocalDate end;
            String status;
            switch (i) {
                case 3 -> { // expirant dans < 60 jours
                    start = day(700);
                    end = LocalDate.now().plusDays(20);
                    status = "EXPIRANT";
                }
                case 7 -> {
                    start = day(730);
                    end = LocalDate.now().plusDays(45);
                    status = "EXPIRANT";
                }
                case 5 -> { // expiré
                    start = day(800);
                    end = day(30);
                    status = "EXPIRE";
                }
                case 10 -> {
                    start = day(900);
                    end = day(200);
                    status = "EXPIRE";
                }
                case 11 -> { // suspendu
                    start = day(300);
                    end = day(-365);
                    status = "SUSPENDU";
                }
                default -> {
                    start = day(ri(200, 640));
                    end = day(-ri(120, 900));
                    status = "ACTIF";
                }
            }
            long id = Jdbc.insert(
                    "INSERT INTO contracts(contract_no, supplier_id, title, contract_type, start_date, end_date, annual_cost, sla, response_time, contact_name, contact_phone, contact_email, status, notes) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    "CTR-" + start.getYear() + "-" + String.format("%03d", i + 1),
                    supplierIds.get(i % supplierIds.size()),
                    types[i][1], types[i][0], start, end,
                    money(ri(6, 88) * 1000L),
                    pick("Préventif 4 visites/an, curatif inclus", "Préventif semestriel, pièces à part",
                            "Full-ride, pièces et main-d'œuvre incluses", "Visites trimestrielles + astreinte"),
                    responseTimes[sla++ % responseTimes.length],
                    pick("Julien Barbier", "Fatima Cherif", "Pierre Lemoine", "Inès Morel"),
                    "04 72 00 11 " + String.format("%02d", i),
                    "contrat" + (i + 1) + "@fournisseur-exemple.fr",
                    status,
                    "Révision contractuelle annuelle prévue. Annexes techniques disponibles.");
            contractIds.add(id);
            if ("EXPIRANT".equals(status)) {
                expiringContractIds.add(id);
            }
        }
    }

    private void seedEquipment() {
        Map<String, String[]> deptPool = new HashMap<>();
        deptPool.put("RESP", new String[]{"REA", "PNEU", "BLOC", "URG"});
        deptPool.put("MONI", new String[]{"REA", "CARD", "URG", "PED"});
        deptPool.put("ECG", new String[]{"CARD", "URG", "REA"});
        deptPool.put("ECHG", new String[]{"IMAG", "MAT", "PED"});
        deptPool.put("RADI", new String[]{"IMAG", "URG"});
        deptPool.put("DEFIB", new String[]{"URG", "CARD", "BLOC", "REA"});
        deptPool.put("POMP", new String[]{"REA", "PED", "MAT", "BLOC"});
        deptPool.put("AUTO", new String[]{"STER", "BLOC"});
        deptPool.put("INCU", new String[]{"MAT", "PED"});
        deptPool.put("DIAL", new String[]{"DIAL", "REA"});
        deptPool.put("ANAL", new String[]{"LABO", "PHAR"});
        deptPool.put("ANES", new String[]{"BLOC", "URG"});

        Map<String, String[]> names = new HashMap<>();
        names.put("RESP", new String[]{"Respirateur polivalent", "Respirateur d'anesthésie", "Ventilateur de réanimation"});
        names.put("MONI", new String[]{"Moniteur multiparamétrique", "Monitorage NIBP/SpO₂"});
        names.put("ECG", new String[]{"Électrocardiographe 12 dérivations", "Holter ECG 24 h"});
        names.put("ECHG", new String[]{"Échographe couleur", "Échographe duplex"});
        names.put("RADI", new String[]{"Radiologie mobile", "Table de radiologie"});
        names.put("DEFIB", new String[]{"Défibrillateur monitoré", "Défibrillateur de réanimation"});
        names.put("POMP", new String[]{"Pompe à seringue", "Pompe à perfusion volumétrique"});
        names.put("AUTO", new String[]{"Autoclave vapeur", "Stérilisateur vertical"});
        names.put("INCU", new String[]{"Couveuse néonatale", "Radiant chauffant néonatal"});
        names.put("DIAL", new String[]{"Moniteur d'hémodialyse", "Machine de dialyse"});
        names.put("ANAL", new String[]{"Analyseur d'hématologie", "Automate d'analyses"});
        names.put("ANES", new String[]{"Anesthésieur", "Console d'anesthésie"});

        Map<String, String[]> models = new HashMap<>();
        models.put("RESP", new String[]{"VentiLab 3000", "AeroVent 500", "PulmoCare V30"});
        models.put("MONI", new String[]{"MultiView 12", "VitalTrack 800", "CareScope 7"});
        models.put("ECG", new String[]{"CardioGraph X5", "ECG Pro 300"});
        models.put("ECHG", new String[]{"EchoWave 7", "SonoScan S12"});
        models.put("RADI", new String[]{"RadioMobile 100", "MobileRay 60"});
        models.put("DEFIB", new String[]{"DefibSafe 2", "HeartStart M50"});
        models.put("POMP", new String[]{"Injectomat 2000", "SyriFlow 12"});
        models.put("AUTO", new String[]{"SteriVap 250", "VapoClave 180"});
        models.put("INCU", new String[]{"ThermoNest 300", "BabyCare C20"});
        models.put("DIAL", new String[]{"DialyOne 600", "HemoTrack 9"});
        models.put("ANAL", new String[]{"HemaLyse 4000", "LaboStar 8"});
        models.put("ANES", new String[]{"Anesto 7", "MediGas 500"});

        String[] locDetails = {"Salle principale — mur nord", "Box 3 — pied de lit",
                "Couloir B — embase murale", "Salle de surveillance — chevet 2",
                "Réserve équipements — travée 2", "Salle de soins — bras articulé"};

        Map<String, Integer> perCat = new HashMap<>();
        for (int i = 0; i < 40; i++) {
            String catCode = catIds.isEmpty() ? "RESP" : categoryCode(i % catIds.size());
            int seq = perCat.merge(catCode, 1, Integer::sum);
            String code = catCode + "-" + String.format("%03d", seq);
            long catId = catIds.get(i % catIds.size());

            // service : pool compatible avec la catégorie
            String[] pool = deptPool.getOrDefault(catCode, new String[]{"REA"});
            String deptCode = pick(pool);
            long deptId = deptIds.get(deptIndex(deptCode));
            List<Long> locs = Jdbc.query("SELECT id FROM locations WHERE department_id=?", rs -> rs.getLong(1), deptId);
            Long locId = locs.isEmpty() ? null : locs.get(ri(0, locs.size() - 1));

            String criticality = switch (catCode) {
                case "RESP", "DEFIB", "DIAL", "ANES" -> pick("HAUTE", "VITALE", "HAUTE");
                case "AUTO", "RADI", "ECHG" -> pick("MOYENNE", "HAUTE");
                default -> pick("MOYENNE", "MOYENNE", "FAIBLE");
            };
            String risk = switch (criticality) {
                case "VITALE" -> "HAUT";
                case "HAUTE" -> "HAUT";
                case "FAIBLE" -> "BAS";
                default -> "MOYEN";
            };
            String deviceClass = switch (criticality) {
                case "VITALE" -> "Classe III";
                case "HAUTE" -> "Classe IIb";
                case "FAIBLE" -> "Classe I";
                default -> "Classe IIa";
            };
            String status = switch (i) {
                case 7, 15 -> "EN_MAINTENANCE";
                case 23 -> "HORS_SERVICE";
                case 31 -> "EN_ATTENTE_REPARATION";
                default -> "EN_SERVICE";
            };
            LocalDate purchase = LocalDate.of(ri(2015, 2025), ri(1, 12), ri(1, 28));
            LocalDate reception = purchase.plusDays(ri(3, 15));
            LocalDate commissioning = reception.plusDays(ri(5, 40));
            int warrantyMonths = pick(12, 24, 36);
            LocalDate warrantyEnd = commissioning.plusMonths(warrantyMonths);
            if (i == 5 || i == 21) { // garanties bientôt expirées
                warrantyEnd = LocalDate.now().plusDays(ri(8, 45));
                warrantyMonths = (int) ChronoUnit.MONTHS.between(commissioning, warrantyEnd);
            }
            String freq = frequencyOfCat(catId);

            Long contractId = (i % 3 != 2) ? contractIds.get(i % contractIds.size()) : null;
            Long responsible = switch (i % 4) {
                case 0 -> Long.valueOf(engineerId);
                case 1 -> Long.valueOf(managerId);
                default -> null;
            };

            long eqId = Jdbc.insert(
                    "INSERT INTO equipment(code, name, category_id, subcategory, manufacturer_id, model, serial_number, reference_number, equipment_type, "
                            + "criticality, risk_level, device_class, status, department_id, location_id, location_details, supplier_id, purchase_date, "
                            + "reception_date, commissioning_date, purchase_price, warranty_months, warranty_end_date, maintenance_type, pm_frequency, "
                            + "responsible_id, contract_id, power_supply, power_rating, dimensions, weight_kg, fabrication_year, notes, archived, created_by) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    code, pick(names.get(catCode)), catId,
                    chance(0.3) ? pick("Adulte", "Pédiatrie", "Urgence") : null,
                    (long) ri(1, 8), pick(models.get(catCode)),
                    "SN-" + code + "-01", "REF-" + code + "-" + ri(10, 99),
                    categoryName(catId), criticality, risk, deviceClass, status,
                    deptId, locId, pick(locDetails),
                    (long) ri(1, supplierIds.size()),
                    purchase, reception, commissioning,
                    money(ri(2, 140) * 1000L + ri(0, 9) * 100L),
                    warrantyMonths, warrantyEnd, "PREVENTIVE", freq,
                    responsible, contractId,
                    pick("230 V — 50 Hz", "230 V / 110 V", "Secteur + batterie de secours"),
                    pick("80 W", "150 W", "400 W", "650 W", "1 200 W"),
                    pick("60 x 45 x 130 cm", "30 x 25 x 15 cm", "120 x 70 x 55 cm", "45 x 40 x 22 cm"),
                    BigDecimal.valueOf(ri(3, 190)),
                    purchase.getYear() - ri(0, 2),
                    chance(0.35) ? pick("Révisé en interne", "Sous contrat fabricant", "Équipement prioritaire du service", "Maintenu par le pool biomédical") : null,
                    Boolean.FALSE, adminId);

            equipIds.add(eqId);
            equipDeptIds.add(deptId);
            equipCat.put(eqId, catId);
            if (contractId != null) {
                equipContract.put(eqId, contractId);
                Jdbc.insert("INSERT INTO contract_equipment(contract_id, equipment_id) VALUES (?,?)", contractId, eqId);
            }
            // localisation : rafraîchir les dates de maintenance plus tard via les plans
        }
    }

    private void seedChecklists() {
        record Item(String label, String hint) {
        }
        Map<String, List<Item>> defs = new HashMap<>();
        defs.put("RESP", List.of(
                new Item("Contrôle des alarmes visuelles et sonores", "Déclencher l'alarme basse pression"),
                new Item("Vérification du circuit patient et des joints", null),
                new Item("Contrôle de la cellule O₂ et du détendeur", "Taux O₂ 21 % → 100 %"),
                new Item("Test du débitmètre et de la valve de sécurité", null),
                new Item("Contrôle de la batterie de secours", "Autonomie ≥ 30 min"),
                new Item("Nettoyage et désinfection de la façade", null)));
        defs.put("MONI", List.of(
                new Item("Comparaison avec un appareil de référence", "Écart ≤ 5 %"),
                new Item("Contrôle des câbles ECG et SpO₂", null),
                new Item("Test des alarmes haute et basse", null),
                new Item("Vérification de l'écran et des touches", null),
                new Item("Vérification de la version logicielle", null)));
        defs.put("AUTO", List.of(
                new Item("Cycle Bowie-Dick quotidien", "Bulletin conforme"),
                new Item("Contrôle d'étanchéité de la porte", null),
                new Item("Vérification de la vanne de sécurité", "Contre-pression d'ouverture"),
                new Item("Contrôle de la qualité de l'eau (conductivité)", "≤ 30 µS/cm"),
                new Item("Nettoyage du générateur de vapeur", null)));
        defs.put("DEFIB", List.of(
                new Item("Test de choc à énergie réglée", "Énergie délivrée ±10 %"),
                new Item("Contrôle des pads et de leur péremption", null),
                new Item("Vérification du rythme de référence", null),
                new Item("Test d'autonomie de la batterie", "≥ 60 min"),
                new Item("Impression du rapport de test", null)));
        defs.put("ANAL", List.of(
                new Item("Étalonnage avec contrôle bas", null),
                new Item("Étalonnage avec contrôle haut", null),
                new Item("Contrôle de la ligne d'eau et des réactifs", null),
                new Item("Vérification des dates de péremption", null),
                new Item("Nettoyage de la cellule de lecture", null)));
        defs.put("POMP", List.of(
                new Item("Contrôle de précision du débit", "Écart ≤ 5 %"),
                new Item("Test des alarmes d'occlusion", null),
                new Item("Vérification des porte-seringues", null),
                new Item("Contrôle de la batterie interne", null),
                new Item("Nettoyage et désinfection", null)));

        int n = 1;
        for (Map.Entry<String, List<Item>> e : defs.entrySet()) {
            long catId = catIds.get(categoryIndex(e.getKey()));
            long checklistId = Jdbc.insert(
                    "INSERT INTO maintenance_checklists(name, category_id, description) VALUES (?,?,?)",
                    "Checklist — " + categoryName(catId), catId,
                    "Points de contrôle standardisés pour " + categoryName(catId).toLowerCase() + ".");
            catChecklist.put(catId, checklistId);
            List<long[]> items = new ArrayList<>();
            int pos = 1;
            for (Item it : e.getValue()) {
                long itemId = Jdbc.insert(
                        "INSERT INTO checklist_items(checklist_id, position, label, hint) VALUES (?,?,?,?)",
                        checklistId, pos, it.label(), it.hint());
                items.add(new long[]{itemId, pos});
                pos++;
            }
            checklistItems.put(checklistId, items);
            n++;
        }
    }

    private void seedPlans() {
        long[] overdue = {0, 5, 11, 19};
        long[] todayDue = {3, 14, 26};
        long[] weekDue = {7, 16, 22, 27};
        int freqDays;
        for (int i = 0; i < 28 && i < equipIds.size(); i++) {
            long eqId = equipIds.get(i);
            long catId = equipCat.get(eqId);
            String freq = frequencyOfCat(catId);
            freqDays = freqDays(freq);
            LocalDate nextDue;
            if (contains(overdue, i)) {
                nextDue = day(ri(3, 25));
            } else if (contains(todayDue, i)) {
                nextDue = LocalDate.now();
            } else if (contains(weekDue, i)) {
                nextDue = LocalDate.now().plusDays(ri(1, 6));
            } else {
                nextDue = LocalDate.now().plusDays(ri(8, 150));
            }
            LocalDate last = nextDue.minusDays(freqDays);
            if (last.isAfter(LocalDate.now().minusDays(1))) {
                last = LocalDate.now().minusDays(freqDays);
            }
            long days = ChronoUnit.DAYS.between(LocalDate.now(), nextDue);
            String status = days < 0 ? "EN_RETARD" : days == 0 ? "AUJOURD_HUI"
                    : days <= 7 ? "CETTE_SEMAINE" : "A_VENIR";
            Long checklistId = catChecklist.get(catId);
            long planId = Jdbc.insert(
                    "INSERT INTO maintenance_plans(equipment_id, maintenance_type, frequency, custom_days, checklist_id, responsible_id, "
                            + "last_performed, next_due, estimated_duration_min, required_parts, required_tools, instructions, status, active) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    eqId, "Préventif " + freqLabel(freq), freq, null, checklistId,
                    techIds.get(i % techIds.size()), last, nextDue, pick(30, 45, 60, 90, 120),
                    chance(0.4) ? "Filtres, joints d'étanchéité" : null,
                    pick("Multimètre, jeu d'embouts", "Stéthoscope médical, pressionmètre", "Clé dynamométrique, tournevis de précision"),
                    "Contrôle complet selon la procédure interne PM-"
                            + String.format("%03d", i + 1) + ". Traiter les anomalies par ordre de travail.",
                    status, Boolean.TRUE);
            planIds.add(planId);
            Jdbc.update("UPDATE equipment SET last_maintenance_date=?, next_maintenance_date=? WHERE id=?",
                    last, nextDue, eqId);
        }
    }

    private void seedSpareParts() {
        String[][] data = {
                {"Filtre HEPA", "Consommable", "MediVent"},
                {"Cartouche de filtration O₂", "Consommable", "Oxymed"},
                {"Détendeur double raccord", "Pneumatique", "Oxymed"},
                {"Circuit patient adulte usage unique", "Consommable", "MediVent"},
                {"Masque adulte taille M", "Consommable", "Praxis Import"},
                {"Capteur SpO₂ réutilisable", "Électronique", "CardioTech"},
                {"Sonde de pression artérielle", "Capteurs", "CardioTech"},
                {"Électrodes ECG (lot de 50)", "Consommable", "CardioPlus Distribution"},
                {"Batterie 12 V 2,3 Ah", "Énergie", "ControlMed Pièces"},
                {"Batterie lithium 24 V", "Énergie", "ControlMed Pièces"},
                {"Fusible T4A 250 V", "Électrique", "ControlMed Pièces"},
                {"Cordon d'alimentation médical", "Électrique", "ControlMed Pièces"},
                {"Joint torique kit (x20)", "Mécanique", "ControlMed Pièces"},
                {"Valve inspiratoire", "Mécanique", "MediVent"},
                {"Détendeur de sécurité", "Pneumatique", "Oxymed"},
                {"Manomètre de contrôle", "Instrumentation", "NovaBio Instruments"},
                {"Turbine à vide", "Mécanique", "SteriLab"},
                {"Kit de pompe à vide", "Mécanique", "SteriLab"},
                {"Écran tactile 15\"", "Informatique", "ControlMed Pièces"},
                {"Clavier médical étanche", "Informatique", "ControlMed Pièces"},
                {"Carte CPU principale", "Électronique", "ImageX"},
                {"Carte d'alimentation", "Électronique", "ImageX"},
                {"Connecteur série médical", "Électronique", "ControlMed Pièces"},
                {"Tubulure dialyse (lot de 5)", "Consommable", "DialyCare Services"},
                {"Filtre hémoconcentrateur", "Consommable", "DialyCare Services"},
                {"Ligne artérielle stérile", "Consommable", "DialyCare Services"},
                {"Seringue 50 ml stérile (boîte de 100)", "Consommable", "LaboSolutions France"},
                {"Lampe halogène 100 W", "Optique", "ImageX"},
                {"Miroir de réflecteur", "Optique", "Hospitech Distribution"},
                {"Courroie d'entraînement", "Mécanique", "ControlMed Pièces"},
                {"Régulateur de débit O₂", "Pneumatique", "Oxymed"},
                {"Détecteur de fumée interne", "Sécurité", "ControlMed Pièces"},
                {"Tête de nettoyage ultrasonore", "Maintenance", "SteriLab"},
                {"Kit de calibration CO₂", "Instrumentation", "NovaBio Instruments"},
                {"Sonde température centrale", "Capteurs", "CardioTech"}
        };
        int[] lowIndices = {4, 11, 18, 24, 31};
        for (int i = 0; i < data.length; i++) {
            int min = ri(3, 10);
            int qty;
            if (contains(lowIndices, i)) {
                qty = Math.max(0, min - ri(1, 3));
            } else {
                qty = min + ri(2, 40);
            }
            int max = min + ri(20, 60);
            LocalDate expiry = null;
            if ("Consommable".equals(data[i][1])) {
                expiry = LocalDate.now().plusDays(contains(new int[]{8, 20}, i) ? ri(15, 55) : ri(90, 720));
            }
            long id = Jdbc.insert(
                    "INSERT INTO spare_parts(part_no, name, category, manufacturer, compatible_equipment, quantity, min_quantity, max_quantity, "
                            + "unit_cost, supplier_id, storage_location, expiry_date) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    "PIE-" + String.format("%04d", i + 1), data[i][0], data[i][1], data[i][2],
                    pick("Parc respiratoire", "Parc imagerie", "Parc monitoring", "Tous équipements", "Stérilisation"),
                    qty, min, max, money(ri(4, 900)),
                    supplierIds.get(i % supplierIds.size()),
                    pick("Armoire A-2", "Armoire B-1", "Réserve centrale — travée 3", "Atelier — bac 4"),
                    expiry);
            partIds.add(id);
        }
    }

    private void seedRequests() {
        String[] statuses = {
                "CLOTUREE", "CLOTUREE", "CLOTUREE", "CLOTUREE", "CLOTUREE", "CLOTUREE", "CLOTUREE", "CLOTUREE", "CLOTUREE", "CLOTUREE",
                "RESOLUE", "RESOLUE", "RESOLUE", "RESOLUE",
                "REJETEE", "REJETEE",
                "EN_ATTENTE_PIECE", "EN_ATTENTE_PIECE",
                "EN_COURS", "EN_COURS",
                "ASSIGNEE", "ASSIGNEE", "ASSIGNEE",
                "ACCEPTEE", "ACCEPTEE",
                "EN_ATTENTE", "EN_ATTENTE",
                "NOUVELLE", "NOUVELLE", "NOUVELLE"
        };
        String[] problems = {
                "L'appareil démarre puis s'arrête au bout de quelques minutes, alarme « pression basse ».",
                "Écran affiche un message d'erreur et reste bloqué au démarrage.",
                "Bruit anormal en fonctionnement (sifflement) accompagné d'une vibration.",
                "Fuite d'air au niveau du raccord principal, pression instable.",
                "Le capteur ne fournit plus de mesures fiables, valeurs aberrantes affichées.",
                "L'appareil ne charge plus la batterie, fonctionne uniquement sur secteur.",
                "Alarme intempestive toutes les 10 minutes malgré un contrôle du circuit.",
                "Défaillance du bras articulé : position non maintenue.",
                "Arrêt imprévu en cours d'utilisation, redémarrage nécessaire.",
                "L'imprimante intégrée ne délivre plus aucun rapport.",
                "L'éclairage de fond est partiellement défaillant.",
                "La porte ne ferme plus hermétiquement, le cycle est interrompu."};
        String[] symptoms = {
                "Perte de pression, alarme sonore continue",
                "Écran figé, voyant rouge allumé",
                "Vibration et sifflement au démarrage",
                "Pression qui chute en fonctionnement",
                "Valeurs SpO₂ incohérentes",
                "Batterie non rechargeable",
                "Alarmes répétées sans cause identifiée",
                "Bras qui ne tient plus la position",
                "Redémarrage spontané",
                "Aucune sortie imprimante",
                "Rétroéclairage intermittent",
                "Cycle stérilisation avorté"};
        long[] requesterPool = {serviceId, engineerId, managerId, serviceId, engineerId};

        int seq = 1;
        for (int i = 0; i < statuses.length; i++) {
            String st = statuses[i];
            int age = switch (st) {
                case "CLOTUREE" -> ri(45, 340);
                case "RESOLUE" -> ri(20, 90);
                case "REJETEE" -> ri(30, 200);
                case "EN_ATTENTE_PIECE" -> ri(5, 30);
                case "EN_COURS" -> ri(3, 18);
                case "ASSIGNEE" -> ri(2, 12);
                case "ACCEPTEE" -> ri(2, 10);
                case "EN_ATTENTE" -> ri(1, 7);
                default -> ri(0, 4);
            };
            LocalDateTime reported = dateTime(age, ri(7, 19));
            long eqId = equipIds.get(ri(0, equipIds.size() - 1));
            long deptId = equipDeptIds.get(equipIds.indexOf(eqId));
            String priority = switch (ri(1, 20)) {
                case 1 -> "CRITIQUE";
                case 2, 3 -> "URGENTE";
                case 4, 5, 6 -> "HAUTE";
                case 7, 8, 9, 10 -> "FAIBLE";
                default -> "NORMALE";
            };
            if (i == 28) { // une demande critique récente pour le tableau de bord
                priority = "CRITIQUE";
            }
            Long assigned = null;
            if (List.of("ASSIGNEE", "EN_COURS", "EN_ATTENTE_PIECE", "RESOLUE", "CLOTUREE").contains(st)) {
                assigned = techIds.get(ri(0, techIds.size() - 1));
            }
            String resolution = List.of("RESOLUE", "CLOTUREE").contains(st)
                    ? pick("Intervention réalisée, contrôle fonctionnel conforme.",
                            "Pièce remplacée, essai sous charge concluant.",
                            "Réglage effectué, paramètres de référence retrouvés.") : null;
            String rejection = "REJETEE".equals(st)
                    ? pick("Dysfonctionnement d'usage (aucune anomalie détectée).",
                            "Demande en doublon d'une intervention en cours.",
                            "Équipement en cours de réforme, remplacement planifié.") : null;

            long id = Jdbc.insert(
                    "INSERT INTO intervention_requests(request_no, equipment_id, department_id, requester_id, reported_at, problem_description, "
                            + "symptoms, priority, safety_impact, availability, status, assigned_technician_id, rejection_reason, resolution_note) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    "DEM-" + reported.getYear() + "-" + String.format("%04d", seq++),
                    eqId, deptId, requesterPool[i % requesterPool.length], reported,
                    problems[i % problems.length], symptoms[i % symptoms.length],
                    priority,
                    "URGENTE".equals(priority) || "CRITIQUE".equals(priority) ? chance(0.6) : chance(0.1),
                    pick("INDISPONIBLE", "DEGRADE", "TOUTE_FONCTION"),
                    st, assigned, rejection, resolution);
            requestIds.add(id);
        }
    }

    private void seedWorkOrders() {
        String[] failureTypes = {"ÉLECTRIQUE", "MÉCANIQUE", "LOGICIEL", "USURE", "RÉGLAGE", "PNEUMATIQUE"};
        String[] diagnoses = {
                "Condensateur HS sur la carte d'alimentation.",
                "Détendeur colmaté, débit d'entrée insuffisant.",
                "Mise à jour logicielle non appliquée, version corrompue.",
                "Joints d'étanchéité déformés, perte de pression.",
                "Capteur hors tolérance après étalonnage.",
                "Batterie en fin de vie, autonomie < 10 min.",
                "Roulement de turbine usé, bruit anormal.",
                "Connecteur oxyde, contact intermittent."};
        String[] actions = {
                "Remplacement de la carte d'alimentation et test de mise sous tension.",
                "Détendeur remplacé, purge et régulation vérifiées.",
                "Reflash du microprogramme et remise en service.",
                "Kit de joints remplacé, contrôle d'étanchéité conforme.",
                "Étalonnage complet, valeurs de référence retrouvées.",
                "Batterie remplacée, cycle de charge contrôlé.",
                "Turbine déposée, roulement remplacé, vibrations dans les limites.",
                "Connecteur remplacé, câblage contrôlé."};
        String[] causes = {"Vieillissement composant", "Usure normale", "Defaut de fabrication",
                "Absence de maintenance", "Paramétrage incorrect", "Facteur environnemental (humidité)"};

        int seq = 1;
        // --- Ordres liés aux demandes ---
        for (int i = 0; i < requestIds.size(); i++) {
            long reqId = requestIds.get(i);
            String reqStatus = requestStatus(i);
            if (!List.of("ASSIGNEE", "EN_COURS", "EN_ATTENTE_PIECE", "RESOLUE", "CLOTUREE").contains(reqStatus)) {
                continue;
            }
            String woStatus = switch (reqStatus) {
                case "CLOTUREE" -> "CLOTUREE";
                case "RESOLUE" -> "RESOLUE";
                case "EN_ATTENTE_PIECE" -> "EN_ATTENTE_PIECE";
                case "EN_COURS" -> "EN_COURS";
                default -> "ASSIGNEE";
            };
            int openedAge = switch (reqStatus) {
                case "CLOTUREE" -> ri(44, 338);
                case "RESOLUE" -> ri(18, 85);
                case "EN_ATTENTE_PIECE" -> ri(4, 28);
                case "EN_COURS" -> ri(2, 16);
                default -> ri(1, 10);
            };
            createWorkOrder(reqId, woStatus, openedAge, failureTypes, diagnoses, actions, causes, seq++);
        }
        // --- Ordres autonomes (curatif historique + file en cours) ---
        String[][] standalone = {
                {"CLOTUREE", null}, {"CLOTUREE", null}, {"CLOTUREE", null}, {"CLOTUREE", null},
                {"CLOTUREE", null}, {"CLOTUREE", null}, {"CLOTUREE", null}, {"CLOTUREE", null},
                {"CLOTUREE", null}, {"CLOTUREE", null}, {"CLOTUREE", null}, {"CLOTUREE", null},
                {"CLOTUREE", null}, {"VALIDEE", null}, {"VALIDEE", null},
                {"PLANIFIEE", "+3"}, {"PLANIFIEE", "+7"},
                {"DEMANDE", "-1"}, {"DEMANDE", "-2"},
                {"EN_COURS", "-4"}
        };
        for (String[] s : standalone) {
            int openedAge = switch (s[0]) {
                case "CLOTUREE", "VALIDEE" -> ri(20, 350);
                case "EN_COURS" -> ri(2, 8);
                case "DEMANDE" -> ri(0, 3);
                default -> ri(5, 15);
            };
            createWorkOrder(null, s[0], openedAge, failureTypes, diagnoses, actions, causes, seq++);
        }
    }

    private void createWorkOrder(Long requestId, String status, int openedAge,
                                 String[] failureTypes, String[] diagnoses, String[] actions, String[] causes,
                                 int seq) {
        LocalDateTime opened = dateTime(openedAge, ri(7, 17));
        long eqId;
        String priority;
        if (requestId != null) {
            int idx = requestIndexOf(requestId);
            eqId = requestEquipment(idx);
            priority = requestPriority(idx);
        } else {
            eqId = equipIds.get(ri(0, equipIds.size() - 1));
            priority = switch (ri(1, 20)) {
                case 1 -> "CRITIQUE";
                case 2, 3 -> "URGENTE";
                case 4, 5, 6 -> "HAUTE";
                case 7, 8, 9, 10 -> "FAIBLE";
                default -> "NORMALE";
            };
        }
        LocalDateTime scheduled = null;
        LocalDateTime started = null;
        LocalDateTime completed = null;
        LocalDateTime validated = null;
        LocalDateTime closed = null;
        switch (status) {
            case "PLANIFIEE" -> scheduled = LocalDateTime.of(LocalDate.now().plusDays(ri(1, 10)), LocalTime.of(8, 30));
            case "ASSIGNEE" -> scheduled = LocalDateTime.of(LocalDate.now().plusDays(ri(1, 5)), LocalTime.of(8, 30));
            case "EN_COURS" -> {
                started = opened.plusDays(ri(0, 2));
                if (started.isAfter(LocalDateTime.now())) {
                    started = LocalDateTime.now().minusHours(ri(2, 30));
                }
            }
            case "EN_ATTENTE_PIECE" -> started = opened.plusDays(ri(1, 3));
            case "RESOLUE" -> {
                started = opened.plusHours(ri(2, 30));
                completed = started.plusHours(ri(1, 48));
            }
            case "VALIDEE" -> {
                started = opened.plusHours(ri(2, 30));
                completed = started.plusHours(ri(1, 48));
                validated = completed.plusDays(ri(0, 3));
            }
            case "CLOTUREE" -> {
                started = opened.plusHours(ri(2, 30));
                completed = started.plusHours(ri(1, 48));
                if (completed.isAfter(LocalDateTime.now())) {
                    completed = LocalDateTime.now().minusHours(ri(6, 48));
                }
                validated = completed.plusDays(ri(0, 3));
                closed = validated.plusDays(ri(0, 4));
            }
            default -> {
                // DEMANDE / APPROUVEE : rien de planifié
            }
        }
        boolean done = completed != null;
        int laborHours = done ? ri(1, 8) : 0;
        long partsCost = done && chance(0.55) ? ri(2, 45) * 100L : 0;
        long laborCost = laborHours * 65L;
        long total = laborCost + partsCost;
        String diagnosis = done ? pick(diagnoses) : null;
        String corrective = done ? pick(actions) : null;
        Long techId = List.of("ASSIGNEE", "EN_COURS", "EN_ATTENTE_PIECE", "RESOLUE", "VALIDEE", "CLOTUREE").contains(status)
                ? techIds.get(ri(0, techIds.size() - 1)) : null;

        long id = Jdbc.insert(
                "INSERT INTO work_orders(wo_no, request_id, equipment_id, technician_id, team, priority, status, opened_at, scheduled_at, "
                        + "started_at, completed_at, validated_at, closed_at, diagnosis, failure_cause, corrective_action, work_performed, "
                        + "failure_type, labor_hours, downtime_hours, labor_cost, parts_cost, total_cost, comments, created_by, validated_by) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "OT-" + opened.getYear() + "-" + String.format("%04d", seq),
                requestId, eqId, techId, pick("Équipe A", "Équipe B", null), priority, status,
                opened, scheduled, started, completed, validated, closed,
                diagnosis,
                done ? pick(causes) : null,
                corrective,
                corrective,
                done ? pick(failureTypes) : null,
                done ? BigDecimal.valueOf(laborHours) : null,
                done ? BigDecimal.valueOf(ri(1, 24)) : null,
                BigDecimal.valueOf(laborCost), BigDecimal.valueOf(partsCost), BigDecimal.valueOf(total),
                chance(0.4) ? pick("Client satisfait.", "À surveiller au prochain préventif.", "Pièce en commande.", null) : null,
                engineerId,
                validated != null ? managerId : null);
        workOrders.add(new WoInfo(id, eqId, openedAge, status, completed));
    }

    private void seedMaintenanceRecords() {
        // 1) Historique préventif à partir des plans
        int recSeq = 1;
        for (int p = 0; p < planIds.size(); p++) {
            long planId = planIds.get(p);
            PlanRow plan = Jdbc.queryOne(
                    "SELECT equipment_id, frequency, checklist_id, responsible_id, last_performed FROM maintenance_plans WHERE id=?",
                    rs -> new PlanRow(rs.getLong(1), rs.getString(2), (Long) rs.getObject(3),
                            (Long) rs.getObject(4), rs.getObject(5, LocalDate.class)),
                    planId).orElse(null);
            if (plan == null) {
                continue;
            }
            int fd = freqDays(plan.frequency());
            int executions = switch (fd) {
                case 7 -> 8;
                case 30 -> 6;
                case 91 -> 4;
                case 182 -> 3;
                case 365 -> 2;
                default -> 3;
            };
            LocalDate last = plan.lastPerformed() != null ? plan.lastPerformed() : day(fd);
            for (int k = 0; k < executions; k++) {
                LocalDate performed = last.minusDays((long) k * fd);
                if (performed.isBefore(LocalDate.now().minusDays(364))) {
                    continue;
                }
                boolean conform = chance(0.9);
                long recordId = Jdbc.insert(
                        "INSERT INTO maintenance_records(equipment_id, plan_id, checklist_id, record_type, performed_by, performed_at, "
                                + "result, observations, duration_min, cost) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        plan.equipmentId(), planId, plan.checklistId(), "PREVENTIVE",
                        chance(0.7) ? techUserId : engineerId,
                        LocalDateTime.of(performed, LocalTime.of(ri(8, 17), ri(0, 59))),
                        conform ? "CONFORME" : pick("NON_CONFORME", "NON_APPLICABLE"),
                        conform ? pick("Maintenance réalisée dans les temps.", "Aucune anomalie relevée.",
                                        "Contrôle conforme, équipement remis en service.", null)
                                : "Anomalie mineure relevée : pièces à commander.",
                        pick(30, 45, 60, 90, 120),
                        chance(0.3) ? money(ri(1, 6) * 100L) : BigDecimal.ZERO);
                if (plan.checklistId() != null && plan.checklistId() > 0) {
                    seedChecklistResults(recordId, plan.checklistId(), conform);
                }
                recSeq++;
            }
        }
        // 2) Historique correctif à partir des ordres terminés
        for (WoInfo w : workOrders) {
            if (w.completedAt() == null) {
                continue;
            }
            Jdbc.insert(
                    "INSERT INTO maintenance_records(equipment_id, work_order_id, record_type, performed_by, performed_at, result, "
                            + "observations, duration_min, cost) VALUES (?,?,?,?,?,?,?,?,?)",
                    w.equipmentId(), w.id(), "CORRECTIVE", techUserId, w.completedAt(),
                    chance(0.92) ? "CONFORME" : "NON_CONFORME",
                    "Intervention corrective réalisée, contrôle fonctionnel effectué.",
                    pick(45, 60, 90, 120, 180), money(ri(1, 9) * 100L));
            recSeq++;
        }
    }

    private void seedChecklistResults(long recordId, long checklistId, boolean conformRecord) {
        List<long[]> items = checklistItems.get(checklistId);
        if (items == null) {
            return;
        }
        for (long[] item : items) {
            long itemId = item[0];
            String label = Jdbc.queryOne("SELECT label FROM checklist_items WHERE id=?", rs -> rs.getString(1), itemId)
                    .orElse("Point de contrôle");
            boolean ok = conformRecord ? chance(0.95) : chance(0.5);
            Jdbc.insert("INSERT INTO checklist_results(record_id, item_id, item_label, result, notes) VALUES (?,?,?,?,?)",
                    recordId, itemId, label,
                    ok ? "CONFORME" : pick("NON_CONFORME", "NON_APPLICABLE"),
                    ok ? (chance(0.2) ? "Vérifié deux fois." : null)
                            : pick("Écart mesuré hors tolérance.", "Pièce à remplacer au prochain passage.", null));
        }
    }

    private void seedStockTransactions() {
        // Consommations rattachées à des ordres terminés
        Map<Long, Integer> used = new HashMap<>();
        List<long[]> usages = new ArrayList<>(); // {partIdx, woIdx, qty}
        int usageCount = 0;
        for (int i = 0; i < 16 && usageCount < workOrders.size(); i++) {
            WoInfo w = workOrders.get(i);
            if (w.completedAt() == null) {
                continue;
            }
            int partIdx = ri(0, partIds.size() - 1);
            int qty = ri(1, 3);
            used.merge(partIds.get(partIdx), qty, Integer::sum);
            usages.add(new long[]{partIdx, i, qty});
            usageCount++;
        }
        for (int i = 0; i < partIds.size(); i++) {
            long partId = partIds.get(i);
            int current = Jdbc.queryOne("SELECT quantity FROM spare_parts WHERE id=?", rs -> rs.getInt(1), partId).orElse(0);
            int initial = current + used.getOrDefault(partId, 0);
            Jdbc.insert("INSERT INTO stock_transactions(part_id, tx_type, quantity, unit_cost, user_id, note, created_at) VALUES (?,?,?,?,?,?,?)",
                    partId, "ENTREE", initial, money(ri(4, 900)), engineerId,
                    "Stock initial constaté lors du démarrage du système.",
                    dateTime(ri(60, 400), 9));
        }
        for (long[] u : usages) {
            WoInfo w = workOrders.get((int) u[1]);
            Jdbc.insert(
                    "INSERT INTO stock_transactions(part_id, tx_type, quantity, unit_cost, work_order_id, equipment_id, user_id, note, created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?)",
                    partIds.get((int) u[0]), "USAGE", (int) u[2], money(ri(4, 900)),
                    w.id(), w.equipmentId(), techUserId,
                    "Consommation sur intervention.", w.completedAt() != null ? w.completedAt() : dateTime(10, 11));
        }
    }

    private void seedNotifications() {
        // Demandes récentes pour les responsables
        List<Long> recentReq = Jdbc.query(
                "SELECT id FROM intervention_requests WHERE status='NOUVELLE' ORDER BY reported_at DESC",
                rs -> rs.getLong(1));
        for (Long reqId : recentReq) {
            notifyUser(managerId, "NOUVELLE_DEMANDE", "Nouvelle demande d'intervention",
                    "Une demande d'intervention vient d'être soumise et attend un tri.", "INFO",
                    "REQUEST", reqId, ri(0, 3), false);
        }
        // Plans en retard
        List<Long> latePlans = Jdbc.query(
                "SELECT id FROM maintenance_plans WHERE status='EN_RETARD' ORDER BY next_due", rs -> rs.getLong(1));
        for (Long planId : latePlans) {
            notifyUser(engineerId, "PM_EN_RETARD", "Maintenance préventive en retard",
                    "Un plan de maintenance préventive a dépassé sa date d'échéance.", "WARN",
                    "MAINTENANCE_PLAN", planId, ri(1, 6), false);
        }
        // Stock bas
        List<Long> lowParts = Jdbc.query(
                "SELECT id FROM spare_parts WHERE quantity < min_quantity", rs -> rs.getLong(1));
        for (Long partId : lowParts) {
            notifyUser(managerId, "STOCK_FAIBLE", "Stock de pièces bas",
                    "Le stock est passé sous le seuil minimum, merci de prévoir une commande.", "WARN",
                    "SPARE_PART", partId, ri(1, 8), false);
        }
        // Contrats à renouveler
        for (Long contractId : expiringContractIds) {
            notifyUser(managerId, "CONTRAT_EXPIRATION", "Contrat bientôt expiré",
                    "Un contrat arrive à échéance dans moins de 60 jours.", "WARN",
                    "CONTRACT", contractId, ri(2, 10), false);
        }
        // Interventions assignées
        List<Long> assignedWo = Jdbc.query(
                "SELECT id FROM work_orders WHERE status IN ('ASSIGNEE','PLANIFIEE') ORDER BY opened_at DESC",
                rs -> rs.getLong(1));
        for (Long woId : assignedWo) {
            notifyUser(techUserId, "INTERVENTION_ASSIGNEE", "Intervention assignée",
                    "Un ordre de travail vous a été assigné.", "INFO", "WORK_ORDER", woId, ri(0, 5), false);
        }
        // Ciblé rôle techniciens — panne critique
        List<Long> critWo = Jdbc.query(
                "SELECT id FROM work_orders WHERE priority IN ('CRITIQUE','URGENTE') ORDER BY opened_at DESC LIMIT 2",
                rs -> rs.getLong(1));
        for (Long woId : critWo) {
            notifyRole("TECHNICIAN", "PANNE_CRITIQUE", "Panne critique signalée",
                    "Un équipement critique est arrêté, intervention prioritaire requise.", "CRITICAL",
                    "WORK_ORDER", woId, ri(0, 4), false);
        }
        // Notifications déjà lues
        List<Long> doneWo = Jdbc.query(
                "SELECT id FROM work_orders WHERE status='CLOTUREE' ORDER BY closed_at DESC LIMIT 3",
                rs -> rs.getLong(1));
        for (Long woId : doneWo) {
            notifyUser(serviceId, "INTERVENTION_TERMINEE", "Intervention terminée",
                    "Un ordre de travail a été clôturé sur votre service.", "OK",
                    "WORK_ORDER", woId, ri(5, 30), true);
        }
        notifyRole("MANAGER", "VALIDATION_REQUISE", "Validation nécessaire",
                "Des ordres de travail attendent votre validation.", "WARN", null, null, ri(3, 12), true);
        notifyUser(adminId, "EQUIPEMENT_AJOUTE", "Parc équipements mis à jour",
                "De nouveaux équipements ont été intégrés au parc.", "INFO", null, null, ri(4, 20), true);
        notifyUser(engineerId, "GARANTIE_EXPIRATION", "Garantie bientôt expirée",
                "La garantie de deux équipements se termine sous 45 jours.", "INFO", null, null, ri(6, 25), true);
    }

    private void notifyUser(Long userId, String type, String title, String message, String severity,
                            String entityType, Long entityId, int ageDays, boolean read) {
        Jdbc.insert("INSERT INTO notifications(user_id, role_code, type, title, message, severity, entity_type, entity_id, is_read, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                userId, null, type, title, message, severity, entityType, entityId, read,
                dateTime(ageDays, ri(7, 19)));
    }

    private void notifyRole(String role, String type, String title, String message, String severity,
                            String entityType, Long entityId, int ageDays, boolean read) {
        Jdbc.insert("INSERT INTO notifications(user_id, role_code, type, title, message, severity, entity_type, entity_id, is_read, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                null, role, type, title, message, severity, entityType, entityId, read,
                dateTime(ageDays, ri(7, 19)));
    }

    private void seedAuditLogs() {
        long[][] users = {{adminId, 0}, {managerId, 1}, {engineerId, 2}, {techUserId, 3}, {serviceId, 4}};
        String[] names = {"Karim Benali", "Claire Fontaine", "Julie Moreau", "Thomas Leroy", "Sophie Garnier"};
        int i = 0;
        for (long[] u : users) {
            audit(u[0], names[(int) u[1]], "LOGIN", "SESSION", String.valueOf(u[1]),
                    "Connexion au système", null, "Succès", ri(0, 10));
            i++;
        }
        for (int k = 0; k < 12 && k < equipIds.size(); k++) {
            audit(adminId, "Karim Benali", "CREATE", "EQUIPMENT", String.valueOf(equipIds.get(k)),
                    "Équipement " + equipmentCode(equipIds.get(k)), null, "Créé", ri(10, 330));
        }
        for (int k = 0; k < 8 && k < requestIds.size(); k++) {
            audit(serviceId, "Sophie Garnier", "CREATE", "REQUEST", String.valueOf(requestIds.get(k)),
                    "Demande d'intervention", null, "NOUVELLE", ri(5, 200));
        }
        int c = 0;
        for (WoInfo w : workOrders) {
            if (c >= 10) {
                break;
            }
            audit(engineerId, "Julie Moreau", "UPDATE", "WORK_ORDER", String.valueOf(w.id()),
                    "Ordre de travail", "ASSIGNEE", w.status(), (int) Math.min(w.openedDayOffset(), 300));
            c++;
        }
        for (Long contractId : contractIds) {
            audit(managerId, "Claire Fontaine", "UPDATE", "CONTRACT", String.valueOf(contractId),
                    "Contrat", null, "Révision annuelle", ri(20, 300));
        }
        audit(engineerId, "Julie Moreau", "EXPORT", "REPORT", null,
                "Rapport parc équipements", null, "CSV", ri(1, 15));
        audit(managerId, "Claire Fontaine", "EXPORT", "REPORT", null,
                "Rapport coûts de maintenance", null, "PDF", ri(1, 20));
        audit(adminId, "Karim Benali", "UPDATE", "SETTINGS", null,
                "Paramètres système", "Fréquence PM par défaut", "Mensuelle", ri(2, 30));
        audit(adminId, "Karim Benali", "CREATE", "USER", String.valueOf(serviceId),
                "Compte Sophie Garnier", null, "ACTIF", ri(30, 300));
        audit(managerId, "Claire Fontaine", "UPDATE", "TECHNICIAN", String.valueOf(techIds.get(0)),
                "Technicien Thomas Leroy", "DISPONIBLE", "OCCUPE", ri(0, 5));
        audit(engineerId, "Julie Moreau", "DELETE", "REQUEST", requestIds.isEmpty() ? null : String.valueOf(requestIds.get(requestIds.size() - 1)),
                "Demande en doublon", "NOUVELLE", "Supprimée", ri(3, 40));
    }

    private void audit(long userId, String userName, String action, String entityType, String entityId,
                       String label, String oldValue, String newValue, int ageDays) {
        Jdbc.insert("INSERT INTO audit_logs(user_id, user_name, action, entity_type, entity_id, entity_label, old_value, new_value, created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                userId, userName, action, entityType, entityId, label, oldValue, newValue,
                dateTime(ageDays, ri(7, 19)));
    }

    private void seedDocuments() {
        try {
            Path dir = AppConfig.get().uploadDir();
            Files.createDirectories(dir);
            for (int i = 0; i < 4 && i < equipIds.size(); i++) {
                long eqId = equipIds.get(i);
                String code = equipmentCode(eqId);
                String filename = switch (i) {
                    case 0 -> "Manuel utilisateur — " + code + ".pdf";
                    case 1 -> "Manuel technique — " + code + ".pdf";
                    case 2 -> "Attestation de garantie — " + code + ".pdf";
                    default -> "Fiche de maintenance — " + code + ".pdf";
                };
                String category = switch (i) {
                    case 0 -> "MANUEL_UTILISATEUR";
                    case 1 -> "MANUEL_TECHNIQUE";
                    case 2 -> "GARANTIE";
                    default -> "RAPPORT_INSPECTION";
                };
                String stored = "equip-" + eqId + "-" + i + ".pdf";
                writePdf(dir.resolve(stored), filename,
                        List.of("Équipement : " + code + " — " + equipmentName(eqId),
                                "Document fictif généré pour la démonstration MedMaint.",
                                "Aucune donnée patient. Références internes PM-00" + (i + 1) + ".",
                                "Contrôle periodique recommandé selon la catégorie."));
                Jdbc.insert("INSERT INTO documents(entity_type, entity_id, filename, stored_name, mime_type, category, size_bytes, uploaded_by, description) "
                                + "VALUES (?,?,?,?,?,?,?,?,?)",
                        "EQUIPMENT", eqId, filename, stored, "application/pdf", category,
                        Files.size(dir.resolve(stored)), engineerId,
                        "Document de démonstration rattaché à l'équipement " + code + ".");
            }
            if (!contractIds.isEmpty()) {
                long contractId = contractIds.get(0);
                String stored = "contract-" + contractId + ".pdf";
                writePdf(dir.resolve(stored), "Contrat de maintenance.pdf",
                        List.of("Contrat de maintenance préventive — exemplaire de démonstration.",
                                "Parties : établissement fictif et fournisseur fictif.",
                                "Aucune donnée confidentielle."));
                Jdbc.insert("INSERT INTO documents(entity_type, entity_id, filename, stored_name, mime_type, category, size_bytes, uploaded_by, description) "
                                + "VALUES (?,?,?,?,?,?,?,?,?)",
                        "CONTRACT", contractId, "Contrat de maintenance.pdf", stored, "application/pdf",
                        "CONTRAT", Files.size(dir.resolve(stored)), managerId,
                        "Scan du contrat cadre (données de démonstration).");
            }
        } catch (Exception e) {
            throw new DbException("Impossible de créer les documents de démonstration.", e);
        }
    }

    private static void writePdf(Path target, String title, List<String> lines) throws Exception {
        try (OutputStream os = Files.newOutputStream(target)) {
            Document pdf = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(pdf, os);
            pdf.open();
            pdf.add(new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f)));
            pdf.add(new Paragraph("MedMaint — GMAO maintenance biomédicale (démonstration)",
                    FontFactory.getFont(FontFactory.HELVETICA, 9f)));
            pdf.add(new Paragraph(" "));
            for (String line : lines) {
                pdf.add(new Paragraph(line, FontFactory.getFont(FontFactory.HELVETICA, 11f)));
            }
            pdf.close();
        }
    }

    // ------------------------------------------------------------------
    // Utilitaires de lecture pendant le seed
    // ------------------------------------------------------------------

    private record PlanRow(long equipmentId, String frequency, Long checklistId, Long responsibleId,
                           LocalDate lastPerformed) {
    }

    private final Map<Integer, String> requestStatusCache = new HashMap<>();
    private final Map<Integer, Long> requestEquipCache = new HashMap<>();
    private final Map<Integer, String> requestPriorityCache = new HashMap<>();

    private String requestStatus(int index) {
        return requestStatusCache.computeIfAbsent(index, i -> Jdbc.queryOne(
                "SELECT status FROM intervention_requests WHERE id=?", rs -> rs.getString(1),
                requestIds.get(i)).orElse("NOUVELLE"));
    }

    private int requestIndexOf(long requestId) {
        int idx = requestIds.indexOf(requestId);
        return Math.max(0, idx);
    }

    private long requestEquipment(int index) {
        return requestEquipCache.computeIfAbsent(index, i -> Jdbc.queryOne(
                "SELECT equipment_id FROM intervention_requests WHERE id=?", rs -> rs.getLong(1),
                requestIds.get(i)).orElse(equipIds.get(0)));
    }

    private String requestPriority(int index) {
        return requestPriorityCache.computeIfAbsent(index, i -> Jdbc.queryOne(
                "SELECT priority FROM intervention_requests WHERE id=?", rs -> rs.getString(1),
                requestIds.get(i)).orElse("NORMALE"));
    }

    private static int deptIndex(String code) {
        return switch (code) {
            case "REA" -> 0;
            case "CARD" -> 1;
            case "URG" -> 2;
            case "IMAG" -> 3;
            case "BLOC" -> 4;
            case "PED" -> 5;
            case "MAT" -> 6;
            case "LABO" -> 7;
            case "PHAR" -> 8;
            case "DIAL" -> 9;
            case "PNEU" -> 10;
            default -> 11;
        };
    }

    private static int categoryIndex(String code) {
        return switch (code) {
            case "RESP" -> 0;
            case "MONI" -> 1;
            case "ECG" -> 2;
            case "ECHG" -> 3;
            case "RADI" -> 4;
            case "DEFIB" -> 5;
            case "POMP" -> 6;
            case "AUTO" -> 7;
            case "INCU" -> 8;
            case "DIAL" -> 9;
            case "ANAL" -> 10;
            default -> 11;
        };
    }

    private String categoryCode(int catIndex) {
        return switch (catIndex) {
            case 0 -> "RESP";
            case 1 -> "MONI";
            case 2 -> "ECG";
            case 3 -> "ECHG";
            case 4 -> "RADI";
            case 5 -> "DEFIB";
            case 6 -> "POMP";
            case 7 -> "AUTO";
            case 8 -> "INCU";
            case 9 -> "DIAL";
            case 10 -> "ANAL";
            default -> "ANES";
        };
    }

    private String categoryName(long catId) {
        return Jdbc.queryOne("SELECT name FROM equipment_categories WHERE id=?", rs -> rs.getString(1), catId)
                .orElse("Équipement");
    }

    private String frequencyOfCat(long catId) {
        return Jdbc.queryOne("SELECT default_frequency FROM equipment_categories WHERE id=?", rs -> rs.getString(1), catId)
                .orElse("MENSUELLE");
    }

    private static int freqDays(String freq) {
        return switch (freq == null ? "" : freq) {
            case "JOURNALIERE" -> 1;
            case "HEBDOMADAIRE" -> 7;
            case "MENSUELLE" -> 30;
            case "TRIMESTRIELLE" -> 91;
            case "SEMESTRIELLE" -> 182;
            case "ANNUELLE" -> 365;
            default -> 30;
        };
    }

    private static String freqLabel(String freq) {
        return switch (freq == null ? "" : freq) {
            case "JOURNALIERE" -> "quotidien";
            case "HEBDOMADAIRE" -> "hebdomadaire";
            case "MENSUELLE" -> "mensuel";
            case "TRIMESTRIELLE" -> "trimestriel";
            case "SEMESTRIELLE" -> "semestriel";
            case "ANNUELLE" -> "annuel";
            default -> "périodique";
        };
    }

    private String equipmentCode(long eqId) {
        return Jdbc.queryOne("SELECT code FROM equipment WHERE id=?", rs -> rs.getString(1), eqId).orElse("—");
    }

    private String equipmentName(long eqId) {
        return Jdbc.queryOne("SELECT name FROM equipment WHERE id=?", rs -> rs.getString(1), eqId).orElse("Équipement");
    }

    private static boolean contains(long[] arr, long v) {
        for (long x : arr) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(int[] arr, int v) {
        for (int x : arr) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }
}
