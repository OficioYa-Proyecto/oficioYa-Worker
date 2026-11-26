package com.oficioya.worker.service;

import com.oficioya.worker.entity.*;
import com.oficioya.worker.exception.*;
import com.oficioya.worker.mapper.*;
import com.oficioya.worker.model.*;
import com.oficioya.worker.repository.*;
import com.oficioya.worker.integration.LocationCatalog;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.*;
import java.math.BigDecimal;

import static java.util.stream.Collectors.groupingBy;

@Service
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final WorkerRepository workerRepository;
    private final WorkerTradeRepository workerTradeRepository;
    private final AvailabilitySlotRepository slotRepository;
    private final WorkPhotoRepository photoRepository;
    private final TradeRepository tradeRepository;
    private final WorkerMapper workerMapper;
    private final WorkerTradeMapper workerTradeMapper;
    private final AvailabilitySlotMapper slotMapper;
    private final WorkPhotoMapper photoMapper;
    private final LocationCatalog locationCatalog;

    public WorkerService(WorkerRepository workerRepository,
                         WorkerTradeRepository workerTradeRepository,
                         AvailabilitySlotRepository slotRepository,
                         WorkPhotoRepository photoRepository,
                         TradeRepository tradeRepository,
                         WorkerMapper workerMapper,
                         WorkerTradeMapper workerTradeMapper,
                         AvailabilitySlotMapper slotMapper,
                         WorkPhotoMapper photoMapper,
                         LocationCatalog locationCatalog) {
        this.workerRepository = workerRepository;
        this.workerTradeRepository = workerTradeRepository;
        this.slotRepository = slotRepository;
        this.photoRepository = photoRepository;
        this.tradeRepository = tradeRepository;
        this.workerMapper = workerMapper;
        this.workerTradeMapper = workerTradeMapper;
        this.slotMapper = slotMapper;
        this.photoMapper = photoMapper;
        this.locationCatalog = locationCatalog;
    }

    @Transactional(readOnly = true)
    public List<WorkerModel> search(UUID tradeId, Long zoneId, BigDecimal maxRate, Boolean availableNow) {
        log.info("Worker search: tradeId={}, zoneId={}, maxRate={}, availableNow={}",
                tradeId, zoneId, maxRate, availableNow);
        if (maxRate != null && maxRate.signum() < 0) {
            throw new IllegalArgumentException("The maximum rate cannot be negative");
        }
        List<Worker> workers = workerRepository.search(WorkerStatus.ACTIVE, tradeId, zoneId, maxRate, availableNow,
                zoneId != null, tradeId != null || maxRate != null);
        if (workers.isEmpty()) {
            return List.of();
        }

        List<UUID> workerIds = workers.stream().map(Worker::getId).toList();
        var tradesByWorker = workerTradeRepository.findByWorkerIdIn(workerIds).stream()
                .collect(groupingBy(trade -> trade.getWorker().getId()));
        var slotsByWorker = slotRepository.findByWorkerIdIn(workerIds).stream()
                .collect(groupingBy(slot -> slot.getWorker().getId()));
        var photosByWorker = photoRepository.findByWorkerIdIn(workerIds).stream()
                .collect(groupingBy(photo -> photo.getWorker().getId()));

        List<WorkerModel> result = workers.stream()
                .map(worker -> buildModel(worker,
                        tradesByWorker.getOrDefault(worker.getId(), List.of()),
                        slotsByWorker.getOrDefault(worker.getId(), List.of()),
                        photosByWorker.getOrDefault(worker.getId(), List.of())))
                .toList();
        log.info("Worker search returned {} workers", result.size());
        return result;
    }

    public WorkerModel findById(UUID id) {
        log.debug("Looking for worker with id: {}", id);
        return buildModel(getWorkerOrThrow(id));
    }

    // Perfil público: un perfil pausado solo lo ven su dueño y los roles privilegiados (ADMIN, SERVICE).
    // Para los demás responde igual que si no existiera, así no se revela que el perfil está pausado.
    public WorkerModel findVisibleById(UUID id, UUID requesterId, boolean privileged) {
        Worker worker = getWorkerOrThrow(id);
        boolean owner = requesterId != null && requesterId.equals(worker.getUserId());
        if (worker.getStatus() == WorkerStatus.PAUSED && !owner && !privileged) {
            log.debug("Worker {} is paused and hidden from requester {}", id, requesterId);
            throw new ResourceNotFoundException("Worker not found: " + id);
        }
        return buildModel(worker);
    }

    public WorkerModel findByUserId(UUID userId) {
        log.debug("Looking for worker profile of user: {}", userId);
        return buildModel(workerRepository.findByUserId(userId).orElseThrow(() -> {
            log.warn("Worker profile not found for user: {}", userId);
            return new ResourceNotFoundException("Worker profile not found for user: " + userId);
        }));
    }

    @Transactional
    public WorkerModel create(WorkerModel model) {
        if (workerRepository.findByUserId(model.getUserId()).isPresent()) {
            log.warn("User {} already has a worker profile", model.getUserId());
            throw new BusinessRuleException("The user already has a worker profile: " + model.getUserId());
        }
        Trade trade = getTradeOrThrow(model.getPrimaryTrade().getTrade().getId());
        if (!trade.isActive()) {
            throw new BusinessRuleException("Trade is not active: " + trade.getId());
        }

        locationCatalog.requireZones(model.getZoneIds());
        Worker worker = workerMapper.toEntity(model);
        worker.setId(null);
        worker.setStatus(WorkerStatus.ACTIVE);          // siempre nace activo
        Worker savedWorker = workerRepository.save(worker);

        WorkerTrade primary = workerTradeMapper.toEntity(model.getPrimaryTrade());
        primary.setId(null);
        primary.setWorker(savedWorker);
        primary.setTrade(trade);
        primary.setPrimary(true);
        workerTradeRepository.save(primary);

        log.info("Worker created with id: {}", savedWorker.getId());
        return buildModel(savedWorker);
    }

    @Transactional
    public WorkerModel setAvailableNow(UUID id, boolean availableNow) {
        Worker worker = getWorkerOrThrow(id);
        if (worker.getStatus() == WorkerStatus.PAUSED) {
            log.warn("Worker {} is paused and cannot be modified", id);
            throw new BusinessRuleException("The worker is paused; it cannot be modified");
        }
        worker.setAvailableNow(availableNow);
        log.info("Worker {} availableNow set to {}", id, availableNow);
        return buildModel(workerRepository.save(worker));
    }

    @Transactional
    public WorkerModel changeStatus(UUID id, WorkerStatus status) {
        Worker worker = getWorkerOrThrow(id);
        worker.setStatus(status);
        if (status == WorkerStatus.PAUSED) {
            worker.setAvailableNow(false);
        }
        log.info("Worker {} status changed to {}", id, status);
        return buildModel(workerRepository.save(worker));
    }

    // Arma el modelo completo: el mapper copia los campos planos y aquí se cargan las listas
    private WorkerModel buildModel(Worker worker) {
        UUID workerId = worker.getId();
        return buildModel(worker, workerTradeRepository.findByWorkerId(workerId),
                slotRepository.findByWorkerId(workerId), photoRepository.findByWorkerId(workerId));
    }

    private WorkerModel buildModel(Worker worker, List<WorkerTrade> trades,
                                   List<AvailabilitySlot> slots, List<WorkPhoto> photos) {
        WorkerModel model = workerMapper.toModel(worker);

        for (WorkerTrade workerTrade : trades) {
            WorkerTradeModel tradeModel = workerTradeMapper.toModel(workerTrade);
            if (workerTrade.isPrimary()) {
                model.setPrimaryTrade(tradeModel);
            } else {
                model.addSecondaryTrade(tradeModel);
            }
        }
        model.setSlots(slotMapper.toModelList(slots));
        model.setPhotos(photoMapper.toModelList(photos));
        return model;
    }

    private Worker getWorkerOrThrow(UUID id) {
        return workerRepository.findById(id).orElseThrow(() -> {
            log.warn("Worker not found: {}", id);
            return new ResourceNotFoundException("Worker not found: " + id);
        });
    }

    private Trade getTradeOrThrow(UUID id) {
        return tradeRepository.findById(id).orElseThrow(() -> {
            log.warn("Trade not found: {}", id);
            return new ResourceNotFoundException("Trade not found: " + id);
        });
    }

    public Set<Long> findZones(UUID workerId) {
        return new HashSet<>(getWorkerOrThrow(workerId).getZoneIds());
    }

    @Transactional
    public void addZone(UUID workerId, Long zoneId) {
        Worker worker = getActiveWorkerOrThrow(workerId);
        locationCatalog.requireZones(Set.of(zoneId));
        worker.getZoneIds().add(zoneId);
        workerRepository.save(worker);
        log.info("Zone {} added to worker {}", zoneId, workerId);
    }

    @Transactional
    public void removeZone(UUID workerId, Long zoneId) {
        Worker worker = getActiveWorkerOrThrow(workerId);
        if (!worker.getZoneIds().remove(zoneId)) {
            log.warn("Worker {} does not cover zone {}", workerId, zoneId);
            throw new ResourceNotFoundException("The worker does not cover zone: " + zoneId);
        }
        workerRepository.save(worker);
        log.info("Zone {} removed from worker {}", zoneId, workerId);
    }

    public Set<Long> findLocalities(UUID workerId) {
        return new HashSet<>(getWorkerOrThrow(workerId).getLocalityIds());
    }

    @Transactional
    public void addLocality(UUID workerId, Long localityId) {
        Worker worker = getActiveWorkerOrThrow(workerId);
        locationCatalog.requireLocality(localityId);
        worker.getLocalityIds().add(localityId);
        workerRepository.save(worker);
        log.info("Locality {} added to worker {}", localityId, workerId);
    }

    @Transactional
    public void removeLocality(UUID workerId, Long localityId) {
        Worker worker = getActiveWorkerOrThrow(workerId);
        if (!worker.getLocalityIds().remove(localityId)) {
            log.warn("Worker {} does not cover locality {}", workerId, localityId);
            throw new ResourceNotFoundException("The worker does not cover locality: " + localityId);
        }
        workerRepository.save(worker);
        log.info("Locality {} removed from worker {}", localityId, workerId);
    }

    private Worker getActiveWorkerOrThrow(UUID id) {
        Worker worker = getWorkerOrThrow(id);
        if (worker.getStatus() == WorkerStatus.PAUSED) {
            log.warn("Worker {} is paused and cannot be modified", id);
            throw new BusinessRuleException("The worker is paused and cannot be modified: " + id);
        }
        return worker;
    }
}