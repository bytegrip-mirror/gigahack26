package com.pistonpunk.gigahack26.region.web;

import com.pistonpunk.gigahack26.region.application.RegionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionService regions;

    public RegionController(RegionService regions) {
        this.regions = regions;
    }

    @GetMapping
    public List<RegionService.Node> tree() {
        return regions.tree();
    }

    @GetMapping("/countries")
    public List<RegionService.Country> countries() {
        return regions.countries();
    }
}
