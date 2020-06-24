import pl.allegro.tech.build.axion.release.domain.hooks.HookContext

group = "net.transgene.mylittlebudget"
version = scmVersion.version

plugins {
    java
    kotlin("jvm") version "1.3.72"

    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("pl.allegro.tech.build.axion-release") version "1.12.0"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:5.0.0")
    implementation("com.google.api-client:google-api-client:1.30.9")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.30.6")
    implementation("com.google.apis:google-api-services-sheets:v4-rev612-1.25.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:0.20.0")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    jar {
        manifest {
            attributes["Main-Class"] = "net.transgene.mylittlebudget.tg.bot.MainKt"
        }
    }

    shadowJar {
        archiveFileName.set("${rootProject.name}-fat.jar")
    }

    scmVersion {
        tag.prefix = "v"
        tag.versionSeparator = ""

        hooks.pre(
            KotlinClosure1<HookContext, Unit>({
                if ("master" != scmPosition.branch) {
                    throw GradleException("Releases are only allowed on master branch!")
                }
            })
        )
    }
}
