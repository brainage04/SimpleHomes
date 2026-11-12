# SimpleHomes todo

## Loader parity findings (2026-09-29)

From running the release NeoForge jar on a real NeoForge 26.2.0.41-beta server and client. Items marked *both loaders* come from shared code.

- [ ] Low: failures are plain white system messages, unlike the BrainageLib-styled mods (`HomeCommandRegistration.java:63-139`).
- [x] Low: `/homeof Nobody base` for a never-seen player says "nobody does not have a home named 'base'." instead of reporting an unknown player. Fixed: `/homeof` looks a typed name up only among the owners who shared that home with you, by the server's cached names (selectors still match online players), and answers any other name with "Nobody has not shared home 'base' with you.", the same answer as for a home that is not shared.
