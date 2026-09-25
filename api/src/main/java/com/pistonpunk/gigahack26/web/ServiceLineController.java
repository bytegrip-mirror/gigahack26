package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.service.ServiceLineService;
import com.pistonpunk.gigahack26.web.dto.ServiceLineRequest;
import com.pistonpunk.gigahack26.web.dto.ServiceLineView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/service-lines")
public class ServiceLineController {

    private final ServiceLineService lines;

    public ServiceLineController(ServiceLineService lines) {
        this.lines = lines;
    }

    @GetMapping
    public List<ServiceLineView> list(@RequestParam(name = "includeInactive", defaultValue = "false")
                                      boolean includeInactive) {
        return lines.list(includeInactive);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceLineView create(@Valid @RequestBody ServiceLineRequest request) {
        return lines.create(request);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ServiceLineView> update(@PathVariable UUID id,
                                                  @Valid @RequestBody ServiceLineRequest request) {
        return lines.update(id, request).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        lines.delete(id);
        return ResponseEntity.noContent().build();
    }
}
