package com.acme.itsm.mcp.incident;

import com.acme.itsm.mcp.common.ToolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentRulesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void p1CannotBeResolved() throws Exception {
        var p1 = mapper.readTree("{\"number\":\"INC0012345\",\"priority_code\":\"1\",\"state_code\":\"2\"}");
        assertThatThrownBy(() -> IncidentRules.checkResolvable(p1))
                .isInstanceOf(ToolException.class)
                .satisfies(e -> {
                    ToolException te = (ToolException) e;
                    org.assertj.core.api.Assertions.assertThat(te.code()).isEqualTo("P1_RESOLVE");
                    org.assertj.core.api.Assertions.assertThat(te.kind()).isEqualTo(ToolException.Kind.BLOCKED);
                });
    }

    @Test
    void p3CanBeResolvedButNotTwice() throws Exception {
        var p3 = mapper.readTree("{\"number\":\"INC0012346\",\"priority_code\":\"3\",\"state_code\":\"2\"}");
        assertThatCode(() -> IncidentRules.checkResolvable(p3)).doesNotThrowAnyException();
        var resolved = mapper.readTree("{\"number\":\"INC0012346\",\"priority_code\":\"3\",\"state_code\":\"6\"}");
        assertThatThrownBy(() -> IncidentRules.checkResolvable(resolved)).hasMessageContaining("already");
    }

    @Test
    void reassignOnlyWithinOwnGroups() throws Exception {
        var groups = mapper.readTree("[{\"sys_id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"name\":\"Service Desk\"}]");
        assertThatCode(() -> IncidentRules.checkGroupMembership(groups, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> IncidentRules.checkGroupMembership(groups, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
                .isInstanceOf(ToolException.class).extracting("code").isEqualTo("GROUP_NOT_MEMBER");
    }
}
