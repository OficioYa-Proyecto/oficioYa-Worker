package com.oficioya.worker.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// Catálogo de ubicaciones del dominio Users (a través del Orquestador)
public interface LocationCatalogClient {

    // Barrios con el nombre de su localidad; lanza ExternalServiceUnavailableException si Users no responde
    List<Zone> findZones();

    // Localidades; lanza ExternalServiceUnavailableException si Users no responde
    List<Locality> findLocalities();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Zone(Long id, String name, String locality) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Locality(Long id, String name) {
    }
}