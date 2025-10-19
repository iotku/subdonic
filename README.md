# Subdonic

Discord Bot for Subsonic compatible servers.

The bot is currently far from feature complete and considered experimental.

## Requirements

- Java 21
    - Gradle
- Discord Bot Token
  - "Message Content Intent" must be enabled in developer dev portal bot settings
- Subsonic Server (e.g. Navidrome, Airsonic)

## Running

Copy `.env-example` -> `.env` and fill with appropriate values for environment variables.

Then use `gradle run` to launch.

## Roadmap

### Done
- Join voice channels (`!join` while in a voice channel)
- Play the first search result of a song (`!play some song name`)
- Show Search Results (`!search`)
- Pick Specific Song by Id 
  - (use `!play #` after using !search, where # is the number of the result)
- Playback Queue (e.g. add multiple songs)

## Later
- Web interface for managing playback and settings
- Admin controls
- Usage stats & logging

## Thanks / Acknowledgements

Subdonic uses the following libraries

- https://github.com/Discord4J/Discord4J
- https://github.com/calne-ca/subsonic-java-client
- https://github.com/spring-projects/spring-framework
