package com.albudoor.hms.premature.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "prem_tour")
public class PrematureTour extends AggregateRoot {

    @Id
    private UUID id;

    @Column(name = "admission_id", nullable = false)
    private UUID admissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tour_type", nullable = false, length = 10)
    private TourType tourType;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "resp_rate") private Integer respRate;
    @Column private Integer spo2;
    @Column(name = "pulse_rate") private Integer pulseRate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "prem_tour_resp_support", joinColumns = @JoinColumn(name = "tour_id"))
    @Column(name = "resp_support", length = 20)
    @Enumerated(EnumType.STRING)
    private Set<RespSupport> respSupport = new HashSet<>();

    @Column(name = "bowel_motion") private String bowelMotion;
    @Column private String uop;
    @Column private String feeding;
    @Column private String vomiting;
    @Column private String jaundice;
    @Column(name = "iv_access") private String ivAccess;
    @Column(name = "iv_fluid") private String ivFluid;
    @Column(name = "baby_temp_c", precision = 4, scale = 1) private BigDecimal babyTempC;
    @Column(name = "incubator_temp_c", precision = 4, scale = 1) private BigDecimal incubatorTempC;
    @Column private Integer humidity;
    @Column(name = "nasal_septum") private String nasalSeptum;
    @Column private Integer rbs;
    @Column(length = 2000) private String others;

    public static PrematureTour record(UUID admissionId, TourType tourType, UUID recordedBy, TourVitals v) {
        if (admissionId == null) throw new DomainException("ADMISSION_REQUIRED", "admission is required");
        if (tourType == null) throw new DomainException("TOUR_TYPE_REQUIRED", "tour type is required");
        if (v == null) throw new DomainException("VITALS_REQUIRED", "tour vitals are required");
        if (v.respRate() == null || v.spo2() == null || v.pulseRate() == null
                || v.uop() == null || v.uop().isBlank() || v.babyTempC() == null
                || v.respSupport() == null || v.respSupport().isEmpty()) {
            throw new DomainException("TOUR_VITALS_INCOMPLETE",
                    "respRate, SpO2, pulse, UOP, baby temp and resp support are mandatory per tour");
        }
        PrematureTour t = new PrematureTour();
        t.id = UUID.randomUUID();
        t.admissionId = admissionId;
        t.tourType = tourType;
        t.recordedBy = recordedBy;
        t.recordedAt = Instant.now();
        t.respRate = v.respRate();
        t.spo2 = v.spo2();
        t.pulseRate = v.pulseRate();
        t.respSupport = (v.respSupport() == null) ? new HashSet<>() : new HashSet<>(v.respSupport());
        t.bowelMotion = v.bowelMotion();
        t.uop = v.uop();
        t.feeding = v.feeding();
        t.vomiting = v.vomiting();
        t.jaundice = v.jaundice();
        t.ivAccess = v.ivAccess();
        t.ivFluid = v.ivFluid();
        t.babyTempC = v.babyTempC();
        t.incubatorTempC = v.incubatorTempC();
        t.humidity = v.humidity();
        t.nasalSeptum = v.nasalSeptum();
        t.rbs = v.rbs();
        t.others = v.others();
        return t;
    }
}
