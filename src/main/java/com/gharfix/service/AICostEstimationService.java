package com.gharfix.service;

import com.gharfix.dto.AICostEstimateDto;
import com.gharfix.entity.ServiceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;

/**
 * Service for calculating deterministic, grounded cost estimates.
 *
 * Core Pricing Philosophy:
 * - Never allow Gemini to invent GharFix pricing.
 * - When a service exists in the GharFix database, use verified database rates.
 * - When database rates are not yet available for an uncatalogued trade, clearly label the result
 *   as a preliminary AI estimate and emphasize that final pricing requires professional on-site inspection.
 */
@Service
public class AICostEstimationService {

    private static final Logger log = LoggerFactory.getLogger(AICostEstimationService.class);

    private final ServiceCategoryService serviceCategoryService;

    public AICostEstimationService(ServiceCategoryService serviceCategoryService) {
        this.serviceCategoryService = serviceCategoryService;
    }

    /**
     * Resolves the canonical service category name from colloquial user queries.
     */
    public Optional<String> detectServiceCategory(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        String lower = query.toLowerCase(Locale.ROOT);
        if (lower.contains("ac") || lower.contains("air condition") || lower.contains("cooling") || lower.contains("compressor")) {
            return Optional.of("AC Service");
        } else if (lower.contains("plumb") || lower.contains("leak") || lower.contains("pipe") || lower.contains("tap") || lower.contains("drain") || lower.contains("sink")) {
            return Optional.of("Plumber");
        } else if (lower.contains("electr") || lower.contains("wire") || lower.contains("wiring") || lower.contains("switch") || lower.contains("short circuit") || lower.contains("fan") || lower.contains("fuse")) {
            return Optional.of("Electrician");
        } else if (lower.contains("carpent") || lower.contains("furniture") || lower.contains("door") || lower.contains("cupboard") || lower.contains("wood")) {
            return Optional.of("Carpenter");
        } else if (lower.contains("clean") || lower.contains("maid") || lower.contains("sweep") || lower.contains("mop") || lower.contains("dust")) {
            return Optional.of("House Maid");
        } else if (lower.contains("laundry") || lower.contains("wash") || lower.contains("iron")) {
            return Optional.of("Laundry");
        } else if (lower.contains("labor") || lower.contains("labour") || lower.contains("shifting") || lower.contains("heavy lifting")) {
            return Optional.of("Labour");
        } else if (lower.contains("cook") || lower.contains("meal") || lower.contains("kitchen food")) {
            return Optional.of("Cook");
        }

        // Check against active services in database directly
        for (ServiceEntity service : serviceCategoryService.getAllServices()) {
            if (lower.contains(service.getName().toLowerCase(Locale.ROOT))) {
                return Optional.of(service.getName());
            }
        }

        return Optional.empty();
    }

    /**
     * Estimates cost for a canonical service category using catalog rates.
     */
    public Optional<AICostEstimateDto> estimateCost(String categoryName) {
        return estimateCostWithComplexity(categoryName, "STANDARD");
    }

    /**
     * Estimates cost for a service category with visual complexity adjustment (MINOR, STANDARD, MAJOR).
     */
    public Optional<AICostEstimateDto> estimateCostWithComplexity(String categoryName, String complexity) {
        if (categoryName == null || categoryName.isBlank()) {
            return Optional.empty();
        }

        log.debug("Calculating cost estimate for category='{}', complexity='{}'", categoryName, complexity);

        Optional<ServiceEntity> entityOpt = serviceCategoryService.findByNameIgnoreCase(categoryName.trim());
        if (entityOpt.isEmpty()) {
            Optional<String> detected = detectServiceCategory(categoryName);
            if (detected.isPresent()) {
                entityOpt = serviceCategoryService.findByNameIgnoreCase(detected.get());
            }
        }

        if (entityOpt.isEmpty()) {
            return Optional.empty();
        }

        ServiceEntity service = entityOpt.get();
        int basePerHour = (service.getBasePricePerHour() != null && service.getBasePricePerHour() > 0)
                ? service.getBasePricePerHour()
                : 150;
        int durationMinutes = (service.getEstimatedDurationMinutes() != null && service.getEstimatedDurationMinutes() > 0)
                ? service.getEstimatedDurationMinutes()
                : 60;

        double baseHours = durationMinutes / 60.0;
        double minMultiplier = 0.8;
        double maxMultiplier = 1.6;

        if ("MINOR".equalsIgnoreCase(complexity)) {
            minMultiplier = 0.6;
            maxMultiplier = 1.2;
        } else if ("MAJOR".equalsIgnoreCase(complexity) || "HIGH".equalsIgnoreCase(complexity) || "EMERGENCY".equalsIgnoreCase(complexity)) {
            minMultiplier = 1.0;
            maxMultiplier = 2.2;
        }

        BigDecimal minCost = BigDecimal.valueOf(Math.max(basePerHour * minMultiplier, 100.0))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal maxCost = BigDecimal.valueOf(basePerHour * Math.max(baseHours * maxMultiplier, 1.4))
                .setScale(0, RoundingMode.HALF_UP);

        String basis = String.format("GharFix database catalog rate: ₹%d/hr (base duration: ~%d mins, complexity: %s)",
                basePerHour, durationMinutes, (complexity != null ? complexity.toUpperCase(Locale.ROOT) : "STANDARD"));

        return Optional.of(AICostEstimateDto.databaseGrounded(
                service.getName(),
                basePerHour,
                durationMinutes,
                minCost,
                maxCost,
                basis
        ));
    }

    /**
     * Provides a clearly labeled preliminary AI estimate when a trade is not in the database catalog.
     */
    public AICostEstimateDto estimatePreliminary(String queryOrCategory) {
        String name = (queryOrCategory != null && !queryOrCategory.isBlank()) ? queryOrCategory.trim() : "Custom Home Service";
        BigDecimal minCost = BigDecimal.valueOf(150);
        BigDecimal maxCost = BigDecimal.valueOf(450);
        String basis = "Preliminary estimate based on standard industry maintenance norms. Not from GharFix database catalog.";

        return AICostEstimateDto.preliminaryAiEstimate(name, minCost, maxCost, basis);
    }

    /**
     * Convenience method to estimate cost with fallback to preliminary estimate.
     */
    public AICostEstimateDto estimateCostWithFallback(String categoryOrQuery) {
        return estimateCost(categoryOrQuery)
                .or(() -> estimateForQuery(categoryOrQuery))
                .orElseGet(() -> estimatePreliminary(categoryOrQuery));
    }

    /**
     * Convenience method to estimate cost directly from free-text problem or query.
     */
    public Optional<AICostEstimateDto> estimateForQuery(String query) {
        return detectServiceCategory(query).flatMap(this::estimateCost);
    }
}
