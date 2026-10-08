# Sample app

Offline harness for the `com.inqbarna.secretsigning` plugin. The plugin is taken from this repository as a composite build (`pluginManagement { includeBuild("../..") }`), so plugin edits apply on the next run with no publishing. Secrets come from `fake-secrets.json` (`inqbarna.secrets.localFile` in `gradle.properties`), so no AWS credentials are needed.

## Run

Use the system `gradle` from the repository root:

```
gradle -p samples/sampleapp generateSampleKeystores :app:signingReport
gradle -p samples/sampleapp :app:assembleDemoFreeRelease
```

`generateSampleKeystores` creates throwaway keystores in `keystores/` (git-ignored) that match `fake-secrets.json`. Delete `keystores/` and `app/build/secrets/` to start from scratch.

## Setup being exercised

- Dimensions: `env` (`demo`, `production`, `staging`) then `tier` (`free`, `paid`). `env` has priority over `tier`.
- Global block: custom key names `sample_store_pass`, `sample_alias_name`, `sample_alias_pass`.
- `demo`: own keystore and all three key names.
- `production`: only a keystore, the key names are inherited from the global block.
- `staging`: no keystore anywhere, so its release variants are disabled.
- `paid`: only sets `aliasNameKey` (`paid_alias_name`).

## Expected `signingReport` (release variants)

| Variant | Store | Alias |
|---|---|---|
| `demoFreeRelease` | `demo.jks` | `demo-alias` |
| `demoPaidRelease` | `demo.jks` | `demo-alias` (`env` wins over `tier`) |
| `productionFreeRelease` | `production.jks` | `sample-alias` |
| `productionPaidRelease` | `production.jks` | `paid-alias` |
| `stagingFreeRelease`, `stagingPaidRelease` | not present, logged as disabled | |

## Debugging the plugin

```
gradle -p samples/sampleapp :app:signingReport -Dorg.gradle.debug=true
```

Then attach a remote JVM debugger to port 5005 (IntelliJ: Run > Attach to Process, or a Remote JVM Debug configuration). Plugin code runs at configuration time, so set breakpoints in `SecretSigningPlugin`.

## Using real AWS

Remove `inqbarna.secrets.localFile` from `gradle.properties` (or override it, e.g. `-Pinqbarna.secrets.localFile=`), configure `secrets { secretName; regionName }` in `app/build.gradle.kts` and make sure `aws configure` credentials are available. Run `refreshSecrets` to re-download.
