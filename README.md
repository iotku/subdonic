# Subdonic

Discord Bot for Subsonic compatible servers.

## Requirements

- Java 21
    - Gradle
- Discord Bot Token
  - "Message Content Intent" must be enabled in developer dev portal bot settings
- Subsonic Server (e.g. Navidrome, Airsonic)

## Running

Copy `.env-example` -> `.env` and fill with appropriate values for environment variables.

Then use `gradle run` to launch.

# Thanks / Acknowledgements

Subdonic uses the following libraries

- https://github.com/Discord4J/Discord4J
- https://github.com/calne-ca/subsonic-java-client
- https://github.com/spring-projects/spring-framework