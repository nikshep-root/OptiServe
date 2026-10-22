package com.minor_project.optiserve_backend.operations.vehicle.application;

import com.minor_project.optiserve_backend.common.api.ConflictException;
import com.minor_project.optiserve_backend.common.api.ResourceNotFoundException;
import com.minor_project.optiserve_backend.customer.domain.Customer;
import com.minor_project.optiserve_backend.customer.persistence.CustomerRepository;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import com.minor_project.optiserve_backend.operations.persistence.ServiceRequestRepository;
import com.minor_project.optiserve_backend.operations.persistence.VehicleRepository;
import com.minor_project.optiserve_backend.operations.vehicle.api.VehicleRequest;
import com.minor_project.optiserve_backend.operations.vehicle.api.VehicleResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleApplicationService {

    private final VehicleRepository vehicleRepository;
    private final CustomerRepository customerRepository;
    private final ServiceRequestRepository serviceRequestRepository;

    public VehicleApplicationService(
            VehicleRepository vehicleRepository,
            CustomerRepository customerRepository,
            ServiceRequestRepository serviceRequestRepository) {
        this.vehicleRepository = vehicleRepository;
        this.customerRepository = customerRepository;
        this.serviceRequestRepository = serviceRequestRepository;
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> findMine(String email) {
        Customer customer = findCustomer(email);
        return vehicleRepository.findAllByCustomer_IdOrderByCreatedAtDesc(customer.getId())
                .stream().map(VehicleApplicationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public VehicleResponse findMine(String email, UUID vehicleId) {
        return toResponse(findOwnedVehicle(email, vehicleId));
    }

    @Transactional
    public VehicleResponse create(String email, VehicleRequest request) {
        Customer customer = findCustomer(email);
        Vehicle vehicle = Vehicle.create(customer, request.registrationNumber(), request.make(),
                request.model(), request.year());
        return toResponse(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleResponse update(String email, UUID vehicleId, VehicleRequest request) {
        Vehicle vehicle = findOwnedVehicle(email, vehicleId);
        vehicle.updateDetails(request.registrationNumber(), request.make(), request.model(), request.year());
        return toResponse(vehicleRepository.save(vehicle));
    }

    @Transactional
    public void delete(String email, UUID vehicleId) {
        Vehicle vehicle = findOwnedVehicle(email, vehicleId);
        if (serviceRequestRepository.existsByVehicle_Id(vehicleId)) {
            throw new ConflictException("Vehicle cannot be deleted because it has service history.");
        }
        vehicleRepository.delete(vehicle);
    }

    private Customer findCustomer(String email) {
        return customerRepository.findByUser_EmailIgnoreCase(email)
                .orElseThrow(() -> new ConflictException("Complete your customer profile before adding vehicles."));
    }

    private Vehicle findOwnedVehicle(String email, UUID vehicleId) {
        return vehicleRepository.findByIdAndCustomer_User_EmailIgnoreCase(vehicleId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle was not found."));
    }

    private static VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getRegistrationNumber(), vehicle.getMake(),
                vehicle.getModel(), vehicle.getYear(), vehicle.getCreatedAt(), vehicle.getUpdatedAt());
    }
}
