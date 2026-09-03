package com.howl.uwtracker.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link ModuleType} to/from the {@code modules.type} string column (NOT NULL, defaults {@code plugin}). */
@Converter
public class ModuleTypeConverter implements AttributeConverter<ModuleType, String> {

    @Override
    public String convertToDatabaseColumn(ModuleType attribute) {
        return attribute == null ? null : attribute.wireValue();
    }

    @Override
    public ModuleType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ModuleType.fromWire(dbData);
    }
}
