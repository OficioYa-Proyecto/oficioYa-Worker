package com.oficioya.worker.repository;

import com.oficioya.worker.entity.WorkerTrade;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerTradeRepository extends JpaRepository<WorkerTrade, UUID> {

    List<WorkerTrade> findByWorkerId(UUID workerId);

    @Query("select related from WorkerTrade related join fetch related.trade trade join fetch trade.category where related.worker.id in :workerIds")
    List<WorkerTrade> findByWorkerIdIn(Collection<UUID> workerIds);

    Optional<WorkerTrade> findByWorkerIdAndPrimaryTrue(UUID workerId);
}