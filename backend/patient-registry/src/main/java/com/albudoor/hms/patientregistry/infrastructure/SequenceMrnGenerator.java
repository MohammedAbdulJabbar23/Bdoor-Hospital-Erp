package com.albudoor.hms.patientregistry.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Default MRN generator: {prefix}-{YYYY}-{6-digit-sequence}, e.g. ALB-2026-000001.
 * Backed by a Postgres sequence so concurrent inserts don't collide.
 *
 * Format is configurable via hms.patient.mrn.prefix; the year resets the visual prefix
 * but the sequence keeps climbing — the (mrn) column is unique anyway. When the client
 * delivers their preferred MRN spec, this class is the single point of change.
 */
@Component
public class SequenceMrnGenerator implements MrnGenerator {

    private final String prefix;

    @PersistenceContext
    private EntityManager em;

    public SequenceMrnGenerator(@Value("${hms.patient.mrn.prefix:ALB}") String prefix) {
        this.prefix = prefix;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public String next() {
        Number seq = (Number) em.createNativeQuery("SELECT nextval('mrn_seq')").getSingleResult();
        return "%s-%d-%06d".formatted(prefix, LocalDate.now().getYear(), seq.longValue());
    }
}
