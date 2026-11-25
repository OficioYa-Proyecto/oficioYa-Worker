package com.oficioya.worker.repository;

import com.oficioya.worker.entity.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, UUID> {

    List<AvailabilitySlot> findByWorkerId(UUID workerId);

    @Query("select related from AvailabilitySlot related where related.worker.id in :workerIds")
    List<AvailabilitySlot> findByWorkerIdIn(Collection<UUID> workerIds);
}