FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

# Maven wrapper
COPY backend/.mvn/ .mvn/
COPY backend/mvnw ./mvnw

# Reactor Maven descriptors
COPY pom.xml ./pom.xml
COPY devlog-contracts/pom.xml devlog-contracts/pom.xml
COPY backend/pom.xml backend/pom.xml
COPY mcp-server/pom.xml mcp-server/pom.xml

RUN chmod +x mvnw \
    && ./mvnw -pl backend -am dependency:go-offline

# Sources required by the backend reactor build
COPY devlog-contracts/src/ devlog-contracts/src/
COPY backend/src/ backend/src/

RUN ./mvnw -pl backend -am package -DskipTests


FROM eclipse-temurin:21-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install --yes --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system devlog \
    && useradd --system --gid devlog --home-dir /app devlog

WORKDIR /app

RUN mkdir -p /var/lib/devlog-ai/workspaces \
    && chown -R devlog:devlog /var/lib/devlog-ai

COPY --from=build --chown=devlog:devlog \
    /workspace/backend/target/devlog-ai-backend-*.jar app.jar

USER devlog

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]