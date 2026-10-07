package com.medmaint.service;

import com.medmaint.config.AppConfig;
import com.medmaint.domain.enums.DocumentCategory;
import com.medmaint.domain.model.Document;
import com.medmaint.persistence.repo.DocumentRepo;
import com.medmaint.security.Permission;
import com.medmaint.security.Session;
import com.medmaint.security.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gestion des documents : téléversement sécurisé (type + taille vérifiés),
 * nom de fichier aléatoire sur disque (aucun chemin exposé), téléchargement
 * et suppression selon droits.
 */
public final class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    /** Extensions autorisées → type MIME. */
    private static final Map<String, String> ALLOWED = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DocumentRepo repo = new DocumentRepo();
    private final AuditService audit;

    public DocumentService(AuditService audit) {
        this.audit = audit;
    }

    public List<Document> forEntity(String entityType, long entityId) {
        Session.get().require(Permission.DOCUMENT_VIEW);
        return repo.findByEntity(entityType, entityId);
    }

    public PageDocuments search(String text, String entityType, String category, int page, int size) {
        Session.get().require(Permission.DOCUMENT_VIEW);
        return new PageDocuments(repo.search(text, entityType, category, page, size));
    }

    /** Enregistre un fichier téléversé (déjà lu en mémoire par l'UI). */
    public Document store(String entityType, long entityId, String originalName,
                          String category, byte[] content) {
        Session.get().require(Permission.DOCUMENT_UPLOAD);
        if (originalName == null || originalName.isBlank()) {
            throw new ValidationException("Le nom de fichier est obligatoire.");
        }
        String ext = extension(originalName);
        if (!ALLOWED.containsKey(ext)) {
            throw new ValidationException("Type de fichier non autorisé : ." + ext
                    + ". Formats acceptés : PDF, JPG, PNG, DOCX, XLSX.");
        }
        long max = AppConfig.get().maxUploadBytes();
        if (content == null || content.length == 0) {
            throw new ValidationException("Le fichier est vide.");
        }
        if (content.length > max) {
            throw new ValidationException(String.format(
                    "Fichier trop volumineux (%.1f Mo). Taille maximale : %.0f Mo.",
                    content.length / (1024.0 * 1024.0), max / (1024.0 * 1024.0)));
        }
        String stored = UUID.randomUUID() + "." + ext;
        Path dir = AppConfig.get().uploadDir().resolve(entityType.toLowerCase(Locale.ROOT));
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(stored), content);
        } catch (IOException e) {
            log.error("Écriture fichier échouée", e);
            throw new ValidationException("Impossible d'enregistrer le fichier sur le disque.");
        }
        Document d = new Document();
        d.setEntityType(entityType);
        d.setEntityId(entityId);
        d.setFilename(sanitize(originalName));
        d.setStoredName(stored);
        d.setMimeType(ALLOWED.get(ext));
        d.setCategory(category != null && !category.isBlank() ? category : DocumentCategory.AUTRE.name());
        d.setSizeBytes(content.length);
        d.setUploadedBy(Session.get().userId());
        d.setDescription(null);
        long id = repo.insert(d);
        d.setId(id);
        audit.record("UPLOAD", "Document", String.valueOf(id), d.getFilename(), null, entityType + "#" + entityId);
        return d;
    }

    /** Résout le chemin disque d'un document (contrôle d'appartenance au dossier uploads). */
    public Path resolveFile(Document d) {
        Session.get().require(Permission.DOCUMENT_VIEW);
        String sub = d.getEntityType() != null ? d.getEntityType().toLowerCase(Locale.ROOT) : "misc";
        Path base = AppConfig.get().uploadDir().toAbsolutePath().normalize();
        Path p = base.resolve(sub).resolve(d.getStoredName()).normalize();
        if (!p.startsWith(base)) {
            throw new ValidationException("Accès au fichier refusé.");
        }
        return p;
    }

    public byte[] readFile(Document d) {
        Path p = resolveFile(d);
        if (!Files.exists(p)) {
            throw new ValidationException("Le fichier n'existe plus sur le disque.");
        }
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new ValidationException("Lecture du fichier impossible.");
        }
    }

    public void delete(long id) {
        Session.get().require(Permission.DOCUMENT_DELETE);
        Document d = repo.findById(id)
                .orElseThrow(() -> new ValidationException("Document introuvable."));
        Path p = resolveFileQuiet(d);
        try {
            if (p != null && Files.exists(p)) {
                Files.delete(p);
            }
        } catch (IOException e) {
            log.warn("Suppression fichier impossible : {}", e.getMessage());
        }
        repo.delete(id);
        audit.deleted("Document", String.valueOf(id), d.getFilename(), null);
    }

    private Path resolveFileQuiet(Document d) {
        try {
            String sub = d.getEntityType() != null ? d.getEntityType().toLowerCase(Locale.ROOT) : "misc";
            return AppConfig.get().uploadDir().toAbsolutePath().normalize()
                    .resolve(sub).resolve(d.getStoredName()).normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Copie un fichier local vers le dossier d'upload (utilisé par le seed de démo). */
    public Path importLocal(String entityType, long entityId, String filename,
                            String category, byte[] content) {
        String ext = extension(filename);
        if (!ALLOWED.containsKey(ext)) {
            return null;
        }
        String stored = UUID.randomUUID() + "." + ext;
        Path dir = AppConfig.get().uploadDir().resolve(entityType.toLowerCase(Locale.ROOT));
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(stored), content);
        } catch (IOException e) {
            return null;
        }
        Document d = new Document();
        d.setEntityType(entityType);
        d.setEntityId(entityId);
        d.setFilename(filename);
        d.setStoredName(stored);
        d.setMimeType(ALLOWED.get(ext));
        d.setCategory(category);
        d.setSizeBytes(content.length);
        repo.insert(d);
        return dir.resolve(stored);
    }

    public long countByEntity(String entityType, long entityId) {
        return repo.countByEntity(entityType, entityId);
    }

    private static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static String sanitize(String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_").trim();
        return clean.length() > 200 ? clean.substring(clean.length() - 200) : clean;
    }

    /** Wrapper fin pour exposer le total sans coupler l'UI au repo. */
    public record PageDocuments(com.medmaint.persistence.Page<Document> page) {
        public List<Document> items() { return page.items(); }
        public long total() { return page.total(); }
        public int totalPages() { return page.totalPages(); }
    }

    public static Set<String> allowedExtensions() {
        return ALLOWED.keySet();
    }
}
