# syk-dig-backend
[![Deploy to dev and prod](https://github.com/navikt/syk-dig-backend/actions/workflows/deploy.yml/badge.svg)](https://github.com/navikt/syk-dig-backend/actions/workflows/deploy.yml)

This project contains the application code and infrastructure for syk-dig-backend

## Technologies used
* Kotlin
* Spring boot
* Gradle
* Junit

## Requirements for developing the application
#### Java
Make sure you have the Java JDK 25 installed
You can check which version you have installed using this command:
``` bash
java -version
```
or install it: https://sdkman.io/

#### Docker
Make sure you have docker installed using this command:
You can check which version you have installed using this command:
``` bash
docker --version
```
or install it: https://docs.docker.com/engine/install/



## Getting started
### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run 
``` bash
./gradlew bootJar
```
or  on windows 
`gradlew.bat bootJar`

#### Creating a docker image
Creating a docker image should be as simple as
``` bash 
docker build -t syk-dig-backend .
```

#### Running a docker image
``` bash
docker run --rm -it -p 8080:8080 syk-dig-backend
```

### Upgrading the Gradle wrapper version to latest
``` bash
./gradlew :wrapper --gradle-version latest
```

### GraphQL
Generate GraphQL classes 
```
./gradlew generateJava
```

### Contact

This project is maintained by [CODEOWNERS](CODEOWNERS)

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/syk-dig-backend/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997)
