package com.ybc.ybc.applications.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class DiscoverySourceConverter implements AttributeConverter<DiscoverySource, String> {
    @Override
    public String convertToDatabaseColumn(DiscoverySource discoverySource) {
        return discoverySource == null ? null : discoverySource.name();
    }

    @Override
    public DiscoverySource convertToEntityAttribute(String value) {
        return DiscoverySource.fromDatabaseValue(value);
    }
}
