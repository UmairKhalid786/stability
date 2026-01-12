# Compose Stability Checker

An open‑source Gradle setup for **generating, inspecting, and enforcing Jetpack Compose compiler stability** in CI.

This project shows how to:

* Generate Compose compiler **stability reports**
* Parse `stable` / `unstable` class information
* Fail the build when unstable classes are detected
* Wire everything into Gradle (`check`, CI, etc.)

The goal is to make **Compose stability visible and enforceable**, not something you only discover by accident.

---

## What problem does this solve?

Jetpack Compose relies heavily on **type stability** for performance.

If a class is inferred as `Unstable`, it can:

* Prevent recomposition skipping
* Increase recomposition cost
* Cause subtle performance regressions

The Compose compiler *does* generate stability reports — but:

* They are off by default
* They are easy to ignore
* They are not enforced

This project turns those reports into a **build gate**.

---

## What does the compiler generate?

When reports are enabled, the Compose compiler produces files like:

```kotlin
stable class MainActivity {
  <runtime stability> = Stable
}

unstable class GreetingData {
  unstable val list: List<String>
  <runtime stability> = Unstable
}
```

These live in:

```text
build/compose-reports/**/<module>-classes.txt
```

This project parses those files and fails the build if any class is runtime‑`Unstable`.

---

## Prerequisites

* Gradle (7.6+ recommended)
* Kotlin 1.9+ or 2.x
* Jetpack Compose

For Kotlin **2.0+**, this project uses the **Compose Compiler Gradle plugin** (recommended).

---

## Step 1: Enable Compose compiler reports

In your **module** `build.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

composeCompiler {
    reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
}
```

Now run:

```bash
./gradlew compileDebugKotlin
```

You should see:

```text
build/compose-reports/
└── app-classes.txt
```

This file is the input for the stability checker.

---

## Step 2: Add the stability check task

This project defines a custom Gradle task:

```kotlin
checkComposeStability
```

The task:

1. Reads all `*-classes.txt` files under `build/compose-reports`
2. Detects classes with `<runtime stability> = Unstable`
3. Writes a human‑readable report
4. Fails the build if any unstable classes are found

Example failure:

```text
❌ Unstable findings (1):
- GreetingData (runtime=Unstable)
```

---

## Step 3: Ensure reports are generated first

The stability check **must run after compilation**, otherwise reports won’t exist.

We enforce this ordering:

```text
compileDebugKotlin
   ↓
checkComposeStability
```

In Gradle terms:

```kotlin
checkComposeStability.dependsOn(compileDebugKotlin)
```

This guarantees the report is always fresh.

---

## Step 4: Run it

Run the task directly:

```bash
./gradlew checkComposeStability
```

Or as part of your normal verification:

```bash
./gradlew check
```

To verify ordering:

```bash
./gradlew checkComposeStability --dry-run
```

---

## Example output

Generated report:

```text
build/reports/stability/stability-check.txt
```

Contents:

```text
Compose Stability Check
Scanned reports:
- build/compose-reports/app-classes.txt

❌ Unstable findings (1):
- GreetingData (runtime=Unstable)
```

This makes CI failures actionable and debuggable.

---

## What about compose‑metrics?

`compose-metrics` is **optional**.

| Folder          | Required | Purpose                             |
| --------------- | -------- | ----------------------------------- |
| compose-reports | ✅ Yes    | Human‑readable stability info       |
| compose-metrics | ❌ No     | Performance / recomposition metrics |

This project focuses only on **stability correctness**, not performance tracking.

---

## CI usage

This setup works well as a CI gate:

* Prevents accidental introduction of unstable types
* Forces developers to consciously opt into instability
* Encourages better Compose architecture

Typical CI step:

```bash
./gradlew check
```

---

## Common reasons a class is Unstable

* Mutable collections (`MutableList`, `MutableMap`)
* Public `var` properties
* Types without stable equals/hashCode
* Generic types without stability guarantees

Fixes often include:

* Switching to `ImmutableList`
* Making properties `val`
* Adding `@Stable` or `@Immutable`

---

## Roadmap / ideas

Possible future extensions:

* Variant‑aware checks (Debug vs Release)
* KMP support
* Metrics‑based CI regression checks
* Baseline / allowlist support

---

## Contributing

PRs welcome!

Good areas to contribute:

* Better parsing
* Faster scanning
* JSON output
* Documentation improvements

---

**Compose stability is a performance feature.**

This project helps you treat it like one.
