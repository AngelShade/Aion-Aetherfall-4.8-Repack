# Companion/HTTP latency diagnosis — 4 October 2026

**PB-DIAG-PERF-001: investigated; exact incident cause and recurrence open.**
This is validation of a reported latency event, not a gameplay repair or a new
upstream subsystem. Latest installed baseline is Sorcerer strategy receipt
`backups/playerbots-recruitment-20261004-234841-695964`, retaining tank, custody,
spacing, composition and all prior mods. Next broad port remains PB-PORT-005B.
No installed JAR/config/client, loaded gameplay methods, characters, DB rows or
server lifecycle were changed. PID 24908 was already running.

## Confirmed event

`log/server_console.log:1857-1858` and `server_warnings.log:34-35` contain:

- 23:44:40.502 Europe/Bucharest: pool-1-thread-8,
  PlayerBotService lambda `0x000000009acec800`, 7072 ms.
- 23:44:40.514: InstantPool-4, Exchange, 6443 ms.

The measured intervals start approximately 23:44:33.430 and 23:44:34.071,
respectively. They overlap and end 12 ms apart. `ExecuteWrapper.execute` measures
`System.nanoTime` around `Runnable.run`, so these are elapsed-time delays including
lock, I/O and VM/scheduling waits; they do not prove seven seconds of AI CPU work.
No matching exception accompanies this pair. No further execution-time warning
was observed through the bounded follow-up sample and subsequent log inspection.

The exact lambda appears in later JFR samples with caller
`PlayerBotService.tick`, confirming the first warning belongs to the AI update,
not its separate periodic-save task. `Exchange` is the JDK HTTP server Runnable
`sun.net.httpserver.ServerImpl$Exchange`; the native web server installs
ThreadPoolManager as its executor. The warning does not reveal the URL/route.

## Native source and plausible coupling

Exact native sources: `commons/.../concurrent/ExecuteWrapper.java`,
`game-server/.../playerbot/PlayerBotService.java` (`startTasks`, synchronized
`tick` and `saveAll`), `services/PlayerBotHttpService.java` (`handle`) and
`services/MarketplaceService.java` (`HttpServer` startup/setExecutor).
Installed JDK bytecode confirms `ServerImpl$Exchange implements Runnable`.
This is an Aion/JDK runtime diagnosis; no upstream port algorithm is claimed.

Companion HTTP snapshot/actions and response sending currently hold the same
service monitor used by bot updates and saves. A slow AI update can therefore
delay a companion request, and a slow request/action/response can delay AI.
The paired timings are consistent with this coupling, but the original call
stack, monitor owner and HTTP route were not recorded. Database I/O, geodata,
client output/backpressure, scheduling and other work remain possibilities.
Do not label the root cause proven or disable synchronization/custody based on
these two warnings.

## Bounded current evidence

Read-only process inspection, thread dump, VM information, heap state and counters
are in `target/playerbots-latency-diagnosis`. The captured threads show no Java
deadlock or stuck PlayerBot monitor; the bot thread is waiting normally between
scheduled tasks. A CentralMarket worker was reading a native DB result, which
does not establish it caused the earlier incident. Heap use was about 1.2 GiB
under a 2.5 GiB maximum; no out-of-memory failure was observed.

There was no pre-existing JFR recording and no detailed GC log retaining the
incident. One bounded `settings=profile`, 45-second, max-32-MiB recording ran
23:57:24–23:58:09 Europe/Bucharest and ended automatically. It changes diagnostic
recording state only, never AI/character state or loaded gameplay methods.

Results from `profile-summary.json`:

- Four GC pauses: 6.7565 ms maximum, 14.2506 ms total.
- One 10.7335 ms monitor event inside JFR itself; no recorded long companion
  service contention. This does not rule out waits during the earlier incident.
- Longest captured socket read: 153.3198 ms on database port 3306; no multi-second
  socket event in this later window.
- Mean process CPU about 3.95% of total machine capacity; machine mean 39.91%.
- Bot sample stacks include supply eligibility and heal geodata/reach work.
  Sample counts are not exact method durations or proof of the earlier slow call.
- No repeat execution-time warning during this recording.

Artifacts: `latency-profile.jfr`, `profile-events.json`, `profile-summary.json`,
`threads-1.txt`, `vm-info.txt`, `heap-info.txt`, `perf-counters.txt`.
Summarizer: `client-mods/playerbots/analyze_latency_recording.py`.

## Remaining work and acceptance

The user was asked whether the companion window was open, a save/dismiss/summon
was occurring, or this happened during ordinary combat/following. Activity context
was not yet available when this record was written. The original seven-second
operation cannot be reconstructed conclusively from the retained logs.

If reproduced, capture the AI task phase plus contemporaneous HTTP route and
monitor owner before selecting a bounded repair; do not suppress the warnings,
guess at trade/DB corruption, loosen custody or restart automatically. No recurring
monitor or automation was created. Keep PB-DIAG-PERF-001 open for recurrence/root
cause, and all PB-VAL actual gameplay/full-port/item-ID investigations intact.
