package com.albudoor.hms.catalogue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Drug-specific catalogue fields. Only populated when {@link ServiceItem#getCategory()} is DRUG.
 * Inventory and dispensing live in the Pharmacy module — this is just product metadata.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DrugDetails {

    @Column(name = "drug_generic_name", length = 200)
    private String genericName;

    /** Tablet, syrup, injection, capsule, etc. */
    @Column(name = "drug_dosage_form", length = 100)
    private String dosageForm;

    /** "500mg", "5mg/ml", "100 IU/ml". */
    @Column(name = "drug_strength", length = 100)
    private String strength;

    /** "box", "bottle", "vial", "tablet". */
    @Column(name = "drug_unit", length = 50)
    private String unit;

    @Column(name = "drug_controlled", nullable = false)
    private boolean controlled;

    @Column(name = "drug_supplier", length = 200)
    private String supplier;

    @Column(name = "drug_barcode", length = 100)
    private String barcode;
}
