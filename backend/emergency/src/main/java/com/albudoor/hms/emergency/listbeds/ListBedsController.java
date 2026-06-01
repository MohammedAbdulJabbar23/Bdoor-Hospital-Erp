package com.albudoor.hms.emergency.listbeds;

import com.albudoor.hms.emergency.api.BedResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("emergencyListBedsController")
@RequestMapping("/api/emergency/beds")
@PreAuthorize("isAuthenticated()")
public class ListBedsController {

    private final ListBedsHandler handler;

    public ListBedsController(ListBedsHandler handler) {
        this.handler = handler;
    }

    @GetMapping
    public List<BedResponse> list() {
        return handler.list();
    }
}
