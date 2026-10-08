# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Gradle plugin artifact (`com.inqbarna:secretsigning`, version set in `build.gradle.kts`) that fetches secrets from **AWS Secrets Manager** at configuration time and exposes them to Android builds. One jar ships two plugins, both declared in the `gradlePlugin {}` block of `build.gradle.kts`:

| Plugin id | Class | Extension |
|---|---|---|
| `com.inqbarna.secrets` | `com.inqbarna.secrets.SecretsPlugin` | `secrets { secretName; regionName }` on the project |
| `com.inqbarna.secretsigning` | `com.inqbarna.secretsigning.SecretSigningPlugin` | `secretSigning { ... }` on `android { }` and on each `productFlavors` entry |

`README.md` is the user-facing documentation (setup, DSL, expected secret JSON shape). Keep its version numbers and DSL examples in sync with `build.gradle.kts` and the extension interfaces when either changes.

## Build & commands

Use the system `gradle` (the wrapper pins Gradle 9.3.1). JVM toolchain 17, Kotlin 2.2.

- Compile: `gradle assemble`
- Tests: `gradle test` runs the unit tests and the TestKit functional tests (`src/test/.../functional`). Functional tests need an Android SDK (`ANDROID_HOME`, `ANDROID_SDK_ROOT` or `~/Library/Android/sdk`) and are skipped without one. AGP is on the plugin-under-test classpath through the `testPluginClasspath` configuration. `gradle slowTest` runs the `@Tag("slow")` tests, which assemble real projects. Single test: `gradle test --tests 'com.inqbarna.SomeTest.someMethod'`
- Publish to the Gradle Plugin Portal: `gradle publishPlugins`. The `signing` block uses `useGpgCmd()`, so a working local `gpg` setup is required for signed publications.

AGP is a `compileOnly` dependency (`com.android.tools.build:gradle-api`). The consuming Android project supplies AGP at runtime, so only use AGP APIs that exist in the AGP versions the plugin targets.

## Architecture

**Secret fetching (`secrets/SecretFetcher.kt`).** `SecretFetcher` is the seam for where the flat `Map<String, String>` comes from, with two implementations:
- `AwsSecretFetcher` calls `getSecret<T>(secretName, regionName)` in `secretsigning/AWSFetch.kt`. It builds a `SecretsManagerClient` using the AWS SDK v2 default credential chain (in practice the user's `aws configure` profile), reads the secret's string value and decodes it with kotlinx.serialization. Binary secrets are rejected. Every AWS failure is wrapped in a `GradleException` with a user-facing hint.
- `LocalFileSecretFetcher` reads a JSON file with the same shape, ignoring name and region. `SecretsPlugin` selects it when the Gradle property `LOCAL_SECRETS_FILE_PROPERTY` (`inqbarna.secrets.localFile`, resolved against the root project dir) is set and not blank, and then sets the `secretName` convention to `"local"`.

The selected fetcher is stored in `SecretExtensionImpl.fetcher` and is used by both `secrets[...]` and `refreshSecrets`.

**Secrets plugin (`secrets/`).**
- `SecretExtensionImpl` is created through `extensions.create(SecretsExtension::class, "secrets", ...)`, so Gradle generates the abstract `Property`/`RegularFileProperty` members. `regionName` defaults to `eu-west-1`.
- Secrets are cached as a flat `Map<String, String>` JSON in `build/secrets/secrets.json`, so `clean` drops the cache.
- `secrets["key"]` returns a lazy, memoized `Provider<String>`. When resolved, it downloads the file if it's missing, under a global lock. If the key isn't in a cached file, it re-downloads once before failing, because the server has no versioning to check against.
- The `refreshSecrets` task (group `secrets`, never up to date) always re-downloads the file.
- Consumers must resolve providers no earlier than `afterEvaluate` or `androidComponents.finalizeDsl`, because `secretName` has to be configured first.

**Secret signing plugin (`secretsigning/`).**
- It applies `SecretsPlugin` itself, then reacts to `com.android.base`. It expects an *application* project and looks up `ApplicationAndroidComponentsExtension`.
- A `DslExtension` named `secretSigning` (`SecretSigningPlugin`) is registered with `SecretSigningExtensionImpl` on the project (`android { }`) and on each product flavor. Its properties name the *keys* inside the secret (`keystorePassKey`, `aliasNameKey`, `aliasPasswordKey`), and `keystoreFile` names the keystore file.
- Kotlin DSL only gets a generated accessor for the global `android { secretSigning { } }`. Inside a flavor, that outer accessor is still in scope and silently configures the global block. `SecretSigningDsl.kt` ships `ProductFlavor.secretSigning(Action)`, which users import so the short syntax targets the flavor. The alternative is `extensions.configure<SecretSigningExtension>("secretSigning")`. `shorthandSyntaxWithImportedFlavorHelper` covers it.
- The extension has no conventions, so unset fields stay unset. Each block is snapshotted as a `SigningSpec`, and `SecretSigningExtensionImpl.merge` applies the defaults (`store_pass`, `alias_name`, `alias_pass`). Merge is field-wise: flavors in priority order, then the global block, then defaults. The result is a `MergedSigningConfig`, whose `isValid()` / `reportMissingFields()` check `keystoreFile`.
- `finalizeDsl` builds the combinations with `flavorCombinations`, in `flavorDimensions` order (which is AGP's priority). It creates one signing config per distinct `MergedSigningConfig` and records it in `signingByFlavors`, keyed by the set of flavor names.
- `beforeVariants` enables a release variant only if its flavor combination has a signing config.
- `onVariants` calls `variant.signingConfig.setConfig(...)` for those variants.

**Gotchas**
- Secrets are resolved at configuration time and download over the network, so configuration-cache and offline behavior depend on the cached `secrets.json`.

## Sample app

`samples/sampleapp` is an offline harness. Its `settings.gradle.kts` uses `pluginManagement { includeBuild("../..") }`, so it builds against this repository's plugin. `inqbarna.secrets.localFile=fake-secrets.json` in its `gradle.properties` replaces AWS. Run `gradle -p samples/sampleapp generateSampleKeystores :app:signingReport` (keystores are git-ignored and generated from `fake-secrets.json`). To debug the plugin, add `-Dorg.gradle.debug=true` and attach a debugger to port 5005. See `samples/sampleapp/README.md`.
