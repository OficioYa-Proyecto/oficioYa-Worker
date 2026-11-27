package com.oficioya.worker.repository;

import com.oficioya.worker.entity.Worker;
import com.oficioya.worker.model.WorkerStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

public interface WorkerRepository extends JpaRepository<Worker, UUID> {

    Optional<Worker> findByUserId(UUID userId);

    List<Worker> findByAvailableNowTrue();

    @Query("""
            select distinct w from Worker w
            left join fetch w.zoneIds
            where w.status = :status
              and w.availableNow = coalesce(:availableNow, w.availableNow)
              and (:filterZone = false
                   or :zoneId member of w.zoneIds
                   or :zoneLocalityId member of w.localityIds)
              and (:filterLocality = false
                   or :localityId member of w.localityIds
                   or exists (select 1 from Worker w2 join w2.zoneIds z
                              where w2 = w and z in :localityZoneIds))
              and (:filterTrade = false or exists (
                  select 1 from WorkerTrade wt
                  where wt.worker = w
                    and wt.trade.id = coalesce(:tradeId, wt.trade.id)
                    and wt.approxRate <= coalesce(:maxRate, wt.approxRate)
              ))
            """)
    List<Worker> search(WorkerStatus status, UUID tradeId, Long zoneId, Long zoneLocalityId,
                        Long localityId, Set<Long> localityZoneIds, BigDecimal maxRate,
                        Boolean availableNow, boolean filterZone, boolean filterLocality,
                        boolean filterTrade);
}