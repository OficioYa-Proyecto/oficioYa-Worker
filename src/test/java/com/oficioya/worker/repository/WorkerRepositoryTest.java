package com.oficioya.worker.repository;

import com.oficioya.worker.entity.Worker;
import com.oficioya.worker.model.WorkerStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class WorkerRepositoryTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID UNKNOWN_USER_ID = UUID.randomUUID();

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldFilterWorkersAndFetchAllTheirZones() {
        Worker matching = newWorker(USER_ID, true);
        matching.getZoneIds().addAll(List.of(5L, 8L));
        Worker saved = workerRepository.saveAndFlush(matching);
        workerRepository.save(newWorker(OTHER_USER_ID, false));
        entityManager.flush();
        entityManager.clear();

        List<Worker> result = workerRepository.search(WorkerStatus.ACTIVE, null, 5L, null, true, true, false);

        assertEquals(List.of(saved.getId()), result.stream().map(Worker::getId).toList());
        assertEquals(Set.of(5L, 8L), result.getFirst().getZoneIds());
        assertEquals(2, workerRepository.search(WorkerStatus.ACTIVE, null, null, null, null, false, false).size());
        assertTrue(workerRepository.search(WorkerStatus.ACTIVE, UUID.randomUUID(), null, null, null, false, true).isEmpty());
    }

    private Worker newWorker(UUID userId, boolean availableNow) {
        Worker worker = new Worker();
        worker.setUserId(userId);
        worker.setStatus(WorkerStatus.ACTIVE);
        worker.setAvailableNow(availableNow);
        return worker;
    }

    @Test
    @DisplayName("Debe guardar un worker y generar su id")
    void shouldSaveWorker() {
        Worker saved = workerRepository.save(newWorker(USER_ID, false));

        assertNotNull(saved.getId());
        assertEquals(USER_ID, saved.getUserId());
    }

    @Test
    @DisplayName("Debe buscar un worker por userId")
    void shouldFindByUserId() {
        workerRepository.save(newWorker(USER_ID, false));

        assertTrue(workerRepository.findByUserId(USER_ID).isPresent());
        assertTrue(workerRepository.findByUserId(UNKNOWN_USER_ID).isEmpty());
    }

    @Test
    @DisplayName("Debe listar solo los workers disponibles ahora")
    void shouldFindAvailableNow() {
        workerRepository.save(newWorker(USER_ID, true));
        workerRepository.save(newWorker(OTHER_USER_ID, false));

        List<Worker> result = workerRepository.findByAvailableNowTrue();

        assertEquals(1, result.size());
        assertEquals(USER_ID, result.get(0).getUserId());
    }

    @Test
    @DisplayName("Debe actualizar el estado de un worker")
    void shouldUpdateWorker() {
        Worker saved = workerRepository.save(newWorker(USER_ID, false));

        saved.setStatus(WorkerStatus.PAUSED);
        workerRepository.save(saved);

        assertEquals(WorkerStatus.PAUSED, workerRepository.findById(saved.getId()).get().getStatus());
    }

    @Test
    @DisplayName("Debe eliminar un worker")
    void shouldDeleteWorker() {
        Worker saved = workerRepository.save(newWorker(USER_ID, false));

        workerRepository.deleteById(saved.getId());

        assertTrue(workerRepository.findById(saved.getId()).isEmpty());
    }
}