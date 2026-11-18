package com.fitTrack.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fitTrack.mapper.LogMapper;
import com.fitTrack.model.FitLog;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogChecklistTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void anObjectOrAJsonStringBothBecomeTheStoredJson() throws Exception {
        assertEquals("{\"a\":true,\"b\":false}",
                LogResource.checklistToJson(MAPPER.readTree("{\"a\":true,\"b\":false}")));
        assertEquals("{\"a\":true}", LogResource.checklistToJson(TextNode.valueOf("{\"a\": true}")));
    }

    @Test
    void absentOrUnusableValuesKeepWhatIsStored() throws Exception {
        assertEquals(" ", LogResource.checklistToJson(null));
        assertEquals(" ", LogResource.checklistToJson(TextNode.valueOf("not json")));
        assertEquals(" ", LogResource.checklistToJson(TextNode.valueOf("[1,2]")));
        assertEquals(" ", LogResource.checklistToJson(MAPPER.readTree("{}")));
    }

    @Test
    void theAppGetsTheChecklistBackAsAJsonString() throws Exception {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("userId", AttributeValue.fromS("u"));
        item.put("date", AttributeValue.fromS("2026-11-17"));
        item.put("dayNumber", AttributeValue.fromN("1"));
        for (String k : new String[] {"session", "exercises", "protein", "calories", "water", "sleep", "notes"}) {
            item.put(k, AttributeValue.fromS(" "));
        }
        item.put("checklist", AttributeValue.fromS("{\"a\":true}"));
        FitLog log = LogMapper.toLog(item);
        assertTrue(log.getChecklist().isTextual());
        String json = MAPPER.writeValueAsString(log);
        assertTrue(json.contains("\"checklist\":\"{\\\"a\\\":true}\""), json);
    }
}
