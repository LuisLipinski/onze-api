FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre-noble
WORKDIR /app

RUN useradd --system --uid 10001 onze
COPY --from=build --chown=onze:onze /workspace/target/onze-api-0.0.1-SNAPSHOT.jar /app/onze-api.jar

USER onze
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/onze-api.jar"]
