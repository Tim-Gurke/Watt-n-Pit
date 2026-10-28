# Watt’n’Pit 1.6.6 – build verification

Source commit: 99f5f2ee2ba2e13c6164a9e3ca642d797939ee03
Source tree: 05c6e438efa1c1c237a6d5e62d5a37b85f97677f
Verified source CI: https://github.com/Tim-Gurke/Watt-n-Pit/actions/runs/40718756105
CI build job: 122923554372
Unsigned artifact ID: 12829807123
Unsigned artifact ZIP SHA-256: 009b933acdcb103a3f38aaae2c803246c9a3dbef2d0f2abf2403a5971bde1919
Unsigned APK SHA-256: a90345aaf043e6fc33d76fb5097e3f7c4ac660975d5ae6ce59d6e6cac3a19dee
Signed APK SHA-256: 9a1d2e45312452d4b8fa6c050bde0bef94ebf7f60e7d09243483f658224bff8b
Signed APK size: 605043 bytes
Package: de.wattpit.app
Version: 1.6.6 (55), min SDK 26, target SDK 35
Update certificate SHA-256: 0e6e04bc2b50990a8f28bebae31dea596c2cd2f8563f1505aaaafc07277faeb2
APK Signature Scheme v2: verified

The release APK was signed locally from the exact CI artifact above using the existing private update key. Private signing material is not part of the repository. The publication commit changes .release files only and has the verified source commit as its only parent.

Validation: 161 JavaScript calculation/configuration tests; Android unit tests, lint, debug and release builds; native SQLite/archive/learning proofs; real authenticated encrypted TCP handover tests; browser tests including roller owner/lock/log display, per-circuit loss factors and forecast learning. The source CI reports are attached to the run above. Matching ScootPit 1.6.14 source: e45639004bcea5fb7b3d8308a528434dae14d3dc, with signed Android build and emulator UI checks on API 26, 33 and 35.

Physical G5/S20/Joyor BLE handover, hotspot behaviour and device-specific Android recovery have not been exercised by these software/emulator checks. Confirmed fresh frames, scoped ownership, timeouts and BMS acknowledgements remain required at runtime.
