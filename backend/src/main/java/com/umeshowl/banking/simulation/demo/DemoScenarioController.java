package com.umeshowl.banking.simulation.demo;

import com.umeshowl.banking.simulation.demo.dto.DemoScenarioRunResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation/demos")
public class DemoScenarioController {

    private final DemoScenarioService demoScenarioService;

    public DemoScenarioController(DemoScenarioService demoScenarioService) {
        this.demoScenarioService = demoScenarioService;
    }

    @PostMapping("/structuring")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public DemoScenarioRunResponse runStructuringDemo() {
        return demoScenarioService.run(DemoScenarioType.STRUCTURING_DEMO);
    }

    @PostMapping("/high-risk-wire")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public DemoScenarioRunResponse runHighRiskWireDemo() {
        return demoScenarioService.run(DemoScenarioType.HIGH_RISK_WIRE_DEMO);
    }

    @PostMapping("/money-mule")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public DemoScenarioRunResponse runMoneyMuleDemo() {
        return demoScenarioService.run(DemoScenarioType.MONEY_MULE_DEMO);
    }

    @PostMapping("/normal")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public DemoScenarioRunResponse runNormalActivityDemo() {
        return demoScenarioService.run(DemoScenarioType.NORMAL_ACTIVITY_DEMO);
    }
}
