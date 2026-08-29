package com.howl.uwtracker.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Maps {@link RoleModel} to/from the {@code map_configs.role_model} string column (nullable). */
@Converter
public class RoleModelConverter implements AttributeConverter<RoleModel, String> {

    @Override
    public String convertToDatabaseColumn(RoleModel attribute) {
        return attribute == null ? null : attribute.wireValue();
    }

    @Override
    public RoleModel convertToEntityAttribute(String dbData) {
        return RoleModel.fromWire(dbData);
    }
}
