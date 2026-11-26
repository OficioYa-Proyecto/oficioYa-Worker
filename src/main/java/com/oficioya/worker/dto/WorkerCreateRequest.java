package com.oficioya.worker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.HashSet;
import java.util.Set;

// El userId no viaja en el cuerpo: se toma del token
public record WorkerCreateRequest(
        boolean availableNow,
        Set<@NotNull @Positive Long> zoneIds,
        Set<@NotNull @Positive Long> localityIds,
        @NotNull @Valid WorkerTradeRequest primaryTrade) {

    public WorkerCreateRequest {
        zoneIds = zoneIds == null ? new HashSet<>() : zoneIds;
        localityIds = localityIds == null ? new HashSet<>() : localityIds;
    }
}
