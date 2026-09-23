# Public EduPage data

EduSchedule reads the same public timetable RPC used by the EduPage timetable viewer for a school address supplied by the user. No school is built into the app, and no request is made until first-time setup is completed.

Typical public calls are:

```text
POST /timetable/server/ttviewer.js?__func=getTTViewerData
{"__args":[null,<school-year>],"__gsh":"00000000"}

POST /timetable/server/regulartt.js?__func=regularttGetData
{"__args":[null,"<timetable-id>"],"__gsh":"00000000"}
```

`__gsh` is the public no-session value used by the public viewer endpoint. The app never obtains private sessions or attempts to bypass login. Hosts are restricted to one valid `*.edupage.org` school name, HTTPS is required, redirects are rejected, and responses are bounded and time-limited.

The response uses an `r` envelope. Index data is read from `regular.timetables`; hidden entries are ignored. For a selected date, choose the greatest `datefrom <= date`, using the numeric timetable id as a deterministic tie-breaker. A future revision is never substituted for an older date.

Timetable data is under `dbiAccessorRes.tables`. Cards are joined to lessons, then subjects, classes, teachers, groups, rooms, periods, and bells. `days` and `weeks` are positional bit strings. Identifiers are opaque strings and may contain characters such as `*` or `-`.

## Times

Resolve times in this order: day-specific overrides first; then an explicit valid `H:mm-H:mm` range embedded in the published period `short`/`name`; then generic bell/base `starttime` / `endtime`. This is deliberate because some EduPage exports contain stale or misaligned generic structured times while the user-visible period label carries the intended published range. Some schools also publish long teaching blocks while `durationperiods` still reflects the editor's smaller underlying grid. Extending an already-long block can create phantom late endings, so EduSchedule only extends multi-period lessons when the visible base period is 60 minutes or shorter. Missing or non-increasing times remain unknown and are not exported to calendars.

## Groups

Split-group ids are de-duplicated at parse time, and visible labels are de-duplicated separately because different source ids can share a name. All distinct ids remain available for filtering. For class schedules, parallel split groups occupying the same published time slot are presented as one card with readable group options instead of several duplicate-looking lesson cards. Teacher and room schedules remain unmerged because those views represent different responsibilities/locations rather than student alternatives.

## Localization boundary

EduPage-provided names are source data rather than application copy. Subject, class, teacher, room, group, school, week-cycle and revision names are therefore shown exactly as published by the school and are not translated by EduSchedule. Application-owned labels, states, notifications and export metadata use Android localization resources.

## Cache policy

Index refresh: 30 minutes. Revision data: six hours. Manual refresh bypasses both. Foreground prefetch loads the selected week and keeps a bounded in-memory day cache so revisiting a loaded date is immediate. Background reminder rescheduling may also read the validated disk cache, including after reboot when network access is unavailable; it never fabricates lesson data. Disk cache size is bounded. Parsed payloads are validated before replacing a working snapshot, and `AtomicFile` preserves the prior copy if a write is interrupted.

## Limitations

The public regular timetable is not guaranteed to include substitutions, cancellations, holidays, or other live changes. Capability differs by school and can change independently of EduSchedule. Parser failures must fail visibly and preserve the last validated cache rather than inventing schedule data.

## Testing

The committed fixture is synthetic. It covers split groups, duplicate group ids, bell/day overrides, block-style periods, conventional short double periods, cycle weeks, and unknown times. Raw real-school responses are not committed.
