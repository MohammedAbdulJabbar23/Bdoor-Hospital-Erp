package com.albudoor.hms.pharmacy.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
public class SequenceDispenseIdGenerator implements DispenseIdGenerator {

    private final String prefix;

    @PersistenceContext
    private EntityManager em;

    public SequenceDispenseIdGenerator(@Value("${hms.pharmacy.id.prefix:RX}") String prefix) {
        this.prefix = prefix;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public String next() {
        Number seq = (Number) em.createNativeQuery("SELECT nextval('pharmacy_dispense_id_seq')").getSingleResult();
        return "%s-%d-%06d".formatted(prefix, LocalDate.now().getYear(), seq.longValue());
    }
}
