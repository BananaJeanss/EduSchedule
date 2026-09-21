# EduSchedule design standards

EduSchedule should feel like a native Android app first: fast to scan, obvious to operate, and quiet when there is nothing useful to say.

## Product principles

- **Glanceable first.** A student should understand the next lesson, time, room, and schedule state in a few seconds.
- **UI before explanation.** Prefer hierarchy, icons, labels, and platform conventions over paragraphs explaining how the app works.
- **Progressive detail.** Keep the timetable compact; put lesson metadata and secondary actions in the lesson sheet.
- **Predictable navigation.** Preserve the selected date when jumping between class, teacher, and room schedules.
- **Offline is a first-class state.** Cached data stays useful, but it must not look freshly fetched when the app knows it is offline.
- **School-agnostic by design.** UI, screenshots, fixtures, and documentation must not depend on a real school or expose personal timetable data.

## Material 3

Use Jetpack Compose Material 3 components and behavior unless there is a concrete UX reason not to.

- Keep the app edge-to-edge and compatible with gesture and button navigation.
- Use Material dynamic color on supported Android versions, with the existing fallback schemes elsewhere.
- Use `MaterialTheme.colorScheme`, `MaterialTheme.typography`, and component defaults instead of one-off hardcoded styling.
- Prefer standard Material 3 components such as `TopAppBar`, `NavigationBar`, `Card`, `ModalBottomSheet`, `ListItem`, `FilterChip`, dialogs, snackbars, and progress indicators.
- Do not recreate a platform component merely to make it look unique.

## Icons

Use **official Google Material Symbols Rounded** vector drawables.

- Do not hand-draw replacement icons with `Canvas` when an appropriate Material Symbol exists.
- Do not add the deprecated Compose `material-icons-extended` library just for icons; vendor only the vector assets the app actually uses.
- Use 24dp symbols by default.
- Directional symbols should use the official auto-mirrored vector where provided.
- Interactive icon controls must retain Material touch targets; never shrink the tap target to the glyph size.
- Give actionable or state-bearing icons a useful content description. Decorative icons should use a null description.
- Never communicate a state by color alone. Offline, selected, warning, and error states need an icon, text, shape, or other non-color cue.
- Do not use emoji as application icons.

Vendored Material Symbols come from Google's `google/material-design-icons` repository and are licensed under Apache-2.0.

## Layout and spacing

Use a small, repeatable spacing vocabulary instead of arbitrary values.

- 4dp: tight internal separation
- 8dp: related controls and icon/text gaps
- 12dp: compact card/list rhythm
- 16dp: standard component padding
- 20dp: normal screen horizontal content inset
- 24dp: roomy card/sheet sections
- 28dp: exceptional large insets such as setup and empty states

Prefer adaptive width and scrolling behavior over squeezing content until labels become unreadable. Preserve readable lesson names and times on narrow screens.

## Typography and copy

- Use Material typography roles instead of manual font sizes.
- Use weight for hierarchy sparingly; most emphasis should come from the component and typography role.
- Use sentence case.
- Keep labels short and concrete: `Refresh timetable`, `Choose schedule`, `Add to calendar`.
- Avoid repeating the app name or explaining obvious controls.
- Status copy should say what is true now, not narrate implementation details.
- Error messages should state the problem and, when useful, the next action.

## Localization

- Put user-visible application copy in Android string/plural resources; do not add language conditionals in Compose or domain models.
- English is the default resource set. Estonian is the first translation, and new locales must keep resource keys and format arguments in parity.
- Persist stable language identifiers, never translated labels.
- Source-provided timetable content (subjects, teachers, rooms, classes, revision names) is data and must not be translated.
- Format weekday/month names with the active app locale rather than the process default.
- Background notifications, reminder actions, calendar descriptions, and export headers are part of the localized product surface too.
- Prefer plurals for count-bearing copy instead of manually concatenating singular/plural words.

## Navigation

The primary destinations are **Day**, **Week**, and **Browse**.

- Bottom navigation changes primary destination only.
- Back should behave like Android users expect and should not invent a parallel navigation stack.
- Tapping a lesson opens its detail sheet.
- Teacher, class, and room entries in the lesson sheet should open the exact linked schedule by entity ID, not by matching a display name.
- Entity jumps keep the lesson date and return to Day view.
- The saved home class is a preference; temporarily browsing another schedule must not overwrite it.

## Timetable cards and lesson details

- Time and subject are the primary card information.
- Room and teacher are secondary information.
- Group labels are tertiary and should not overpower the subject.
- Parallel group lessons may share a visual block, but each underlying lesson must remain independently inspectable.
- Lesson detail sheets may expose room, teacher, class, group, and calendar actions without making the timetable itself dense.
- Use list rows with a trailing navigation symbol when metadata is actionable.

## Loading, offline, empty, and error states

### Loading

- Keep already-cached content visible while refreshing whenever possible.
- Use compact progress indicators near the control or content being refreshed.
- Avoid full-screen loading states after usable cached content exists.

### Offline

- Show the Material Symbol `cloud_off` plus an explicit `Offline` label when the current snapshot is cached because the network fetch failed.
- Include the saved timestamp where space allows.
- In Week view, mark individual offline day snapshots because different days may have different cache/network states.
- Cached data must remain browsable and lesson links must continue to work.

### Empty

- Empty copy may have some personality, but it must still clearly mean “no published lessons.”
- Do not imply a holiday or cancellation unless the data actually says so.

### Error

- Preserve a known-good cached snapshot rather than replacing it with invalid network data.
- Give a retry action when retrying can reasonably help.

## Accessibility

- Maintain readable contrast through Material theme tokens.
- Use content descriptions for icon-only actions and meaningful status icons.
- Keep touch targets at least as large as Material component defaults.
- Do not rely on color, motion, or position alone to convey meaning.
- Avoid unnecessarily small text.
- Respect system theme and dynamic-color preferences.

## Motion

Use Material component motion/default transitions. Add custom motion only when it clarifies state or navigation; never make timetable access wait on decorative animation.

## Privacy and data

- Do not add analytics or advertising.
- Do not require an EduPage login for the public timetable reader.
- Do not commit real student names, credentials, authenticated responses, or private timetable data.
- Parser/UI regression fixtures must be synthetic and sanitized.

## Review checklist

For user-visible changes, verify:

- the component follows Material 3 conventions;
- icons are official Material Symbols Rounded;
- dynamic light/dark themes remain readable;
- edge-to-edge and system navigation still work;
- offline/cached behavior is clear;
- Day, Week, and Browse navigation remain predictable;
- actionable controls have accessible semantics;
- UI copy is necessary and concise;
- narrow phone layouts remain usable;
- relevant unit/UI tests and screenshots are updated.
