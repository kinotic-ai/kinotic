package org.kinotic.structures.sql.domain.statements;

/**
 * Template part for settings.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public class SettingTemplatePart implements TemplatePart {
    private final String name;
    private final String value;

    public SettingTemplatePart(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }
} 