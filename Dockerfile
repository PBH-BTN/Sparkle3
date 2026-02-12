FROM docker.io/bellsoft/liberica-runtime-container:jdk-all-25-musl
LABEL maintainer="https://github.com/PBH-BTN/Sparkle"
USER 0
ENV TZ=UTC
WORKDIR /app
COPY build/libs/sparkle-0.0.1-SNAPSHOT.jar /app/sparkle.jar

ENTRYPOINT ["java", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/app/data/oom.dump","-XX:+UseCompactObjectHeaders", "-XX:+UseZGC", "-XX:SoftMaxHeapSize=512M", "-XX:MaxRAMPercentage=90.0", "-XX:ZUncommitDelay=1","-XX:+UseStringDeduplication", "-XX:-ShrinkHeapInSteps",  "-XX:+UseContainerSupport", "-jar","sparkle.jar"]
