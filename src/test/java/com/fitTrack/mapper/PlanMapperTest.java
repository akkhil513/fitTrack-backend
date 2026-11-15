package com.fitTrack.mapper;

import com.fitTrack.model.FitPlan;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanMapperTest {

    @Test
    void roundTripKeepsWeekNumberAndStatus() {
        FitPlan plan = new FitPlan();
        plan.setUserId("u1");
        plan.setStatus("READY");
        plan.setWeekNumber(3);
        plan.setStrategy("Strategy");

        FitPlan back = PlanMapper.toPlan(PlanMapper.toMap(plan));

        assertEquals("u1", back.getUserId());
        assertEquals("READY", back.getStatus());
        assertEquals(3, back.getWeekNumber());
        assertEquals("Strategy", back.getStrategy());
    }

    @Test
    void toPlanDefaultsMissingFields() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("userId", AttributeValue.fromS("u1"));

        FitPlan plan = PlanMapper.toPlan(item);

        assertEquals(1, plan.getWeekNumber());
        assertEquals("GENERATING", plan.getStatus());
    }

    @Test
    void toMapDefaultsMissingStatusToGenerating() {
        FitPlan plan = new FitPlan();
        plan.setUserId("u1");

        assertEquals("GENERATING", PlanMapper.toMap(plan).get("status").s());
    }
}
