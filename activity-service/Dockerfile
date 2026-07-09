# build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS  builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

RUN java -Djarmode=layertools -jar target/*.jar extract

# run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S MyGroup && adduser -S MyUser -G MyGroup
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./
RUN chown -R MyUser:MyGroup /app

EXPOSE 8081

USER MyUser

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]