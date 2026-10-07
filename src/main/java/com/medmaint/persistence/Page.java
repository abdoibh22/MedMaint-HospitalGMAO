package com.medmaint.persistence;

import java.util.Collections;
import java.util.List;

/**
 * Résultat paginé : page courante + nombre total d'enregistrements.
 * Les listes ne chargent jamais toute la table en mémoire.
 */
public final class Page<T> {

    private final List<T> items;
    private final long total;
    private final int page;
    private final int pageSize;

    public Page(List<T> items, long total, int page, int pageSize) {
        this.items = items != null ? items : Collections.emptyList();
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<T> items() {
        return items;
    }

    public long total() {
        return total;
    }

    public int page() {
        return page;
    }

    public int pageSize() {
        return pageSize;
    }

    public int totalPages() {
        return pageSize <= 0 ? 1 : (int) Math.max(1, (total + pageSize - 1) / pageSize);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public static <T> Page<T> empty(int pageSize) {
        return new Page<>(Collections.emptyList(), 0, 1, pageSize);
    }
}
