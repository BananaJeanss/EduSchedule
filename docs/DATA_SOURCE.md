# Public EduPage data

Inspected on 2026-09-18 at https://kunst.edupage.org/timetable/. Its JavaScript viewer calls two JSON RPC endpoints without authentication:

```text
POST /timetable/server/ttviewer.js?__func=getTTViewerData
{"__args":[null,2026],"__gsh":"00000000"}

POST /timetable/server/regulartt.js?__func=regularttGetData
{"__args":[null,"226"],"__gsh":"00000000"}
```

`__gsh` here is the public no-session request value accepted by the public viewer endpoint. The app never obtains private sessions or bypasses a login. Restrict hosts to a single valid `*.edupage.org` school name, require HTTPS, reject redirects, use bounded responses and timeouts. If the public schema stops working, show a useful failure and preserve validated cache.

The response uses an `r` envelope. Index data is in `regular.timetables`; ignore `hidden` entries. Choose the greatest `datefrom <= selected date`, using numeric timetable ID to resolve equal-date revisions. On inspection, 226 began 2026-09-14; 227 began 2026-09-21. `current.allow` was false. Do not silently use a future version or pretend the regular data includes daily substitutions.

Timetable data is in `dbiAccessorRes.tables`, with `id` and `data_rows`. Join `cards.lessonid` to lessons, then subjects, classes, teachers, groups and classrooms. Cards contain `days`/`weeks` bit strings; positions are ordered, not numeric masks. Preserve identifiers including `*` and `-`. Class groups marked `entireclass` cannot be hidden. A lesson's class IDs can also be recovered from groups.

Read period times from structured `starttime`/`endtime`, not localized labels (the real school has inconsistent labels). Apply the lesson/class/teacher bell override and day-specific overrides. End time for a multi-period lesson comes from its final period. Missing or non-increasing times remain unknown and cannot be exported as events.

## Cache policy

Index refresh: 30 minutes. Revision data: six hours. Manual refresh bypasses both. Foreground prefetch loads the selected week; background refresh is opt-in and covers the home schedule. Cache size is bounded to 24 JSON snapshots across schools. Payloads are parsed before replacing a snapshot and AtomicFile preserves old data if a write is interrupted. Explicit stale state is returned if a network refresh fails. Clearing app storage deletes the cache and preferences.

## Testing

The committed fixture is synthetic and models the inspected public schema, including split groups, bell overrides, day overrides, cycle weeks and unknown times. Raw real-school responses are not committed. The RPC is undocumented and can change independently of the app; parser failures must fail visibly rather than display invented lessons.
