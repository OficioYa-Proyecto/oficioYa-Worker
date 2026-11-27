package com.oficioya.worker.entity;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import com.oficioya.worker.model.WorkerStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "workers")
@Getter
@Setter
@NoArgsConstructor
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WorkerStatus status;

    @Column(name = "available_now", nullable = false)
    private boolean availableNow;

    // Ids de zonas (otro dominio): solo se guardan los ids
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "worker_coverage", joinColumns = @JoinColumn(name = "worker_id"))
    @Column(name = "zone_id", nullable = false)
    private Set<Long> zoneIds = new HashSet<>();

    // Ids de localidades (otro dominio): cubre todos los barrios de cada una. SUBSELECT evita una consulta por trabajador en las búsquedas
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "worker_localities", joinColumns = @JoinColumn(name = "worker_id"))
    @Column(name = "locality_id", nullable = false)
    private Set<Long> localityIds = new HashSet<>();
}