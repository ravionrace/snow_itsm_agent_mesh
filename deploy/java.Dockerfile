# One Dockerfile for all seven Java services. The build stage compiles every module once (no build args there, so
# BuildKit caches and shares it across services); the final stage picks one jar with MODULE:
#   --build-arg MODULE=mcp-incident | mcp-problem | mcp-change | mcp-catalog | mcp-cmdb-kb | mcp-access | orchestrator
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY mcp-common/pom.xml mcp-common/
COPY mcp-incident/pom.xml mcp-incident/
COPY mcp-problem/pom.xml mcp-problem/
COPY mcp-change/pom.xml mcp-change/
COPY mcp-catalog/pom.xml mcp-catalog/
COPY mcp-cmdb-kb/pom.xml mcp-cmdb-kb/
COPY mcp-access/pom.xml mcp-access/
COPY orchestrator/pom.xml orchestrator/
# Dependencies first, so source changes don't re-download them (inter-module deps resolve in the package step).
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline -DskipTests || true
COPY mcp-common/src mcp-common/src
COPY mcp-incident/src mcp-incident/src
COPY mcp-problem/src mcp-problem/src
COPY mcp-change/src mcp-change/src
COPY mcp-catalog/src mcp-catalog/src
COPY mcp-cmdb-kb/src mcp-cmdb-kb/src
COPY mcp-access/src mcp-access/src
COPY orchestrator/src orchestrator/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
 && mkdir /jars && for m in mcp-incident mcp-problem mcp-change mcp-catalog mcp-cmdb-kb mcp-access orchestrator; do \
      cp "$m"/target/"$m"-*.jar /jars/"$m".jar; done

FROM eclipse-temurin:17-jre
ARG MODULE
RUN useradd --system --uid 10001 app
COPY --from=build /jars/${MODULE}.jar /app/app.jar
USER 10001
# Memory flags come from JAVA_TOOL_OPTIONS (see the compose files); this default suits a dedicated container.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
