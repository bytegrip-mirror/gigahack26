package com.pistonpunk.gigahack26.web;

import com.pistonpunk.gigahack26.service.BacktestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    private final BacktestService backtest;

    public BacktestController(BacktestService backtest) {
        this.backtest = backtest;
    }

    @GetMapping
    public BacktestService.Outcome replay(@RequestParam(name = "serviceLine") String serviceLine,
                                          @RequestParam(name = "daysAgo", defaultValue = "90") int daysAgo) {
        return backtest.replay(serviceLine, Math.max(7, Math.min(365, daysAgo)));
    }
}
