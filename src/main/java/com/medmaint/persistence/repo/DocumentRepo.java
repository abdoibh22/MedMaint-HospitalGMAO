package com.medmaint.persistence.repo;

import com.medmaint.domain.model.Document;
import com.medmaint.persistence.Jdbc;
import com.medmaint.persistence.Page;
import com.medmaint.persistence.SqlWhere;

import java.util.List;
import java.util.Optional;

/** Documents joints (métadonnées) + accès fichier sécurisé. */
public final class DocumentRepo {

    private static final Jdbc.RowMapper<Document> MAP = rs -> {
        Document d = new Document();
        d.setId(rs.getLong("id"));
        d.setEntityType(rs.getString("entity_type"));
        d.setEntityId(rs.getLong("entity_id"));
        d.setFilename(rs.getString("filename"));
        d.setStoredName(rs.getString("stored_name"));
        d.setMimeType(rs.getString("mime_type"));
        d.setCategory(rs.getString("category"));
        d.setSizeBytes(rs.getLong("size_bytes"));
        d.setUploadedBy(Jdbc.getLongOrNull(rs, "uploaded_by"));
        d.setUploadedByName(rs.getString("uploaded_by_name"));
        d.setUploadedAt(Jdbc.getDateTime(rs, "uploaded_at"));
        d.setDescription(rs.getString("description"));
        return d;
    };

    private static final String SELECT = """
            SELECT d.*, u.full_name AS uploaded_by_name
            FROM documents d LEFT JOIN users u ON u.id = d.uploaded_by
            """;

    public List<Document> findByEntity(String entityType, long entityId) {
        return Jdbc.query(SELECT + " WHERE d.entity_type=? AND d.entity_id=? ORDER BY d.uploaded_at DESC",
                MAP, entityType, entityId);
    }

    public Page<Document> search(String text, String entityType, String category,
                                 int page, int pageSize) {
        SqlWhere w = new SqlWhere("1=1");
        w.addLike("d.filename", text)
                .add("d.entity_type = ?", entityType)
                .add("d.category = ?", category);
        long total = Jdbc.count(
                "SELECT COUNT(*) FROM documents d LEFT JOIN users u ON u.id=d.uploaded_by WHERE " + w.clause(),
                w.params());
        int offset = Math.max(0, (page - 1) * pageSize);
        List<Document> items = Jdbc.query(
                SELECT + " WHERE " + w.clause() + " ORDER BY d.uploaded_at DESC LIMIT ? OFFSET ?",
                MAP, concat(w.params(), pageSize, offset));
        return new Page<>(items, total, page, pageSize);
    }

    public Optional<Document> findById(long id) {
        return Jdbc.queryOne(SELECT + " WHERE d.id=?", MAP, id);
    }

    public long insert(Document d) {
        return Jdbc.insert("""
                INSERT INTO documents(entity_type, entity_id, filename, stored_name, mime_type, category,
                    size_bytes, uploaded_by, uploaded_at, description)
                VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)""",
                d.getEntityType(), d.getEntityId(), d.getFilename(), d.getStoredName(),
                d.getMimeType(), d.getCategory(), d.getSizeBytes(), d.getUploadedBy(), d.getDescription());
    }

    public void delete(long id) {
        Jdbc.update("DELETE FROM documents WHERE id=?", id);
    }

    public long countByEntity(String entityType, long entityId) {
        return Jdbc.count("SELECT COUNT(*) FROM documents WHERE entity_type=? AND entity_id=?",
                entityType, entityId);
    }

    private static Object[] concat(Object[] base, Object... extra) {
        Object[] out = new Object[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }
}
