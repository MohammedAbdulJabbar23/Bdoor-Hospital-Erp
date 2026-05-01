package com.albudoor.hms.catalogue.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A billable item from the service catalogue.
 *
 * <p>One row per concrete offering — e.g. "CBC" (LAB), "Chest X-Ray" (IMAGING), "Above-Elbow
 * Cast" (EMERGENCY), "Paracetamol 500mg" (DRUG). Codes are unique within a category so that
 * department staff can search by mnemonic.
 *
 * <p>The {@code forwardTo} field encodes the locked decision that some Emergency catalogue
 * items (Echo, Lab, Sonar, X-Ray) are not in-Emergency charges but pointers that route the
 * patient to the receiving department's catalogue + cashier flow.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "service_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_service_item_category_code", columnNames = {"category", "code"})
})
public class ServiceItem extends AggregateRoot {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ServiceCategory category;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "name_en", nullable = false, length = 300)
    private String nameEn;

    @Column(name = "name_ar", length = 300)
    private String nameAr;

    @Column(length = 1000)
    private String description;

    @Column(precision = 12, scale = 2)
    private BigDecimal fee;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private boolean active;

    /**
     * If set, selecting this item triggers a forward-to-target-department workflow rather than
     * an in-place charge. See HMS comments + locked decision on Emergency catalogue items #37–40.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "forward_to", length = 30)
    private ServiceCategory forwardTo;

    @Embedded
    private DrugDetails drugDetails;

    public static ServiceItem create(
            ServiceCategory category,
            String code,
            String nameEn,
            String nameAr,
            String description,
            BigDecimal fee,
            String currency,
            Integer sortOrder,
            ServiceCategory forwardTo,
            DrugDetails drugDetails
    ) {
        validate(category, code, nameEn, fee, currency);
        if (category == ServiceCategory.DRUG && drugDetails == null) {
            throw new DomainException("DRUG_DETAILS_REQUIRED", "Drug details are required for DRUG items");
        }
        if (forwardTo != null && forwardTo == category) {
            throw new DomainException("INVALID_FORWARD_TO",
                    "forwardTo must point to a different category");
        }
        ServiceItem item = new ServiceItem();
        item.id = UUID.randomUUID();
        item.category = category;
        item.code = code.trim();
        item.nameEn = nameEn.trim();
        item.nameAr = nameAr == null ? null : nameAr.trim();
        item.description = description;
        item.fee = forwardTo != null ? null : fee;
        item.currency = currency;
        item.sortOrder = sortOrder == null ? 0 : sortOrder;
        item.active = true;
        item.forwardTo = forwardTo;
        item.drugDetails = drugDetails;
        return item;
    }

    public void update(
            String nameEn,
            String nameAr,
            String description,
            BigDecimal fee,
            String currency,
            Integer sortOrder,
            ServiceCategory forwardTo,
            DrugDetails drugDetails
    ) {
        if (nameEn == null || nameEn.isBlank()) {
            throw new DomainException("SERVICE_NAME_REQUIRED", "English name is required");
        }
        if (forwardTo != null && forwardTo == this.category) {
            throw new DomainException("INVALID_FORWARD_TO",
                    "forwardTo must point to a different category");
        }
        this.nameEn = nameEn.trim();
        this.nameAr = nameAr == null ? null : nameAr.trim();
        this.description = description;
        this.fee = forwardTo != null ? null : fee;
        this.currency = currency == null ? this.currency : currency;
        this.sortOrder = sortOrder == null ? this.sortOrder : sortOrder;
        this.forwardTo = forwardTo;
        if (this.category == ServiceCategory.DRUG) {
            this.drugDetails = drugDetails;
        }
    }

    public void archive() {
        this.active = false;
    }

    public void unarchive() {
        this.active = true;
    }

    private static void validate(
            ServiceCategory category, String code, String nameEn, BigDecimal fee, String currency
    ) {
        if (category == null) {
            throw new DomainException("SERVICE_CATEGORY_REQUIRED", "Category is required");
        }
        if (code == null || code.isBlank()) {
            throw new DomainException("SERVICE_CODE_REQUIRED", "Code is required");
        }
        if (nameEn == null || nameEn.isBlank()) {
            throw new DomainException("SERVICE_NAME_REQUIRED", "English name is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new DomainException("SERVICE_CURRENCY_REQUIRED", "Currency is required");
        }
        if (fee != null && fee.signum() < 0) {
            throw new DomainException("SERVICE_FEE_NEGATIVE", "Fee must be non-negative");
        }
    }
}
