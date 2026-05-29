package com.albudoor.hms.premature.listbeds;

import com.albudoor.hms.premature.api.BedResponse;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListBedsHandler {

    private final BedRepository beds;

    public ListBedsHandler(BedRepository beds) {
        this.beds = beds;
    }

    @Transactional(readOnly = true)
    public List<BedResponse> list() {
        return beds.findAllByOrderByCodeAsc().stream().map(BedResponse::from).toList();
    }
}
