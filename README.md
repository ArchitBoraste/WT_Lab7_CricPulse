# CricPulse: Real-Time Cricket Score Management System

A live cricket scoring platform. A **Spring Boot** backend simulates and processes ball-by-ball match data and serves it over **REST** and **WebSocket (STOMP)**. A **React** dashboard shows live scores, scorecards, commentary, charts and player statistics, updating the moment each ball is bowled.

```
┌──────────────────────── React (Vite) ────────────────────────┐
│ Dashboard · Match Centre · Stats · Player Profile · Control  │
│        REST (fetch)                 STOMP over WebSocket     │
└──────────┬───────────────────────────────────┬───────────────┘
           │ /api/**                           │ /ws  (/topic/matches, /topic/matches/{id})
┌──────────▼───────────────────────────────────▼───────────────┐
│ Controllers ─► MatchService / StatsService                   │
│                     │                                        │
│      MatchUpdateExecutor (per-match lock + transaction)      │
│        │             │                │               │      │
│  ScoringEngine  MatchLifecycle  SnapshotBuilder  LiveUpdatePublisher
│        ▲                              │                      │
│  SimulationEngine (@Scheduled, worker pool)   LiveScoreCache │
│        │                                                     │
│  JPA / Hibernate ─► H2 (swap for MySQL/PostgreSQL)           │
└──────────────────────────────────────────────────────────────┘
```

## Features

- **Live simulation feed**: every tick (2.5 s by default) each live match bowls one ball. Outcomes come from a probability model that weighs batter vs bowler skill, the phase of the innings (powerplay, middle, death overs) and the pressure of a chase.
- **A full scoring rules engine**: runs, wides, no-balls, byes and leg byes; strike rotation; overs and maidens; bowler quotas and no consecutive overs; six ways to be dismissed; all-out, overs-complete and target-chased endings; results ("won by 5 wickets (4 balls left)", "won by 23 runs", ties); player of the match.
- **Match centre**: live batters and bowler, partnership, this over, CRR/RRR, projected score, win probability, boundary/wicket banners, ball-by-ball commentary with over summaries, full scorecards (fall of wickets, did not bat, extras), Manhattan and worm charts.
- **Player statistics**: career batting and bowling figures, leaderboards (runs, wickets, strike rate, sixes, economy), recent performances. They are aggregated in SQL and include balls bowled seconds ago.
- **Control Room**: schedule matches (T10 / T20 / ODI), run the toss, pause or resume the simulation, or take manual control and score each ball yourself with a scoring pad. Every ball you enter is pushed to all viewers.
- **Self-running demo**: on startup the app seeds 8 fictional franchises (88 generated players), 4 completed matches, 3 matches in progress and upcoming fixtures. New fixtures keep being scheduled, so there is always cricket on.

## Running it

Requirements: **JDK 17+**, **Maven 3.9+**, **Node 18+**.

```bash
cd backend
mvn spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**. The Vite dev server proxies `/api` and `/ws` to Spring Boot on port 8080.

Other useful URLs:

| URL | What it is |
| --- | --- |
| http://localhost:8080/swagger-ui.html | Interactive API docs (OpenAPI) |
| http://localhost:8080/h2-console | Database console (JDBC URL `jdbc:h2:mem:cricpulse`, user `sa`) |
| http://localhost:8080/actuator/health | Health check |

Run the backend tests with `mvn test` (10 tests: scoring rules, consistency checks over full simulated matches, and the REST API).

**Single deployable (optional):** run `npm run build` in `frontend`, copy `frontend/dist/*` into `backend/src/main/resources/static/`, then run `mvn package` and `java -jar target/cricpulse-backend-1.0.0.jar`. Spring Boot then serves the UI at http://localhost:8080, and `SpaController` forwards client-side routes to `index.html`.

## REST API

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/matches/dashboard` | Live, upcoming and recent matches in one call |
| GET | `/api/matches?status=LIVE\|SCHEDULED\|COMPLETED&limit=` | Match summaries |
| GET | `/api/matches/{id}` | Full snapshot: summary, live state, scorecards, recent balls |
| GET | `/api/matches/{id}/summary` | Compact live score |
| GET | `/api/matches/{id}/scorecard` | Scorecards of each innings |
| GET | `/api/matches/{id}/commentary?page=&size=&innings=` | Paged ball-by-ball commentary (newest first) |
| POST | `/api/matches` | Schedule a match `{teamAId, teamBId, format, venue?, startTime?, autoSimulate?}` |
| POST | `/api/matches/{id}/start` | Toss and start `{tossWinnerId?, decision?: BAT\|BOWL}` |
| POST | `/api/matches/{id}/deliveries` | Record a ball (see below) |
| PATCH | `/api/matches/{id}/simulation` | `{paused?, autoSimulate?}` |
| GET | `/api/teams`, `/api/teams/{id}/players` | Teams and squads |
| GET | `/api/players?q=&teamId=`, `/api/players/{id}` | Player search and profile with career stats |
| GET | `/api/stats/leaderboard?limit=` | Leaderboards |
| GET | `/api/stats/overview` | Platform totals |

Recording a delivery:

```json
POST /api/matches/11/deliveries
{
  "runsOffBat": 0,          // 0-6, credited to the batter
  "extraType": "WIDE",      // NONE | WIDE | NO_BALL | BYE | LEG_BYE
  "extraRuns": 1,           // runs run on a wide/no-ball, or the bye/leg-bye count
  "wicket": true,
  "wicketType": "STUMPED",  // BOWLED | CAUGHT | LBW | RUN_OUT | STUMPED | HIT_WICKET
  "dismissedPlayerId": null, // non-striker, for run outs only
  "fielderId": null, "bowlerId": null, "commentary": null   // optional; chosen or generated if omitted
}
```

Requests that break the rules (for example, runs off the bat on a wide, an LBW off a no-ball, or the same bowler bowling twice in a row) return **409 Conflict** with a message, and the ball is not recorded.

## Real-time updates (WebSocket)

Connect with STOMP to `ws://localhost:8080/ws` and subscribe to:

- `/topic/matches`: the summary of any match that changes (used by the dashboard)
- `/topic/matches/{id}`: the full snapshot of one match after every ball (used by the match centre)

The React client keeps one shared connection, restores its subscriptions after a reconnect, and falls back to REST polling while the socket is down. The connection pill in the header shows which mode it is in.

## How it scales

- **Write once, read many.** Every change goes through `MatchUpdateExecutor`. It updates the match in one transaction, builds an immutable snapshot, writes it to `LiveScoreCache`, then broadcasts it. Reads (REST and page loads) are served from memory without touching the database, however many people are watching. Each viewer gets the update by push instead of polling.
- **Per-match concurrency.** Each match has its own lock, so the simulator and a human scorer can never interleave on the same match, while different matches are processed **in parallel** on a worker pool (`cricpulse.simulation.worker-threads`).
- **Cheap polling.** `ShallowEtagHeaderFilter` returns `304 Not Modified` when a polled response has not changed, and gzip compression is on.
- **Efficient queries.** Stats come from `GROUP BY` queries and are cached for 5 s. The commentary feed uses an indexed, denormalised `match_id` with pagination. Entity graphs avoid N+1 loading, and the tables are indexed on status, innings and player.
- **Ready to scale out.** To run several instances, replace H2 with PostgreSQL/MySQL (change `spring.datasource.*`), swap the in-memory simple broker for a STOMP broker relay (RabbitMQ/ActiveMQ) in `WebSocketConfig`, and move the snapshot cache to Redis. The publishers and controllers do not change.

## Configuration (`backend/src/main/resources/application.properties`)

| Property | Default | Meaning |
| --- | --- | --- |
| `cricpulse.simulation.enabled` | `true` | Run the live simulation |
| `cricpulse.simulation.seed-demo-data` | `true` | Seed teams, players and matches on startup |
| `cricpulse.simulation.ball-interval-ms` | `2500` | Time between balls |
| `cricpulse.simulation.skip-probability` | `0.2` | Chance a match skips a tick (so matches don't move in lock-step) |
| `cricpulse.simulation.min-upcoming-fixtures` | `3` | Fixtures kept on the schedule |
| `cricpulse.simulation.fixture-gap-seconds` | `240` | Gap between generated fixtures |
| `cricpulse.cors.allowed-origins` | `http://localhost:5173,...` | Origins allowed for REST and WebSocket |

## Project layout

```
backend/src/main/java/com/cricpulse
├── domain/       JPA entities: Team, Player, Match, Innings, BattingEntry, BowlingEntry, Delivery
├── repository/   Spring Data repositories and aggregate projections
├── service/      ScoringEngine, MatchLifecycle, SelectionPolicy, SnapshotBuilder,
│                 MatchUpdateExecutor, LiveScoreCache, LiveUpdatePublisher, MatchService, StatsService
├── simulation/   SimulationEngine (scheduler), BallOutcomeGenerator, MatchSimulator,
│                 FixtureGenerator, DataSeeder
├── dto/          Records sent to clients and request bodies
├── config/       WebSocket, CORS/ETag/worker pool/OpenAPI, simulation properties
└── web/          REST controllers and error handling
frontend/src
├── lib/          api.js (REST), live.js (STOMP and hooks), format.js
├── components/   MatchCard, LivePanel, Scorecard, Commentary, Charts, Common
└── pages/        Dashboard, MatchCentre, Stats, PlayerProfile, ControlRoom
```

All teams and players are fictional, and the match data is simulated.
