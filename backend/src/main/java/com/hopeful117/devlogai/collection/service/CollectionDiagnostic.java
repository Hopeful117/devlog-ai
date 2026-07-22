package com.hopeful117.devlogai.collection.service;

import com.hopeful117.devlogai.collection.collector.CollectorType;

import java.util.UUID;

public record CollectionDiagnostic(
        UUID sourceId,
        CollectorType collectorType,
        String collectorVersion,
        String code,
        String message
) {
}
