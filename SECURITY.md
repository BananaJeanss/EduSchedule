# Security Policy

## Supported versions

Security fixes are applied to the latest released version and the current `main` branch.

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability.

Use GitHub's private vulnerability reporting flow if the repository shows **Report a vulnerability** under the Security tab. If that option is unavailable, contact the maintainer privately through the GitHub profile associated with this repository before sharing details publicly.

Include:

- the affected version or commit;
- a clear description of the issue;
- minimal reproduction steps;
- the expected security impact;
- whether user data, signing material, or update integrity could be affected.

Do not include real student names, private timetable contents, school account credentials, access tokens, signing keys, or other unnecessary personal data.

## Scope

Especially relevant areas include:

- EduPage host validation and network requests;
- cache/file handling;
- exported Android components and intents;
- calendar/export handling;
- notification actions;
- release/update verification;
- GitHub Actions and release signing.

EduSchedule intentionally reads only public timetable data from a user-supplied `*.edupage.org` host over HTTPS. It does not support authenticated/private scraping.

## Disclosure

Please allow reasonable time for a fix and release before publishing vulnerability details. There is currently no bug bounty program.
