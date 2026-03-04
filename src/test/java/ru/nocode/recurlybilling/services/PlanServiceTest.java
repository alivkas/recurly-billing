package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.nocode.recurlybilling.data.dto.request.PlanCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.PlanResponse;
import ru.nocode.recurlybilling.data.entities.Plan;
import ru.nocode.recurlybilling.data.repositories.PlanRepository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    @Mock
    private ObjectMapper objectMapper;

    private PlanService planService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        planService = new PlanService(planRepository, objectMapper);
    }

    @Test
    void createPlanWithSemesterIntervalShouldSetEndDateToDec31() {
        var request = new PlanCreateRequest(
                "math-autumn-2025",
                "Математика — осень 2025",
                400000L,
                "RUB",
                "semester",
                1,
                0,
                LocalDate.of(2025, 9, 1),
                null,
                Map.of("semester", "2025-осень")
        );

        when(planRepository.existsByTenantIdAndCode("moscow_digital_school", "math-autumn-2025"))
                .thenReturn(false);

        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));

        when(objectMapper.valueToTree(any(Map.class)))
                .thenReturn(JsonNodeFactory.instance.objectNode().put("semester", "2025-осень"));
        when(objectMapper.createObjectNode())
                .thenReturn(JsonNodeFactory.instance.objectNode());

        PlanResponse response = planService.createPlan("moscow_digital_school", request);

        assertThat(response.endDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    }
}