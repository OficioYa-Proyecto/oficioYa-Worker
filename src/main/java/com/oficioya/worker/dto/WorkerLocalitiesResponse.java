package com.oficioya.worker.dto;

import java.util.Set;
import java.util.UUID;

// Localidades de cobertura (ids de localidades del dominio de ubicaciones)
public record WorkerLocalitiesResponse(UUID workerId, Set<Long> localityIds) {
}