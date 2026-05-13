FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

ARG GITHUB_USERNAME
ARG GITHUB_TOKEN

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw mvnw
COPY src src
COPY shared-model-local.jar shared-model-local.jar

RUN mkdir -p /root/.m2 && \
    printf '<settings><servers><server><id>github</id><username>%s</username><password>%s</password></server></servers></settings>' \
    "${GITHUB_USERNAME}" "${GITHUB_TOKEN}" > /root/.m2/settings.xml

RUN mvn install:install-file \
    -Dfile=shared-model-local.jar \
    -DgroupId=com.systeam \
    -DartifactId=shared-model \
    -Dversion=0.0.1-SNAPSHOT \
    -Dpackaging=jar \
    -q

RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
