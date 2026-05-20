package com.albudoor.hms.doctorappointment.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A practising doctor at the hospital. The {@code userId} optionally links to an HMS
 * login account so the doctor can see their own schedule when signed in. The fee is the
 * default consultation fee charged at reception when an appointment is booked; admin can
 * override per-visit if needed.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "doctor")
public class Doctor extends AggregateRoot {

    @Id
    private UUID id;

    /** Optional link to an {@code app_user} so the doctor can log in. */
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 200)
    private String specialty;

    @Column(name = "consultation_fee", precision = 12, scale = 2)
    private BigDecimal consultationFee;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "active", nullable = false)
    private boolean active;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "doctor_weekly_hour",
            joinColumns = @JoinColumn(name = "doctor_id")
    )
    private List<WeeklyHour> weeklyHours = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "doctor_day_off",
            joinColumns = @JoinColumn(name = "doctor_id")
    )
    private List<DayOff> daysOff = new ArrayList<>();

    public static Doctor create(
            UUID userId,
            String fullName,
            String specialty,
            BigDecimal consultationFee,
            String currency,
            String phone
    ) {
        if (fullName == null || fullName.isBlank()) {
            throw new DomainException("DOCTOR_NAME_REQUIRED", "Full name is required");
        }
        if (currency == null || currency.isBlank()) {
            throw new DomainException("DOCTOR_CURRENCY_REQUIRED", "Currency is required");
        }
        Doctor d = new Doctor();
        d.id = UUID.randomUUID();
        d.userId = userId;
        d.fullName = fullName.trim();
        d.specialty = specialty;
        d.consultationFee = consultationFee;
        d.currency = currency;
        d.phone = phone;
        d.active = true;
        return d;
    }

    public void update(String fullName, String specialty, BigDecimal fee, String currency, String phone) {
        if (fullName != null && !fullName.isBlank()) this.fullName = fullName.trim();
        if (specialty != null) this.specialty = specialty;
        if (fee != null) this.consultationFee = fee;
        if (currency != null && !currency.isBlank()) this.currency = currency;
        if (phone != null) this.phone = phone;
    }

    public void replaceSchedule(List<WeeklyHour> newHours) {
        this.weeklyHours.clear();
        if (newHours != null) this.weeklyHours.addAll(newHours);
    }

    public void addDayOff(DayOff dayOff) {
        this.daysOff.add(dayOff);
    }

    public void removeDayOff(java.time.LocalDate date) {
        this.daysOff.removeIf(d -> d.getDate().equals(date));
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
