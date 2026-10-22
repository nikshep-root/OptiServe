FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/target/optiserve-backend-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 10000

ENTRYPOINT ["sh", "-c", "exec java -Duser.timezone=Asia/Kolkata -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
