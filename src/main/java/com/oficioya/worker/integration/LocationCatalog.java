package com.oficioya.worker.integration;

import com.oficioya.worker.exception.ExternalServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

// Copia en memoria del catálogo de Users: barrios, localidades y a qué localidad pertenece cada barrio.
// Se carga en la primera consulta y se refresca en segundo plano cuando vence (así buscar no espera a Users);
// si el refresco falla se siguen usando los últimos datos.
@Component
public class LocationCatalog {

    private static final Logger log = LoggerFactory.getLogger(LocationCatalog.class);

    private final LocationCatalogClient client;
    private final Duration ttl;
    private final Executor executor;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile Snapshot snapshot;

    @Autowired
    public LocationCatalog(LocationCatalogClient client,
                           @Value("${integration.catalog.ttl-minutes:10}") long ttlMinutes) {
        this(client, Duration.ofMinutes(ttlMinutes), ForkJoinPool.commonPool());
    }

    LocationCatalog(LocationCatalogClient client, Duration ttl, Executor executor) {
        this.client = client;
        this.ttl = ttl;
        this.executor = executor;
    }

    // Localidad del barrio, si se conoce. Si Users no responde queda vacío: la búsqueda sigue con los barrios guardados.
    public Optional<Long> localityOfZone(Long zoneId) {
        return available().map(data -> data.localityByZone().get(zoneId));
    }

    // Barrios de una localidad (vacío si no se conoce o Users no responde)
    public Set<Long> zonesOfLocality(Long localityId) {
        return available().map(data -> data.zonesByLocality().getOrDefault(localityId, Set.of())).orElse(Set.of());
    }

    // Validan ids: IllegalArgumentException si no existen; ExternalServiceUnavailableException si Users no responde
    public void requireZones(Collection<Long> zoneIds) {
        if (zoneIds.isEmpty()) {
            return;
        }
        Set<Long> known = current().zoneIds();
        List<Long> missing = zoneIds.stream().filter(id -> !known.contains(id)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Unknown zones: " + missing);
        }
    }

    public void requireLocality(Long localityId) {
        if (!current().localityIds().contains(localityId)) {
            throw new IllegalArgumentException("Unknown locality: " + localityId);
        }
    }

    private Optional<Snapshot> available() {
        try {
            return Optional.of(current());
        } catch (ExternalServiceUnavailableException ex) {
            log.warn("Location catalog unavailable: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Snapshot current() {
        Snapshot data = snapshot;
        if (data == null) {
            return load();
        }
        if (isExpired(data) && refreshing.compareAndSet(false, true)) {
            executor.execute(this::refreshQuietly);
        }
        return data;
    }

    private boolean isExpired(Snapshot data) {
        return !Instant.now().isBefore(data.loadedAt().plus(ttl));
    }

    private void refreshQuietly() {
        try {
            load();
        } catch (RuntimeException ex) {
            log.warn("Location catalog refresh failed; keeping the previous data: {}", ex.getMessage());
        } finally {
            refreshing.set(false);
        }
    }

    private synchronized Snapshot load() {
        Snapshot data = snapshot;
        if (data != null && !isExpired(data)) {
            return data;                        // otro hilo ya la cargó
        }
        snapshot = build(client.findZones(), client.findLocalities());
        log.info("Location catalog loaded: {} zones, {} localities", snapshot.zoneIds().size(), snapshot.localityIds().size());
        return snapshot;
    }

    // Empareja barrio y localidad por el nombre de la localidad (sin tildes, mayúsculas ni espacios de más)
    private static Snapshot build(List<LocationCatalogClient.Zone> zones, List<LocationCatalogClient.Locality> localities) {
        Map<String, Long> localityByName = new HashMap<>();
        Set<Long> localityIds = new HashSet<>();
        for (LocationCatalogClient.Locality locality : localities) {
            localityIds.add(locality.id());
            localityByName.put(normalize(locality.name()), locality.id());
        }
        Set<Long> zoneIds = new HashSet<>();
        Map<Long, Long> localityByZone = new HashMap<>();
        Map<Long, Set<Long>> zonesByLocality = new HashMap<>();
        for (LocationCatalogClient.Zone zone : zones) {
            zoneIds.add(zone.id());
            Long localityId = localityByName.get(normalize(zone.locality()));
            if (localityId != null) {
                localityByZone.put(zone.id(), localityId);
                zonesByLocality.computeIfAbsent(localityId, key -> new HashSet<>()).add(zone.id());
            }
        }
        return new Snapshot(zoneIds, localityIds, localityByZone, zonesByLocality, Instant.now());
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private record Snapshot(Set<Long> zoneIds, Set<Long> localityIds,
                            Map<Long, Long> localityByZone, Map<Long, Set<Long>> zonesByLocality,
                            Instant loadedAt) {
    }
}