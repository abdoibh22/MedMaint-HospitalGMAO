package com.medmaint.ui.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Accès aux chaînes traduites (ResourceBundle).
 * Base française (messages.properties) ; messages_en.properties permet
 * d'ajouter l'anglais sans toucher au code (clé absente → repli sur le français).
 */
public final class Messages {

    private static final String BASE = "i18n/messages";
    private static Locale locale = Locale.FRENCH;

    private Messages() {
    }

    public static void setLocale(Locale l) {
        locale = l != null ? l : Locale.FRENCH;
    }

    public static Locale locale() {
        return locale;
    }

    public static String get(String key) {
        return resolve(key, key);
    }

    public static String get(String key, Object... args) {
        String pattern = resolve(key, key);
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }

    private static String resolve(String key, String fallback) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BASE, locale);
            if (bundle.containsKey(key)) {
                return bundle.getString(key);
            }
        } catch (MissingResourceException ignored) {
            // repli ci-dessous
        }
        return fallback;
    }

    /** Vrai si la clé existe (utilisé par les tests de couverture i18n). */
    public static boolean has(String key) {
        try {
            return ResourceBundle.getBundle(BASE, locale).containsKey(key);
        } catch (MissingResourceException e) {
            return false;
        }
    }
}
