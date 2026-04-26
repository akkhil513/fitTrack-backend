package com.fitTrack.mapper;

import com.fitTrack.model.FitPlan;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

public class PlanMapper {

    private static String nullSafe(String val) {
        return val != null ? val : " ";
    }

    public static Map<String, AttributeValue> toMap(FitPlan plan) {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put("userId",         AttributeValue.fromS(nullSafe(plan.getUserId())));
        map.put("planId",         AttributeValue.fromS(nullSafe(plan.getPlanId())));
        map.put("createdAt",      AttributeValue.fromS(nullSafe(plan.getCreatedAt())));
        map.put("strategy",       AttributeValue.fromS(nullSafe(plan.getStrategy())));
        map.put("training",       AttributeValue.fromS(nullSafe(plan.getTraining())));
        map.put("nutrition",      AttributeValue.fromS(nullSafe(plan.getNutrition())));
        map.put("supplements",    AttributeValue.fromS(nullSafe(plan.getSupplements())));
        map.put("recovery",       AttributeValue.fromS(nullSafe(plan.getRecovery())));
        map.put("status",         AttributeValue.fromS(plan.getStatus() != null ? plan.getStatus() : "GENERATING"));
        map.put("dailyChecklist", AttributeValue.fromS(nullSafe(plan.getDailyChecklist())));
        return map;
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
        plan.setStatus(item.getOrDefault("status", AttributeValue.fromS("GENERATING")).s());
        plan.setDailyChecklist(item.getOrDefault("dailyChecklist", AttributeValue.fromS(" ")).s());
        return plan;
    }
}