plugins {
    kotlin("jvm") version "2.4.21"
    `java-library`
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

repositories {
    mavenCentral()
}

dependencies {
    // Exposed in the public API: FantasyFootballClient takes a Retrofit interface and the factory
    // hands callers an OkHttpClient.Builder to configure.
    api("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-jackson:3.0.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.3")
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.4")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.4.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.5.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.20")
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}

tasks.check {
    dependsOn("detekt", "ktlintCheck")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("espnff4j")
                description.set("Kotlin client for the ESPN Fantasy Football API")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
    repositories {
        // Set MAVEN_REPO_URL, MAVEN_REPO_USER and MAVEN_REPO_TOKEN to publish anywhere other than mavenLocal.
        System.getenv("MAVEN_REPO_URL")?.let { repoUrl ->
            maven {
                name = "remote"
                url = uri(repoUrl)
                credentials {
                    username = System.getenv("MAVEN_REPO_USER")
                    password = System.getenv("MAVEN_REPO_TOKEN")
                }
            }
        }
    }
}
