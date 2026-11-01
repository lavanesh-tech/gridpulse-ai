package com.gridpulse.fleet.api;

import com.gridpulse.fleet.Fleet;
import com.gridpulse.fleet.FleetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fleets")
public class FleetController {
    private final FleetService service;

    public FleetController(FleetService service) {
        this.service = service;
    }

    public record CreateFleetRequest(@NotBlank @Size(max = 200) String name) {
    }

    public record FleetResponse(UUID id, String name, Instant createdAt) {
        static FleetResponse of(Fleet f) {
            return new FleetResponse(f.getId(), f.getName(), f.getCreatedAt());
        }
    }

    @GetMapping
    public List<FleetResponse> list() {
        return service.list().stream().map(FleetResponse::of).toList();
    }

    @GetMapping("/{id}")
    public FleetResponse get(@PathVariable UUID id) {
        return FleetResponse.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER')")
    public FleetResponse create(@Valid @RequestBody CreateFleetRequest request) {
        return FleetResponse.of(service.create(request.name()));
    }
}
