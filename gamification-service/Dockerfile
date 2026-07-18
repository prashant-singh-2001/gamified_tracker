# build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS  builder
WORKDIR /app

COPY pom.xml .
COPY gamification-service/pom.xml gamification-service/pom.xml
RUN mvn -f gamification-service/pom.xml dependency:go-offline

COPY gamification-service/src gamification-service/src

RUN mvn -f gamification-service/pom.xml clean package -DskipTests

RUN java -Djarmode=layertools -jar gamification-service/target/*.jar extract

# run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
RUN addgroup -S MyGroup && adduser -S MyUser -G MyGroup
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./
RUN chown -R MyUser:MyGroup /app

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --spider --quiet http://localhost:8082/actuator/health || exit 1

USER MyUser

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]