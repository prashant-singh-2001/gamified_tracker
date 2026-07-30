# build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS  builder
WORKDIR /app

# copy root parent POM + this service's POM first (Docker layer caching:
# dependency resolution only reruns if a pom.xml actually changes)
COPY pom.xml .
COPY contracts/pom.xml contracts/pom.xml
COPY activity-service/pom.xml activity-service/pom.xml

# Install the parent POM itself first (-N = non-recursive: build only this pom, ignore its
# <modules> — most of those directories aren't even copied into this image). Without this,
# `contracts` installs fine here (relativePath=../pom.xml resolves it from disk), but when
# activity-service's single-module reactor later resolves `contracts` as a dependency, Maven
# reads contracts' parent purely from the local repo — and gamified-tracker-parent was never
# actually installed there, only referenced from the filesystem. That resolution then fails.
RUN mvn -N install

# The shared contracts module must be installed into the local repo BEFORE this service's
# single-module reactor tries to resolve it. `mvn -f <module>/pom.xml` does not build siblings,
# and -am is a no-op without -pl, so an explicit install is the minimal fix.
COPY contracts/src contracts/src
RUN mvn -f contracts/pom.xml install -DskipTests

RUN mvn -f activity-service/pom.xml dependency:go-offline

# now copy source and build
COPY activity-service/src activity-service/src
RUN mvn -f activity-service/pom.xml clean package -DskipTests

RUN java -Djarmode=layertools -jar activity-service/target/*.jar extract

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

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --spider --quiet http://localhost:8081/actuator/health || exit 1

USER MyUser

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]