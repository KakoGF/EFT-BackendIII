FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
ENV MAVEN_OPTS="-Xmx384m"
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
VOLUME /tmp
COPY --from=build /build/target/*.jar /app.jar
EXPOSE 8100
ENTRYPOINT ["java","-jar","/app.jar"]
