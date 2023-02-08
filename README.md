# syk-dig-backend
[![Deploy to dev and prod](https://github.com/navikt/syk-dig-backend/actions/workflows/deploy.yml/badge.svg)](https://github.com/navikt/syk-dig-backend/actions/workflows/deploy.yml)
This project contains the application code and infrastructure for syk-dig-backend

## Technologies used
* Kotlin
* Spring boot
* Gradle

#### Requirements

* JDK 17
* Docker


## Getting started
### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the GitHub Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/syfosm-common")
    }
}
```

`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:

```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.
See githubs guide [creating-a-personal-access-token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token) on
how to create a personal access token.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

``` bash
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

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

### Upgrading the gradle wrapper
Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

``` bash
./gradlew wrapper --gradle-version $gradleVersjon
```

### Contact

This project is maintained by [CODEOWNERS](CODEOWNERS)

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/syk-dig-backend/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997)