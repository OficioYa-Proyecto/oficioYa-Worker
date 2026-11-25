package com.oficioya.worker.repository;

import com.oficioya.worker.entity.WorkPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkPhotoRepository extends JpaRepository<WorkPhoto, UUID> {

    List<WorkPhoto> findByWorkerId(UUID workerId);

    @Query("select related from WorkPhoto related where related.worker.id in :workerIds")
    List<WorkPhoto> findByWorkerIdIn(Collection<UUID> workerIds);
}