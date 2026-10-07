package com.medmaint.ui.components;

import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Convertisseur jj/mm/aaaa pour les DatePicker (saisie française). */
public final class FrDateConverter extends StringConverter<LocalDate> {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public String toString(LocalDate date) {
        return date == null ? "" : FMT.format(date);
    }

    @Override
    public LocalDate fromString(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(s.trim(), FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
