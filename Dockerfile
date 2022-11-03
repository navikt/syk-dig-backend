FROM navikt/java:17
COPY build/libs/*-plain.jar app.jar
ENV JAVA_OPTS='-Dlogback.configurationFile=logback.xml'