FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline

COPY src/ src/
RUN ./mvnw package -DskipTests


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
    /workspace/target/devlog-ai-backend-*.jar app.jar

USER devlog

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
