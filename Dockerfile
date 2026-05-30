FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY guacamole-common/pom.xml guacamole-common/
COPY guacamole-ext/pom.xml guacamole-ext/
COPY guacamole/pom.xml guacamole/
COPY extensions/ extensions/
RUN mvn dependency:go-offline -B -q
COPY . .
RUN mvn clean package -pl guacamole -am -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/guacamole/target/*.jar app.jar

# Create non-root user for security
RUN addgroup -S guacamole && adduser -S guacamole -G guacamole
USER guacamole

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
