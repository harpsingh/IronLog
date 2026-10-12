# IronLog

A clean, local-first workout tracker for Android. Kotlin · Jetpack Compose · Material3 · Room · MVVM / unidirectional data flow.
No account, no backend, no cloud: everything lives in a local SQLite database.

## Features
- **Exercise library** – 876 exercises seeded on first launch from `assets/exercises.json` (derived from
  [yuhonas/free-exercise-db](https://github.com/yuhonas/free-exercise-db), public domain) with muscles, equipment,
  step-by-step instructions and bundled thumbnails (`assets/exercises/*.jpg`, dumbbell and bodyweight moves).
  Search, filter by muscle, and add / edit / delete custom exercises.
- **Pre-loaded plan** – 3-day full-body dumbbell programme (Workout A & B) created for new users.
- **Routine builder** – sets, rep range, rest timer and ordering per exercise.
- **Active workout** – tap-to-complete sets, weights/reps auto-filled from your previous session, rest-timer
  banner (+ vibration and notification when rest ends), exercise instructions one tap away.
- **History & stats** – log, calendar, and Canvas charts: weight lifted per workout, workouts per week,
  estimated 1RM progress per exercise.
- **Backup** – export / restore `ironlog_backup.json` through the Storage Access Framework (Settings screen).

## Structure (`app/src/main/java/com/ironlog`)
| Package | Contents |
|---|---|
| `data/local` | Room entities (`Exercise`, `Routine`, `RoutineExercise`, `WorkoutLog`, `SetLog`), DAOs, database, `SeedCallback` |
| `data/repository` | `IronLogRepository` |
| `data/backup` | `IronLogBackup` model + `IronLogBackupCodec` (JSON import/export) |
| `domain` | `ActiveWorkoutManager` (session + rest timer), `StatsCalculator`, `RestTimerNotifier` |
| `di` | `AppContainer` (manual DI) |
| `ui` | Compose screens + ViewModels, theme, navigation (`IronLogRoot`) |

## Build
Open in Android Studio (Ladybug+), or run `./gradlew assembleDebug` with an Android SDK (API 35) installed.
Weights are in kg.

## Notes
- The in-progress workout is held in memory (it survives navigation but not process death).
- Restoring a backup replaces custom exercises, routines and history; the built-in exercise set is untouched.
