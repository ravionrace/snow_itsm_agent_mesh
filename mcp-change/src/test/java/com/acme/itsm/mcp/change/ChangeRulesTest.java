package com.acme.itsm.mcp.change;

import com.acme.itsm.mcp.common.ToolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeRulesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void emergencyIsAlwaysBlocked() {
        assertThatThrownBy(() -> ChangeRules.rejectEmergency("Emergency"))
                .isInstanceOf(ToolException.class).extracting("code").isEqualTo("NO_EMERGENCY_CHANGE");
    }

    @Test
    void existingEmergencyChangeCannotBeSubmittedOrMoved() throws Exception {
        var emergency = mapper.readTree("{\"number\":\"CHG0030001\",\"type\":\"emergency\",\"state_code\":\"-5\"}");
        assertThatThrownBy(() -> ChangeRules.checkReadyForApproval(emergency))
                .extracting("code").isEqualTo("NO_EMERGENCY_CHANGE");
        assertThatThrownBy(() -> ChangeRules.checkTransition(emergency, Map.of("state", "implement")))
                .extracting("code").isEqualTo("NO_EMERGENCY_CHANGE");
    }

    @Test
    void standardNeedsTemplateAndWindowMustBeOrdered() {
        Map<String, Object> body = new HashMap<>(Map.of("type", "standard"));
        assertThatThrownBy(() -> ChangeRules.checkCreate(body)).hasMessageContaining("template");
        Map<String, Object> normal = new HashMap<>(Map.of("type", "normal",
                "start_date", "2026-10-10 10:00:00", "end_date", "2026-10-10 09:00:00"));
        assertThatThrownBy(() -> ChangeRules.checkCreate(normal)).hasMessageContaining("after");
    }

    @Test
    void transitionsFollowTheModel() throws Exception {
        var scheduled = mapper.readTree("{\"number\":\"CHG0030002\",\"type\":\"normal\",\"state_code\":\"-2\",\"state\":\"Scheduled\"}");
        assertThatCode(() -> ChangeRules.checkTransition(scheduled, Map.of("state", "implement"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> ChangeRules.checkTransition(scheduled, Map.of("state", "closed")))
                .extracting("code").isEqualTo("INVALID_TRANSITION");
    }

    @Test
    void normalChangeNeedsPlansBeforeApproval() throws Exception {
        var noPlans = mapper.readTree("""
                {"number":"CHG0030003","type":"normal","state_code":"-5",
                 "start_date":"2026-10-10 10:00:00","end_date":"2026-10-10 12:00:00"}""");
        assertThatThrownBy(() -> ChangeRules.checkReadyForApproval(noPlans)).hasMessageContaining("implementation plan");
    }
}
