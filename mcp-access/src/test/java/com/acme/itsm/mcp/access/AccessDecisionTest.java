package com.acme.itsm.mcp.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccessDecisionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> decide(String api, String level) throws Exception {
        var me = mapper.readTree("""
                {"apis":[
                  {"api":"incident","title":"Incident","read":true,"write":false,
                   "granted_by":[{"group":"Service Desk","level":"read"}]},
                  {"api":"change","title":"Change","read":true,"write":true,
                   "granted_by":[{"group":"Change Managers","level":"write"}]}]}""");
        return AccessTools.AccessDecision.decide(me, api, level);
    }

    @Test
    void readGrantedWriteDenied() throws Exception {
        assertThat(decide("incident", "read")).containsEntry("allowed", true);
        var denied = decide("incident", "write");
        assertThat(denied).containsEntry("allowed", false);
        assertThat((String) denied.get("next_step")).contains("write access to the Incident API");
    }

    @Test
    void writeGrantShowsGroup() throws Exception {
        assertThat(decide("change", "write")).containsEntry("allowed", true)
                .extractingByKey("granted_by").asList().containsExactly("Change Managers (write)");
    }

    @Test
    void unknownApiIsDenied() throws Exception {
        assertThat(decide("problem", "read")).containsEntry("allowed", false);
    }
}
