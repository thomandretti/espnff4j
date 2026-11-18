import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
    kotlin("jvm") version "2.4.21"
    `java-library`
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    // Builds the javadoc jar Maven Central requires from the KDoc.
    id("org.jetbrains.dokka") version "2.3.0"
    // Sources and javadoc jars, signing, POM checks and the Central Portal upload.
    id("com.vanniktech.maven.publish") version "0.37.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Exposed in the public API: the factory hands callers an OkHttpClient.Builder to configure. Keep
    // this in step with the OkHttp version Retrofit depends on.
    api("com.squareup.okhttp3:okhttp:4.12.1")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-jackson:3.0.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.23.0")
    // Retrofit's await() and the *Async futures both need it at runtime.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.7")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.4.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.1")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.20")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform { excludeTags("live") }
}

// Calls the real ESPN API, so it's never part of check. See EspnLiveSmokeTest for the variables it needs.
val liveTest by tasks.registering(Test::class) {
    description = "Runs the live smoke tests against ESPN."
    group = "verification"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("live") }
    // ESPN's answers change; never treat a previous run as up to date.
    outputs.upToDateWhen { false }
}

tasks.check {
    dependsOn("detekt", "ktlintCheck")
}

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"), sourcesJar = true))
    publishToMavenCentral()
    // Only when a key is configured, so publishToMavenLocal works without one.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
    coordinates(group.toString(), "espnff4j", version.toString())

    pom {
        name.set("espnff4j")
        description.set("Kotlin client, usable from Java, for the unofficial ESPN Fantasy Football API")
        url.set("https://github.com/thomandretti/espnff4j")
        inceptionYear.set("2026")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("thomandretti")
                name.set("Thom Andretti")
                url.set("https://github.com/thomandretti")
            }
        }
        scm {
            url.set("https://github.com/thomandretti/espnff4j")
            connection.set("scm:git:https://github.com/thomandretti/espnff4j.git")
            developerConnection.set("scm:git:ssh://git@github.com/thomandretti/espnff4j.git")
        }
        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/thomandretti/espnff4j/issues")
        }
    }
}
