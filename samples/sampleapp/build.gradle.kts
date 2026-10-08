import java.io.File

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.inqbarna.secretsigning") apply false
}

// Throwaway keystores matching fake-secrets.json (git-ignored). Run once: gradle generateSampleKeystores
val keytool = File(System.getProperty("java.home"), "bin/keytool").absolutePath

fun keystoreTask(name: String, storePass: String, vararg aliases: String): TaskProvider<Exec> {
    val file = layout.projectDirectory.file("keystores/$name.jks").asFile
    return tasks.register<Exec>("generate${name.replaceFirstChar(Char::titlecase)}Keystore") {
        onlyIf { !file.exists() }
        doFirst { file.parentFile.mkdirs() }
        val commands = aliases.joinToString(" && ") { alias ->
            "'$keytool' -genkeypair -keystore '${file.absolutePath}' -storetype PKCS12 -storepass '$storePass' " +
                "-keypass '$storePass' -alias '$alias' -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=$name'"
        }
        commandLine("sh", "-c", commands)
    }
}

val demoKeystore = keystoreTask("demo", "demo-secret", "demo-alias")
val productionKeystore = keystoreTask("production", "sample-secret", "sample-alias", "paid-alias")

tasks.register("generateSampleKeystores") {
    group = "secrets"
    description = "Generates the keystores referenced by fake-secrets.json when missing"
    dependsOn(demoKeystore, productionKeystore)
}
