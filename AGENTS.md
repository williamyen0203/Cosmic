# AGENTS.md

Context for AI coding agents working on **Cosmic**, a MapleStory v83 server emulator
(HeavenMS/OdinMS lineage). This is the server ("brain") that routes network traffic
between game clients; clients are external.

## Tech Stack

- **Java 21** (build targets release 21 — see Gotchas about JDK version).
- **Maven** via the `./mvnw` wrapper (`mvnw.cmd` on Windows). Not Gradle.
- **Netty 4.2** (networking), **HikariCP** + **MySQL Connector/J 9** + **jdbi3** + **Liquibase** (DB),
  **GraalVM JS** (runs the `scripts/` game logic), **Log4j2**/**slf4j** (logging),
  **yamlbeans** (parses `config.yaml`).
- **JUnit 5 + Mockito** for unit tests (`src/test/java`).
- Server entry point: `net.server.Server` (`mainClass` in `pom.xml`).

## Commands

```bash
# Build a fat jar (bundles deps via maven-assembly; does NOT include wz/ XML files)
./mvnw clean package

# Compile only / run tests
./mvnw compile
./mvnw test

# Run the server: launch net.server.Server from your IDE, OR
docker compose up            # first time / after code changes: docker compose up --build
```

- The server reads `config.yaml` from the **current working directory** at startup
  (`YamlConfig`), so run it from the repo root.
- DB setup: import `database/` schema into MySQL 8+; credentials live in `config.yaml`
  (`DB_HOST`/`DB_USER`/`DB_PASS`). Under `docker compose`, the `DB_HOST=db` env var in
  `docker-compose.yml` **overrides** the `config.yaml` host.

## Project Structure

```
src/main/java/
  net/          Networking: PacketProcessor (opcode→handler registry),
                net/opcodes/{RecvOpcode,SendOpcode}.java, net/server/channel/handlers/*
  client/       Player-side domain: Character, Client, Skill, BuffStat, etc.
  client/command/  In-game GM/player command system (see below)
  server/       World/map/life/quest logic: MapleMap, StatEffect, life/, maps/, quest/
  tools/        PacketCreator (builds every outgoing packet), utilities
  constants/    Static data incl. constants/skills/*.java (skill id constants)
  config/       YamlConfig + ServerConfig/WorldConfig (config.yaml models)
  provider/     wz data loading
  scripting/    GraalVM JS bridge for scripts/
src/test/java/  JUnit 5 tests
scripts/        ~1900 .js game-logic files (NPCs, quests, events, portals) run via GraalVM
wz/             Game data as .img.xml (Skill.wz, Mob.wz, Item.wz, ...). NOT bundled in jar.
database/       SQL schema/seed
config.yaml     Single server config file (see below)
```

## config.yaml wiring (how to add a server setting)

`config.yaml` has two roots: `worlds:` (list, → `config/WorldConfig.java`) and
`server:` (→ `config/ServerConfig.java`). **yamlbeans binds YAML keys to public fields
by exact name.** To add a server flag:

1. Add a `public boolean MY_FLAG;` (or int/String/double) to `config/ServerConfig.java`.
2. Add `MY_FLAG: false` under `server:` in `config.yaml` (name must match the field exactly).
3. Read it anywhere as `YamlConfig.config.server.MY_FLAG`.

Precedent flags: `USE_AUTOHIDE_GM`, `USE_FISHING_SYSTEM`, `USE_BUFF_EVERLASTING`
(the last one makes buffs effectively permanent via `Integer.MAX_VALUE` duration —
see `server/StatEffect.java`).

## Command system (in-game `@`/`!` commands)

- Commands live in `client/command/commands/gmN/` where **N is the required GM rank**
  (`gm0` = any player, `gm1`–`gm6` = escalating GM levels).
- Each command extends `client.command.Command`, sets its description in an
  **instance-initializer block** (`{ setDescription("..."); }` — no constructor), and
  implements `execute(Client c, String[] params)`.
- Register every new command in `client/command/CommandsExecutor.java`: add an `import`
  and an `addCommand("name", MyCommand.class)` call inside the matching
  `registerLvNCommands()` method.
- Wrap player-state mutations in `if (c.tryacquireClient()) { try { ... } finally { c.releaseClient(); } }`
  (see `ToggleExpCommand`). Message a player with `c.getPlayer().message("...")`.
- Apply a skill's buff to any player (regardless of whether they learned it) with
  `SkillFactory.getSkill(id).getEffect(maxLevel).applyTo(player)` (see `BuffMeCommand`).

## Domain gotchas

- **Player knockback-on-damage is client-side.** The server's `TakeDamageHandler`
  trusts client-reported damage/direction, deducts HP, and rebroadcasts `damagePlayer`
  to *other* clients (the victim is excluded). No packet field controls victim knockback.
  The only client-honored server lever is the `STANCE` buff (`BuffStat.STANCE`) —
  a probability (0–100) of resisting knockback. The warrior Stance skill (`1121002`)
  caps at 90%.
- **wz data is XML**, edited via HaRepacker "Private Server" export. Skill/mob/item
  numeric params (`prop`, `time`, etc.) live in `wz/*.wz/*.img.xml`.
- Config toggle `USE_BUFF_MOST_SIGNIFICANT` (true): when stacking the same BuffStat,
  the highest value wins rather than overwriting.

## Testing

- `./mvnw test` (JUnit 5). Existing tests cover packet (de)serialization, services,
  processors, script evaluation — NOT combat/broadcast flow. New combat/command
  behavior generally needs **manual in-game verification** (launch server, log in, test).

## Environment gotchas

- **JDK version mismatch is the #1 build failure here.** The project targets Java 21,
  but a common local `JAVA_HOME` points at an older JDK (e.g. Zulu 17), producing
  `invalid target release: 21`. `mvnw` manages Maven, **not** the JDK — set
  `JAVA_HOME` to a JDK ≥ 21 before building, e.g.:
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home ./mvnw clean package
  ```
- Dependencies resolve from an internal Nexus proxy; `-o` (offline) fails on a cold
  cache — build online the first time.
