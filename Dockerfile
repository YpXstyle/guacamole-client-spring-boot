FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY guacamole-common/pom.xml guacamole-common/
COPY guacamole-ext/pom.xml guacamole-ext/
COPY guacamole-core/pom.xml guacamole-core/
COPY starters/ starters/
RUN mvn dependency:go-offline -B -q
COPY . .
RUN mvn clean package -pl guacamole-core -am -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/guacamole-core/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
