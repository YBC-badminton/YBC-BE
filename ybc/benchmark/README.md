# Application Mail Benchmark

This benchmark compares `POST /applications` response times when application mail delivery is synchronous vs asynchronous.

## Preconditions

- Run from the project root.
- Use the `benchmark` Spring profile.
- The benchmark profile uses:
  - H2 in-memory database
  - a stub mail sender
  - configurable mail delay via `app.mail.stub-delay-ms`

## k6 script

`benchmark/application-submission.js`

Example:

```powershell
k6 run .\benchmark\application-submission.js
```

## Suggested comparison

1. Start the app with synchronous mail delivery:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=benchmark --app.mail.application.async-enabled=false"
```

2. Run k6:

```powershell
k6 run .\benchmark\application-submission.js
```

3. Stop the app, then start it with asynchronous mail delivery:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=benchmark --app.mail.application.async-enabled=true"
```

4. Run k6 again with the same parameters and compare:
  - `http_req_duration`
  - `http_req_waiting`
  - failure rate

## Notes

- The benchmark profile defaults to a `300ms` stub mail delay.
- Generated benchmark artifacts such as `*.log` and `*.json` in the `benchmark/` directory are local-only outputs and must not be committed.
- Increase `VUS` or `DURATION` with environment variables if needed:

```powershell
$env:VUS="40"
$env:DURATION="30s"
k6 run .\benchmark\application-submission.js
```
