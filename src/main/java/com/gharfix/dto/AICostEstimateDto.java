package com.gharfix.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * Grounded cost estimation DTO based on GharFix database catalog rates.
 * All cost estimates are clearly designated as non-guaranteed estimates.
 *
 * Distinguishes between:
 * - DATABASE_CATALOG_ESTIMATE: Real rates supplied from GharFix database.
 * - PRELIMINARY_AI_ESTIMATE: Uncatalogued trade, clearly labeled as a preliminary AI estimate
 *   requiring professional on-site inspection.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AICostEstimateDto {

    public static final String TYPE_DATABASE_CATALOG = "DATABASE_CATALOG_ESTIMATE";
    public static final String TYPE_PRELIMINARY_AI = "PRELIMINARY_AI_ESTIMATE";

    private String serviceCategory;
    private Integer basePricePerHour;
    private Integer estimatedDurationMinutes;
    private BigDecimal estimatedMinCost;
    private BigDecimal estimatedMaxCost;
    private String pricingBasis;
    private boolean databaseGrounded = true;
    private String estimateType = TYPE_DATABASE_CATALOG;
    private String disclaimer = "Indicative estimate based on GharFix catalog labor rates. " +
            "Exact price is confirmed with the service partner upon on-site diagnosis. Parts/materials are charged separately.";

    public AICostEstimateDto() {
    }

    public AICostEstimateDto(String serviceCategory, Integer basePricePerHour, Integer estimatedDurationMinutes,
                             BigDecimal estimatedMinCost, BigDecimal estimatedMaxCost, String pricingBasis) {
        this.serviceCategory = serviceCategory;
        this.basePricePerHour = basePricePerHour;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.estimatedMinCost = estimatedMinCost;
        this.estimatedMaxCost = estimatedMaxCost;
        this.pricingBasis = pricingBasis;
        this.databaseGrounded = true;
        this.estimateType = TYPE_DATABASE_CATALOG;
    }

    public static AICostEstimateDto databaseGrounded(String serviceCategory, Integer basePricePerHour,
                                                     Integer estimatedDurationMinutes, BigDecimal minCost,
                                                     BigDecimal maxCost, String pricingBasis) {
        AICostEstimateDto dto = new AICostEstimateDto(serviceCategory, basePricePerHour, estimatedDurationMinutes, minCost, maxCost, pricingBasis);
        dto.setDatabaseGrounded(true);
        dto.setEstimateType(TYPE_DATABASE_CATALOG);
        dto.setDisclaimer("Indicative estimate based on verified GharFix catalog rates. " +
                "Final price is confirmed with the technician upon on-site diagnosis. Parts/materials are charged separately.");
        return dto;
    }

    public static AICostEstimateDto preliminaryAiEstimate(String serviceCategory, BigDecimal minCost,
                                                          BigDecimal maxCost, String basis) {
        AICostEstimateDto dto = new AICostEstimateDto();
        dto.setServiceCategory(serviceCategory);
        dto.setEstimatedMinCost(minCost);
        dto.setEstimatedMaxCost(maxCost);
        dto.setPricingBasis(basis);
        dto.setDatabaseGrounded(false);
        dto.setEstimateType(TYPE_PRELIMINARY_AI);
        dto.setDisclaimer("Preliminary AI estimate: This service is not yet part of standard GharFix database catalog pricing. " +
                "This range is strictly indicative and requires professional on-site inspection. Actual prices may differ based on parts and site conditions.");
        return dto;
    }

    public String getServiceCategory() {
        return serviceCategory;
    }

    public void setServiceCategory(String serviceCategory) {
        this.serviceCategory = serviceCategory;
    }

    public Integer getBasePricePerHour() {
        return basePricePerHour;
    }

    public void setBasePricePerHour(Integer basePricePerHour) {
        this.basePricePerHour = basePricePerHour;
    }

    public Integer getEstimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public void setEstimatedDurationMinutes(Integer estimatedDurationMinutes) {
        this.estimatedDurationMinutes = estimatedDurationMinutes;
    }

    public BigDecimal getEstimatedMinCost() {
        return estimatedMinCost;
    }

    public void setEstimatedMinCost(BigDecimal estimatedMinCost) {
        this.estimatedMinCost = estimatedMinCost;
    }

    public BigDecimal getEstimatedMaxCost() {
        return estimatedMaxCost;
    }

    public void setEstimatedMaxCost(BigDecimal estimatedMaxCost) {
        this.estimatedMaxCost = estimatedMaxCost;
    }

    public String getPricingBasis() {
        return pricingBasis;
    }

    public void setPricingBasis(String pricingBasis) {
        this.pricingBasis = pricingBasis;
    }

    public boolean isDatabaseGrounded() {
        return databaseGrounded;
    }

    public void setDatabaseGrounded(boolean databaseGrounded) {
        this.databaseGrounded = databaseGrounded;
    }

    public String getEstimateType() {
        return estimateType;
    }

    public void setEstimateType(String estimateType) {
        this.estimateType = estimateType;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }
}
