package com.gridpulse.grid.api;

import com.gridpulse.grid.GridConnection;
import com.gridpulse.grid.GridService;
import com.gridpulse.shared.domain.PowerKw;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/grid-connections")
public class GridController {
    private final GridService service;

    public GridController(GridService service) {
        this.service = service;
    }

    public record CreateGridRequest(@NotBlank @Size(max = 200) String name,
                                    @NotNull @Positive @DecimalMax("100000") BigDecimal capacityLimitKw) {
    }

    public record GridResponse(UUID id, String name, BigDecimal capacityLimitKw) {
        static GridResponse of(GridConnection g) {
            return new GridResponse(g.getId(), g.getName(), g.getCapacityLimit().value());
        }
    }

    @GetMapping
    public List<GridResponse> list() {
        return service.list().stream().map(GridResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public GridResponse create(@Valid @RequestBody CreateGridRequest r) {
        return GridResponse.of(service.create(r.name(), new PowerKw(r.capacityLimitKw())));
    }
}
