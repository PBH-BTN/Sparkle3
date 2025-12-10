FROM docker.io/bellsoft/liberica-runtime-container:jdk-all-25-musl
LABEL maintainer="https://github.com/PBH-BTN/Sparkle"
USER 0
ENV TZ=UTC
WORKDIR /app
COPY build/libs/sparkle-0.0.1-SNAPSHOT.jar /app/sparkle.jar

ENTRYPOINT ["java","-XX:+UseCompactObjectHeaders", "-XX:+UseZGC", "-XX:SoftMaxHeapSize=1024M", "-XX:MaxRAMPercentage=86.0", "-XX:ZUncommitDelay=1","-XX:+UseStringDeduplication", "-XX:-ShrinkHeapInSteps",  "-XX:+UseContainerSupport", "-Djava.security.egd=file:/dev/./urandom", "-jar","sparkle.jar"]
