package com.pistonpunk.gigahack26.board.web;

import com.pistonpunk.gigahack26.board.application.MarketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketService market;

    public MarketController(MarketService market) {
        this.market = market;
    }

    @GetMapping
    public MarketService.Market market(@RequestParam(name = "serviceLine") String serviceLine) {
        return market.market(serviceLine);
    }
}
