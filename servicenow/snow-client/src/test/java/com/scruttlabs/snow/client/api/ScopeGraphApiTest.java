package com.scruttlabs.snow.client.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.scruttlabs.snow.client.SnowClientConfig;
import com.scruttlabs.snow.client.auth.BasicAuth;
import com.scruttlabs.snow.client.http.SnowHttpClient;
import com.scruttlabs.snow.client.model.DictionaryDto;
import com.scruttlabs.snow.client.model.Page;
import com.scruttlabs.snow.client.model.TableDto;

/** Response bodies follow TableService.tableJson / dictionaryJson in x_scopegraph. */
class ScopeGraphApiTest {

    private static final String TABLES = ScopeGraphApi.BASE + "/tables";

    private static final String TABLES_PAGE = """
            {"result": {
              "items": [
                {"node": {"key": "incident", "kind": "table", "table": "sys_db_object", "name": "incident",
                          "oob": true, "sys_id": "a3c5a3c5a3c5a3c5a3c5a3c5a3c5a3c5"},
                 "sys_id": "a3c5a3c5a3c5a3c5a3c5a3c5a3c5a3c5", "name": "incident", "label": "Incident",
                 "super_class": {"value": "b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1", "display": "Task", "table": "sys_db_object"},
                 "sys_scope": {"value": "global", "display": "Global", "table": "sys_scope"},
                 "sys_package": {"value": "c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2c2", "display": "Incident Management", "table": "sys_package"},
                 "is_extendable": true, "sys_updated_on": "2025-03-01 10:00:00 UTC",
                 "super_class_name": "task", "some_future_field": 1},
                {"node": {"key": "u_order", "kind": "table", "table": "sys_db_object", "name": "u_order", "oob": false},
                 "sys_id": "d4d4d4d4d4d4d4d4d4d4d4d4d4d4d4d4", "name": "u_order", "label": "Order",
                 "super_class": null, "sys_scope": {"value": "global", "display": "Global", "table": "sys_scope"},
                 "sys_package": null, "is_extendable": false, "sys_updated_on": null, "super_class_name": null}
              ],
              "page": {"offset": 0, "limit": 100, "returned": 2, "total": 2, "next_offset": null}
            }}
            """;

    private static final String FIELDS_PAGE = """
            {"result": {
              "items": [
                {"node": {"key": "sys_dictionary:e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5", "kind": "record",
                          "table": "sys_dictionary", "sys_id": "e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5", "name": "u_order.u_customer"},
                 "sys_id": "e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5e5", "name": "u_order", "element": "u_customer",
                 "column_label": "Customer",
                 "internal_type": {"value": "reference", "display": "Reference", "table": "sys_glide_object"},
                 "reference": {"value": "core_company", "display": "Company", "table": "sys_db_object"},
                 "max_length": "32", "mandatory": false, "active": true,
                 "sys_scope": {"value": "global", "display": "Global", "table": "sys_scope"}, "sys_package": null},
                {"sys_id": "f6f6f6f6f6f6f6f6f6f6f6f6f6f6f6f6", "name": "u_order", "element": "u_tags",
                 "internal_type": "glide_list", "reference": null}
              ],
              "page": {"offset": 100, "limit": 100, "returned": 2, "total": 250, "next_offset": 200}
            }}
            """;

    private WireMockServer server;
    private ScopeGraphApi api;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        SnowClientConfig config = new SnowClientConfig(URI.create(server.baseUrl()),
                Duration.ofSeconds(2), Duration.ofSeconds(5), 0);
        api = new ScopeGraphApi(new SnowHttpClient(config, new BasicAuth("u", "p"),
                SnowHttpClient.newObjectMapper(), SnowHttpClient.newHttpClient(config)));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void decodesATablePage() {
        server.stubFor(get(urlPathEqualTo(TABLES)).willReturn(okJson(TABLES_PAGE)));

        Page<TableDto> page = api.tables(TableQuery.all(), 0, 100);

        assertThat(page.page().total()).isEqualTo(2);
        assertThat(page.page().nextOffset()).isNull();
        assertThat(page.page().hasNext()).isFalse();
        assertThat(page.items()).hasSize(2);

        TableDto incident = page.items().get(0);
        assertThat(incident.name()).isEqualTo("incident");
        assertThat(incident.sysId()).isEqualTo("a3c5a3c5a3c5a3c5a3c5a3c5a3c5a3c5");
        assertThat(incident.superClassName()).isEqualTo("task");
        assertThat(incident.superClass().value()).isEqualTo("b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1b1");
        assertThat(incident.sysScope().display()).isEqualTo("Global");
        assertThat(incident.sysPackage().display()).isEqualTo("Incident Management");
        assertThat(incident.isExtendable()).isTrue();
        assertThat(incident.updatedAt()).isEqualTo(Instant.parse("2025-03-01T10:00:00Z"));
        assertThat(incident.isOob()).isTrue();
        assertThat(incident.node().isTable()).isTrue();

        TableDto order = page.items().get(1);
        assertThat(order.superClass()).isNull();
        assertThat(order.superClassName()).isNull();
        assertThat(order.sysPackage()).isNull();
        assertThat(order.updatedAt()).isNull();
        assertThat(order.isOob()).isFalse();
    }

    @Test
    void sendsFiltersAndPaging() {
        server.stubFor(get(urlPathEqualTo(TABLES)).willReturn(okJson(TABLES_PAGE)));

        api.tables(TableQuery.all().withScope("global").withNamePrefix("u_").withExtends("task"), 200, 50);

        server.verify(getRequestedFor(urlPathEqualTo(TABLES))
                .withQueryParam("scope", equalTo("global"))
                .withQueryParam("name_prefix", equalTo("u_"))
                .withQueryParam("extends", equalTo("task"))
                .withQueryParam("offset", equalTo("200"))
                .withQueryParam("limit", equalTo("50")));
    }

    @Test
    void decodesDictionaryEntriesWithReferenceTypedColumns() {
        server.stubFor(get(urlPathEqualTo(TABLES + "/u_order/fields")).willReturn(okJson(FIELDS_PAGE)));

        Page<DictionaryDto> page = api.tableFields("u_order", Set.of("reference", "glide_list"), 100, 100);

        server.verify(getRequestedFor(urlPathEqualTo(TABLES + "/u_order/fields"))
                .withQueryParam("type", equalTo("glide_list,reference")));
        assertThat(page.page().nextOffset()).isEqualTo(200);

        DictionaryDto customer = page.items().get(0);
        assertThat(customer.element()).isEqualTo("u_customer");
        assertThat(customer.columnLabel()).isEqualTo("Customer");
        assertThat(customer.internalTypeName()).isEqualTo("reference");
        assertThat(customer.referenceTable()).isEqualTo("core_company");
        assertThat(customer.active()).isTrue();

        DictionaryDto tags = page.items().get(1);   // internal_type as a bare string, no reference
        assertThat(tags.internalTypeName()).isEqualTo("glide_list");
        assertThat(tags.referenceTable()).isNull();
    }

    @Test
    void validatesArgumentsWithoutCallingTheInstance() {
        assertThatThrownBy(() -> api.tables(TableQuery.all(), 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.tables(TableQuery.all(), 0, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.tables(TableQuery.all(), -1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.tables(TableQuery.all().withExtends("Task;drop"), 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> api.tableFields("../sys_user", Set.of(), 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(server.getAllServeEvents()).isEmpty();
    }
}
