package com.minor_project.optiserve_backend.operations.application;

import com.minor_project.optiserve_backend.operations.api.dto.QuickIntakeResponse;
import com.minor_project.optiserve_backend.operations.api.dto.QuickVehicleIntakeRequest;
import com.minor_project.optiserve_backend.operations.domain.Customer;
import com.minor_project.optiserve_backend.operations.domain.QueueEntry;
import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.persistence.CustomerRepository;
import com.minor_project.optiserve_backend.operations.persistence.QueueEntryRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceRequestRepository;
import com.minor_project.optiserve_backend.operations.persistence.ServiceTypeRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuickIntakeApplicationService {

    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final QueueEntryRepository queueEntryRepository;

    public QuickIntakeApplicationService(
            CustomerRepository customerRepository,
            VehicleRepository vehicleRepository,
            ServiceTypeRepository serviceTypeRepository,
            ServiceRequestRepository serviceRequestRepository,
            QueueEntryRepository queueEntryRepository) {
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.queueEntryRepository = queueEntryRepository;
    }

    @Transactional
    public QuickIntakeResponse processQuickIntake(QuickVehicleIntakeRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        // 1. Locate existing customer by phone, or create customer profile
        Customer customer = customerRepository.findByPhone(request.customerPhone().trim())
                .orElseGet(() -> customerRepository.save(
                        Customer.createRegistered(
                                request.customerName().trim(),
                                request.customerEmail(),
                                "auto-guest-temp-pass",
                                request.customerPhone().trim(),
                                null
                        )
                ));

        // 2. Locate existing vehicle by registration number, or create new vehicle record
        String regNumber = request.registrationNumber().trim().toUpperCase();
        Vehicle vehicle = vehicleRepository.findByRegistrationNumber(regNumber)
                .map(existing -> {
                    if (request.currentMileage() != null && request.currentMileage() > existing.getCurrentMileage()) {
                        existing.updateMileage(request.currentMileage());
                    }
                    if (existing.getCustomer() == null) {
                        existing.assignOwner(customer);
                    }
                    return existing;
                })
                .orElseGet(() -> vehicleRepository.save(
                        Vehicle.create(
                                customer,
                                regNumber,
                                request.make(),
                                request.model(),
                                request.manufacturingYear(),
                                request.fuelType(),
                                request.color(),
                                request.vin(),
                                request.currentMileage()
                        )
                ));

        // 3. Retrieve service type
        ServiceType serviceType = serviceTypeRepository.findById(request.serviceTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Service type not found with ID: " + request.serviceTypeId()));

        // 4. Create Service Request and transition to WAITING
        ServiceRequest serviceRequest = ServiceRequest.create(
                vehicle,
                serviceType,
                request.resolvePriority(),
                null
        );
        serviceRequest.enqueue();
        ServiceRequest savedRequest = serviceRequestRepository.save(serviceRequest);

        // 5. Enter into Queue
        Instant queuedAt = Instant.now();
        QueueEntry queueEntry = QueueEntry.enter(savedRequest, queuedAt);
        QueueEntry savedQueueEntry = queueEntryRepository.save(queueEntry);

        return new QuickIntakeResponse(
                savedRequest.getId(),
                savedQueueEntry.getId(),
                vehicle.getId(),
                customer.getId(),
                vehicle.getRegistrationNumber(),
                customer.getName(),
                serviceType.getName(),
                savedRequest.getPriorityClass(),
                savedRequest.getStatus(),
                savedQueueEntry.getStatus(),
                queuedAt
        );
    }
}
