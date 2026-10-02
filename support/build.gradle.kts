plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
    signing
}

group = "com.ticketrackr"
version = "0.3.0"

android {
    namespace = "com.ticketrackr.android"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

// Maven Central (com.ticketrackr:support-android): `./gradlew :support:publishReleasePublicationToBundleRepository`
// writes the signed release to support/build/central-bundle, which is zipped and uploaded at central.sonatype.com.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.ticketrackr"
            artifactId = "support-android"
            version = project.version.toString()
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("TicketRackr Support for Android")
                description.set("TicketRackr support inside your Android app: requests and reports with their forms, the conversation, files, the AI assistant and surveys, with no link out.")
                url.set("https://ticketrackr.com/docs/support-api#embedded-support")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("ticketrackr")
                        name.set("TicketRackr")
                        url.set("https://ticketrackr.com")
                    }
                }
                scm {
                    url.set("https://github.com/Ticketrackr/ticketrackr-android")
                    connection.set("scm:git:https://github.com/Ticketrackr/ticketrackr-android.git")
                }
            }
        }
    }
    repositories {
        maven {
            name = "bundle"
            url = uri(layout.buildDirectory.dir("central-bundle"))
        }
    }
}

// Signed with the release key named in ~/.gradle/gradle.properties (signing.gnupg.keyName), never kept in the repository.
if (providers.gradleProperty("signing.gnupg.keyName").isPresent) {
    signing {
        useGpgCmd()
        sign(publishing.publications["release"])
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.activity:activity:1.7.0")
    implementation("androidx.webkit:webkit:1.14.0")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in unit tests: the real one, for reading the page's events.
    testImplementation("org.json:json:20180813")
}
