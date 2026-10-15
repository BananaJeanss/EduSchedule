# Widget styling screenshots

Captured by `WidgetStylingTest` on the Android 15 emulator from application commit `25f348d3413d4f6294737e1831b863a5acc0d27c`.

[Passing phone/Wear UI run and reports](https://github.com/BananaJeanss/EduSchedule/actions/runs/38820157180) · [Original screenshot artifact](https://github.com/BananaJeanss/EduSchedule/actions/runs/38820157180/artifacts/12012860561)

- `default-light.png` and `catppuccin-dark.png`: an in-app lesson card beside the actual widget RemoteViews inflated in an Android instrumentation host.
- `ocean-light-large-font.png`: actual widget RemoteViews with the Ocean light palette and 1.5× system font scale. Times remain complete; two full lesson rows fit.
- `compact-*.png`: all five modes at 180dp width, using emulator system colors and a dashed border. Next uses 100dp height; the other modes use 240dp. Subject ellipsizing is expected at compact widths.

All subjects, rooms and teacher labels are synthetic test fixtures. These are Android-rendered captures, not launcher home-screen captures or picker artwork. PNGs are retained unchanged from the emulator output.

Validation on the application commit: local `testDebugUnitTest lintDebug assembleDebug assembleRelease` passed after a clean app build; phone and Wear emulator suites passed. Physical launcher/device testing was not performed.
