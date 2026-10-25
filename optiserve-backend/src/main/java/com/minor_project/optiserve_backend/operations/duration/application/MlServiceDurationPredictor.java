package com.minor_project.optiserve_backend.operations.duration.application;

import com.minor_project.optiserve_backend.operations.domain.ServiceRequest;
import com.minor_project.optiserve_backend.operations.domain.ServiceStage;
import com.minor_project.optiserve_backend.operations.domain.ServiceType;
import com.minor_project.optiserve_backend.operations.domain.Vehicle;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Calls the advisory ML service and deterministically falls back to the service type duration. */
@Service
@Primary
public class MlServiceDurationPredictor implements ServiceDurationPredictor {

    static final String SUCCESS_METRIC = "optiserve.ml.predictions.success";
    static final String TIMEOUT_METRIC = "optiserve.ml.predictions.timeout";
    static final String ERROR_METRIC = "optiserve.ml.predictions.error";
    static final String FALLBACK_METRIC = "optiserve.ml.predictions.fallback";

    static final int MIN_SUPPORTED_VEHICLE_YEAR = 2008;
    static final int MAX_SUPPORTED_VEHICLE_YEAR = 2026;
    private static final Logger LOGGER = LoggerFactory.getLogger(MlServiceDurationPredictor.class);

    private final RestClient restClient;
    private final DefaultServiceDurationPredictor defaultPredictor;
    private final ZoneId serviceTimeZone;
    private final int minimumPredictionMinutes;
    private final int maximumPredictionMinutes;
    private final Counter successfulPredictions;
    private final Counter predictionTimeouts;
    private final Counter predictionErrors;
    private final Counter fallbackUses;

    public MlServiceDurationPredictor(
            DefaultServiceDurationPredictor defaultPredictor,
            MeterRegistry meterRegistry,
            @Value("${optiserve.ml.base-url:http://localhost:8000}") String baseUrl,
            @Value("${optiserve.ml.connect-timeout:250ms}") Duration connectTimeout,
            @Value("${optiserve.ml.response-timeout:1s}") Duration responseTimeout,
            @Value("${optiserve.ml.service-time-zone:Asia/Kolkata}") String serviceTimeZone,
            @Value("${optiserve.ml.minimum-duration-minutes:5}") int minimumPredictionMinutes,
            @Value("${optiserve.ml.maximum-duration-minutes:1440}") int maximumPredictionMinutes) {
        this.defaultPredictor = Objects.requireNonNull(defaultPredictor, "defaultPredictor must not be null");
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.successfulPredictions = meterRegistry.counter(SUCCESS_METRIC);
        this.predictionTimeouts = meterRegistry.counter(TIMEOUT_METRIC);
        this.predictionErrors = meterRegistry.counter(ERROR_METRIC);
        this.fallbackUses = meterRegistry.counter(FALLBACK_METRIC);
        this.serviceTimeZone = ZoneId.of(serviceTimeZone);
        if (minimumPredictionMinutes <= 0 || maximumPredictionMinutes < minimumPredictionMinutes) {
            throw new IllegalArgumentException("ML duration bounds must be positive and ordered");
        }
        this.minimumPredictionMinutes = minimumPredictionMinutes;
        this.maximumPredictionMinutes = maximumPredictionMinutes;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(responseTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Duration predict(ServiceStage serviceStage) {
        Objects.requireNonNull(serviceStage, "serviceStage must not be null");
        MlPredictionRequest request = toRequest(serviceStage);
        if (request == null) {
            return fallback(serviceStage);
        }

        try {
            MlPredictionResponse response = restClient.post()
                    .uri("/predict-duration")
                    .body(request)
                    .retrieve()
                    .body(MlPredictionResponse.class);
            Integer minutes = null;
            if (response != null && response.predictedDurationMinutes() != null) {
                try {
                    minutes = response.predictedDurationMinutes().intValueExact();
                } catch (ArithmeticException exception) {
                    LOGGER.warn("ML duration response was not a whole number; using the service type default duration");
                }
            }
            if (minutes == null || minutes < minimumPredictionMinutes || minutes > maximumPredictionMinutes) {
                LOGGER.warn("ML duration response was invalid; using the service type default duration");
                predictionErrors.increment();
                return fallback(serviceStage);
            }
            successfulPredictions.increment();
            return Duration.ofMinutes(minutes);
        } catch (RestClientException exception) {
            if (isTimeout(exception)) {
                predictionTimeouts.increment();
            } else {
                predictionErrors.increment();
            }
            LOGGER.warn("ML duration service failed; using the service type default duration ({})",
                    exception.getClass().getSimpleName());
            return fallback(serviceStage);
        }
    }

    private Duration fallback(ServiceStage serviceStage) {
        fallbackUses.increment();
        return defaultPredictor.predict(serviceStage);
    }

    private static boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private MlPredictionRequest toRequest(ServiceStage stage) {
        ServiceRequest serviceRequest = stage.getWorkflow().getServiceRequest();
        Vehicle vehicle = serviceRequest.getVehicle();
        ServiceType serviceType = stage.getServiceType();
        int vehicleYear = vehicle.getYear();
        if (vehicleYear < MIN_SUPPORTED_VEHICLE_YEAR || vehicleYear > MAX_SUPPORTED_VEHICLE_YEAR) {
            LOGGER.debug("ML duration service does not support this vehicle year; using default duration");
            return null;
        }

        Instant serviceDate = serviceRequest.getAppointmentAt() == null
                ? serviceRequest.getRequestedAt()
                : serviceRequest.getAppointmentAt();
        if (serviceDate == null) {
            LOGGER.warn("ML duration input date was unavailable; using the service type default duration");
            return null;
        }

        return new MlPredictionRequest(
                serviceType.getName(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicleYear,
                serviceDate.atZone(serviceTimeZone).getDayOfWeek().name());
    }
}
