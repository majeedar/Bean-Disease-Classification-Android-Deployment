# CLAUDE.md

The full project specification is in AGENTS.md. Read it before every task and follow it.

@AGENTS.md

## Working agreement for Claude Code

- Work in **small phases** (see `docs/claude-code-prompts.md`). Do only the phase you are asked for; don't jump ahead to v2 features.
- **Before writing code**, briefly state the plan and which files you will create or change.
- **After every change**, run `./gradlew assembleDebug` (and `./gradlew test` once tests exist) and fix all errors before saying you are done. Use `gradlew.bat` on Windows.
- Look up the latest **stable** versions of Android/Kotlin libraries rather than relying on memory, and keep them in `gradle/libs.versions.toml`.
- Never change the model input contract (224×224, raw 0–255 RGB floats, NHWC). If predictions look wrong, suspect preprocessing first (rotation, crop, channel order, normalisation).
- Never add the `INTERNET` permission or any analytics, ads or crash-reporting SDK.
- All user-facing text goes in `res/values/strings.xml`.
- Do not modify `app/src/main/assets/bean_model.tflite` or `labels.txt`.
- When a phase is finished, summarise what was done, what you tested, and anything the owner must check manually on a real phone.
