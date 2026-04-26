package com.fitTrack.mapper;

import com.fitTrack.model.FitPlan;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

public class PlanMapper {

    public static Map<String, AttributeValue> toMap(FitPlan plan) {
        return Map.of(
                "userId",      AttributeValue.fromS(plan.getUserId()),
                "planId",      AttributeValue.fromS(plan.getPlanId()),
                "createdAt",   AttributeValue.fromS(plan.getCreatedAt()),
                "strategy",    AttributeValue.fromS(plan.getStrategy()),
                "training",    AttributeValue.fromS(plan.getTraining()),
                "nutrition",   AttributeValue.fromS(plan.getNutrition()),
                "supplements", AttributeValue.fromS(plan.getSupplements()),
                "recovery",    AttributeValue.fromS(plan.getRecovery()),
                "status", AttributeValue.fromS(plan.getStatus() != null ? plan.getStatus() : "GENERATING")
        );
    }

    public static FitPlan toPlan(Map<String, AttributeValue> item) {
        FitPlan plan = new FitPlan();
        plan.setUserId(item.get("userId").s());
        plan.setPlanId(item.get("planId").s());
        plan.setCreatedAt(item.get("createdAt").s());
        plan.setStrategy(item.get("strategy").s());
        plan.setTraining(item.get("training").s());
        plan.setNutrition(item.get("nutrition").s());
        plan.setSupplements(item.get("supplements").s());
        plan.setRecovery(item.get("recovery").s());
        return plan;
    }
}