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
        map.put("weekNumber", AttributeValue.fromN(String.valueOf(plan.getWeekNumber())));
        map.put("profile",        AttributeValue.fromS(nullSafe(plan.getProfile())));
        map.put("weekSummary",    AttributeValue.fromS(nullSafe(plan.getWeekSummary())));
        return map;
    }

    public static FitPlan toPlan(Map<String, AttributeValue> item) {
        FitPlan plan = new FitPlan();
        plan.setUserId(item.getOrDefault("userId", AttributeValue.fromS("")).s());
        plan.setPlanId(item.getOrDefault("planId", AttributeValue.fromS("")).s());
        plan.setCreatedAt(item.getOrDefault("createdAt", AttributeValue.fromS("")).s());
        plan.setStrategy(item.getOrDefault("strategy", AttributeValue.fromS("")).s());
        plan.setTraining(item.getOrDefault("training", AttributeValue.fromS("")).s());
        plan.setNutrition(item.getOrDefault("nutrition", AttributeValue.fromS("")).s());
        plan.setSupplements(item.getOrDefault("supplements", AttributeValue.fromS("")).s());
        plan.setRecovery(item.getOrDefault("recovery", AttributeValue.fromS("")).s());
        plan.setStatus(item.getOrDefault("status", AttributeValue.fromS("GENERATING")).s());
        plan.setDailyChecklist(item.getOrDefault("dailyChecklist", AttributeValue.fromS(" ")).s());
        plan.setWeekNumber(Integer.parseInt(
                item.getOrDefault("weekNumber", AttributeValue.fromN("1")).n()));
        plan.setProfile(item.getOrDefault("profile", AttributeValue.fromS(" ")).s());
        plan.setWeekSummary(item.getOrDefault("weekSummary", AttributeValue.fromS(" ")).s());
        return plan;
    }
}