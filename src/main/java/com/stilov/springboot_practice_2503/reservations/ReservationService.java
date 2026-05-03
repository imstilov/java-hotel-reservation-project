package com.stilov.springboot_practice_2503.reservations;

import com.stilov.springboot_practice_2503.entities.ReservationEntity;
import com.stilov.springboot_practice_2503.reservations.availability.ReservationAvailabilityService;
import com.stilov.springboot_practice_2503.reservations.stats.RequestCounterService;
import com.stilov.springboot_practice_2503.reservations.stats.ReservationStats;
import com.stilov.springboot_practice_2503.search_filters.GroupByUserAndStatus;
import com.stilov.springboot_practice_2503.search_filters.ReservationSearchFilter;
import com.stilov.springboot_practice_2503.users.UserRepository;
import com.stilov.springboot_practice_2503.web.exceptions.InvalidReservationStatusException;
import com.stilov.springboot_practice_2503.web.exceptions.ReservationNotFoundException;
import com.stilov.springboot_practice_2503.web.exceptions.RoomNotAvailableException;
import com.stilov.springboot_practice_2503.web.exceptions.UserIdNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class ReservationService {
    private final ReservationAvailabilityService reservationAvailabilityService;

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository repository;

    private final ReservationMapper mapper;

    private final RequestCounterService requestCounterService;
    private final UserRepository userRepository;

    public ReservationService(ReservationRepository repository,
                              ReservationMapper mapper,
                              ReservationAvailabilityService reservationAvailabilityService,
                              RequestCounterService requestCounterService, UserRepository userRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.reservationAvailabilityService = reservationAvailabilityService;
        this.requestCounterService = requestCounterService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ReservationDTO> searchAllByFilter(
            ReservationSearchFilter filter
    ) {
    int pageSize = filter.pageSize() != null
            ? filter.pageSize() : 10;
    int pageNumber = filter.pageNumber() != null
            ? filter.pageNumber() : 0;
    var pageable = Pageable
                .ofSize(pageSize)
                .withPage(pageNumber);
        List<ReservationEntity> allEntities = repository.searchAllByFilter(filter.roomId(), filter.userId(), pageable);
        requestCounterService.increment();
        return allEntities.stream()
                .map(mapper::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationDTO> groupByStatusAndUserId(
            GroupByUserAndStatus filter) {
        int pageSize = filter.pageSize() != null
                ? filter.pageSize()
                : 10;
        int pageNumber = filter.pageNumber() != null
                ? filter.pageNumber()
                : 0;
        var pageable = Pageable
                .ofSize(pageSize)
                .withPage(pageNumber);
        List<ReservationEntity> allEntities = repository.groupByUserIdAndStatus(filter.userId(), filter.status(), pageable);
        requestCounterService.increment();
        return allEntities.stream()
                .map(mapper::toDomain).toList();
    };

    @Transactional(readOnly = true)
    public List<Long> getMostPopularRooms(){
      requestCounterService.increment();
      return repository.findTop3PopularRooms();
    };

    @Transactional(readOnly = true)
    public ReservationDTO getReservationById(Long id) {
        ReservationEntity reservationEntity = repository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found by id: " + id));

        return mapper.toDomain(reservationEntity);
    };

    public ReservationStats getCountStats(){
        var startDate = LocalDate.now().withDayOfMonth(1);
        var countByPeriod = repository.countByPeriod(startDate);
        var getRequestsCount = requestCounterService.getCount();
        requestCounterService.increment();
        return new ReservationStats(getRequestsCount, countByPeriod);
    }


    public ReservationDTO createReservation(ReservationDTO reservationDTOToCreate) {
        if(reservationDTOToCreate.status() != null){
            throw new IllegalArgumentException("Reservation status must be null");
        }
        if(!reservationDTOToCreate.endDate().isAfter(reservationDTOToCreate.startDate())){
            throw new IllegalArgumentException("Reservation end date must be after start date");
        }
        if(!userRepository.existsById(reservationDTOToCreate.userId())){
            throw new UserIdNotFoundException("User id doesnt exist.");
        }
        if(!reservationAvailabilityService.isReservationAvailable(  reservationDTOToCreate.roomId(),
                                                                    reservationDTOToCreate.startDate(),
                                                                    reservationDTOToCreate.endDate()
                                                                    )){
            throw new RoomNotAvailableException(reservationDTOToCreate.roomId());
        }
        var entityToSave = mapper.toEntity(reservationDTOToCreate);
        entityToSave.setStatus(ReservationStatus.PENDING);
        var savedEntity = repository.save(entityToSave);
        requestCounterService.increment();
        return mapper.toDomain(savedEntity);
    }


    public ReservationDTO updateReservation(
            Long id,
            ReservationDTO reservationDTOToUpdate)
    {
        var reservationEntity = repository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found by id: " + id));

        if(reservationEntity.getStatus() != ReservationStatus.PENDING){
            throw new IllegalStateException("Reservation status cannot be modified, status is=" + reservationEntity.getStatus());
        }
        if(!reservationDTOToUpdate.endDate().isAfter(reservationDTOToUpdate.startDate())){
            throw new IllegalArgumentException("Reservation end date must be after start date");
        }

        var reservationToSave = mapper.toEntity(reservationDTOToUpdate);
        reservationToSave.setId(reservationEntity.getId());
        reservationToSave.setStatus(ReservationStatus.PENDING);

        var updatedReservation = repository.save(reservationToSave);
        requestCounterService.increment();
        return mapper.toDomain(updatedReservation);

    }

    @Transactional
    public void cancelReservation(Long id) {
        var reservation = repository.findById(id)
                        .orElseThrow(() -> new ReservationNotFoundException("Reservation not found by id: " + id));
        if(reservation.getStatus().equals(ReservationStatus.APPROVED)){
            throw new InvalidReservationStatusException("Reservation status cannot be cancelled, status is approved");
        }
        if(reservation.getStatus().equals(ReservationStatus.CANCELED)){
            throw new InvalidReservationStatusException("Reservation status cannot be cancelled, status is already cancelled");
        }
        repository.setStatus(id, ReservationStatus.CANCELED);
        requestCounterService.increment();
        log.info("Reservation has been cancelled by user: id={}", id);
    }


    @Retryable(
            value = CannotSerializeTransactionException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ReservationDTO approveReservation(Long id) {
        var reservationEntity = repository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found by id: " + id));

        if(reservationEntity.getStatus() != ReservationStatus.PENDING){
            throw new InvalidReservationStatusException("Cannot approve reservation: status=" + reservationEntity.getStatus());
        }
        var isAvailable = reservationAvailabilityService.isReservationAvailable(
                reservationEntity.getRoomId(),
                reservationEntity.getStartDate(),
                reservationEntity.getEndDate()
        );
        if(!isAvailable){
            throw new InvalidReservationStatusException("Cannot approve reservation: isAvailable");
        }

        reservationEntity.setStatus(ReservationStatus.APPROVED);
        repository.save(reservationEntity);
        requestCounterService.increment();
        ReservationDTO result = mapper.toDomain(reservationEntity);
        return result;
    }
}
