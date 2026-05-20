package com.albudoor.hms.pharmacy.inventory;

import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import com.albudoor.hms.pharmacy.domain.DrugBatch;
import com.albudoor.hms.pharmacy.infrastructure.DrugBatchRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory API: list drugs with current stock & expiry, receive new batches, list batches
 * expiring soon. Backs the BRD locked decision that pharmacy tracks inventory and expiry.
 */
@RestController
@RequestMapping("/api/pharmacy/inventory")
public class InventoryController {

    public record BatchResponse(
            UUID id,
            UUID drugServiceItemId,
            String drugCode,
            String drugName,
            String batchNo,
            LocalDate expiryDate,
            int qtyReceived,
            int qtyRemaining,
            BigDecimal unitCost,
            String supplier,
            String status
    ) {
        public static BatchResponse from(DrugBatch b, ServiceItem item) {
            String status = b.getQtyRemaining() == 0 ? "EMPTY"
                    : b.isExpired() ? "EXPIRED"
                    : b.isExpiringSoon(30) ? "EXPIRING_SOON"
                    : "OK";
            return new BatchResponse(
                    b.getId(), b.getDrugServiceItemId(),
                    item == null ? null : item.getCode(),
                    item == null ? null : item.getNameEn(),
                    b.getBatchNo(), b.getExpiryDate(),
                    b.getQtyReceived(), b.getQtyRemaining(),
                    b.getUnitCost(), b.getSupplier(),
                    status
            );
        }
    }

    public record DrugStockResponse(
            UUID drugServiceItemId,
            String drugCode,
            String drugName,
            int totalRemaining,
            LocalDate earliestExpiry,
            int batchCount
    ) {}

    public record ReceiveBatchBody(
            @NotNull UUID drugServiceItemId,
            @NotBlank String batchNo,
            @NotNull LocalDate expiryDate,
            @Positive int qty,
            @PositiveOrZero BigDecimal unitCost,
            String supplier
    ) {}

    private final DrugBatchRepository batches;
    private final ServiceItemRepository catalogue;

    public InventoryController(DrugBatchRepository batches, ServiceItemRepository catalogue) {
        this.batches = batches;
        this.catalogue = catalogue;
    }

    @PostMapping("/batches")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    @Transactional
    public BatchResponse receive(@Valid @RequestBody ReceiveBatchBody body) {
        ServiceItem item = catalogue.findById(body.drugServiceItemId())
                .orElseThrow(() -> new NotFoundException("Drug not found: " + body.drugServiceItemId()));
        if (item.getCategory() != ServiceCategory.DRUG) {
            throw new DomainException("NOT_A_DRUG",
                    item.getCode() + " is not a drug catalogue item");
        }
        DrugBatch saved = batches.save(DrugBatch.receive(
                body.drugServiceItemId(), body.batchNo(),
                body.expiryDate(), body.qty(),
                body.unitCost(), body.supplier(),
                currentUserId()));
        return BatchResponse.from(saved, item);
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public List<BatchResponse> listBatches(@RequestParam(required = false) UUID drugId) {
        List<DrugBatch> list = drugId == null
                ? batches.findAll(Sort())
                : batches.findAllByDrugServiceItemIdOrderByExpiryDateAsc(drugId);
        var items = catalogueIndex(list);
        return list.stream()
                .map(b -> BatchResponse.from(b, items.get(b.getDrugServiceItemId())))
                .toList();
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public List<BatchResponse> expiring(@RequestParam(defaultValue = "60") int withinDays) {
        var list = batches.findExpiringBy(LocalDate.now().plusDays(Math.max(0, withinDays)));
        var items = catalogueIndex(list);
        return list.stream()
                .map(b -> BatchResponse.from(b, items.get(b.getDrugServiceItemId())))
                .toList();
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN', 'DOCTOR')")
    public List<DrugStockResponse> stock() {
        var all = batches.findAll();
        var items = catalogueIndex(all);
        // group by drug
        return all.stream()
                .filter(b -> b.getQtyRemaining() > 0)
                .collect(java.util.stream.Collectors.groupingBy(DrugBatch::getDrugServiceItemId))
                .entrySet().stream()
                .map(e -> {
                    ServiceItem item = items.get(e.getKey());
                    int total = e.getValue().stream().mapToInt(DrugBatch::getQtyRemaining).sum();
                    LocalDate earliest = e.getValue().stream()
                            .map(DrugBatch::getExpiryDate)
                            .min(Comparator.naturalOrder())
                            .orElse(null);
                    return new DrugStockResponse(
                            e.getKey(),
                            item == null ? null : item.getCode(),
                            item == null ? null : item.getNameEn(),
                            total, earliest, e.getValue().size());
                })
                .sorted(Comparator.comparing(DrugStockResponse::drugName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @GetMapping("/drug/{id}")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN', 'DOCTOR')")
    public List<BatchResponse> drugBatches(@PathVariable("id") UUID drugId) {
        var list = batches.findAllByDrugServiceItemIdOrderByExpiryDateAsc(drugId);
        var items = catalogueIndex(list);
        return list.stream()
                .map(b -> BatchResponse.from(b, items.get(b.getDrugServiceItemId())))
                .toList();
    }

    private Map<UUID, ServiceItem> catalogueIndex(List<DrugBatch> list) {
        var ids = list.stream().map(DrugBatch::getDrugServiceItemId).distinct().toList();
        return catalogue.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(ServiceItem::getId, x -> x));
    }

    private static org.springframework.data.domain.Sort Sort() {
        return org.springframework.data.domain.Sort.by("expiryDate").ascending();
    }

    private static UUID currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) return p.userId();
        return null;
    }
}
