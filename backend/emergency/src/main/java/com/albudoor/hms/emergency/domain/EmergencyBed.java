package com.albudoor.hms.emergency.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "emerg_bed")
public class EmergencyBed extends AggregateRoot {

    @Id
    private UUID id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(length = 100)
    private String room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BedStatus status;

    @Column(nullable = false)
    private boolean active;

    public static EmergencyBed create(String code, String room) {
        if (code == null || code.isBlank()) {
            throw new DomainException("BED_CODE_REQUIRED", "Bed code is required");
        }
        EmergencyBed bed = new EmergencyBed();
        bed.id = UUID.randomUUID();
        bed.code = code.trim();
        bed.room = (room == null || room.isBlank()) ? null : room.trim();
        bed.status = BedStatus.AVAILABLE;
        bed.active = true;
        return bed;
    }

    /** Admin edits. */
    public void updateDetails(String room, boolean active) {
        this.room = (room == null || room.isBlank()) ? null : room.trim();
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }

    /** Assign to an admission; bed must be free and active. */
    public void reserve() {
        if (!active) {
            throw new DomainException("BED_INACTIVE", "Bed " + code + " is inactive");
        }
        if (status != BedStatus.AVAILABLE) {
            throw new DomainException("BED_NOT_AVAILABLE",
                    "Bed " + code + " is not available (status=" + status + ")");
        }
        this.status = BedStatus.PENDING_PAYMENT;
    }

    /** Initial payment approved. */
    public void occupy() {
        if (status != BedStatus.PENDING_PAYMENT) {
            throw new DomainException("BED_NOT_PENDING",
                    "Bed " + code + " is not pending payment (status=" + status + ")");
        }
        this.status = BedStatus.OCCUPIED;
    }

    /** Initial payment rejected — free the bed. */
    public void release() {
        if (status != BedStatus.PENDING_PAYMENT) {
            throw new DomainException("BED_NOT_PENDING",
                    "Bed " + code + " is not pending payment (status=" + status + ")");
        }
        this.status = BedStatus.AVAILABLE;
    }

    /** Discharge — free the bed for reuse. */
    public void discharge() {
        if (status != BedStatus.OCCUPIED) {
            throw new DomainException("BED_NOT_OCCUPIED",
                    "Bed " + code + " is not occupied (status=" + status + ")");
        }
        this.status = BedStatus.AVAILABLE;
    }
}
