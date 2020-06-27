import com.github.zafarkhaja.semver.Version
import org.eclipse.jgit.api.Git.open
import org.eclipse.jgit.lib.Constants.HEAD
import pl.allegro.tech.build.axion.release.ReleaseTask
import pl.allegro.tech.build.axion.release.domain.VersionIncrementerContext
import pl.allegro.tech.build.axion.release.domain.hooks.HookContext
import pl.allegro.tech.build.axion.release.infrastructure.di.GradleAwareContext
import java.util.regex.Pattern

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

        versionIncrementer = KotlinClosure1<VersionIncrementerContext, Version>({
            val versionConfig = GradleAwareContext.config(project)
            val repo = GradleAwareContext.create(project, versionConfig).repository()
            val jgit = open(projectDir)
            val jgitRepo = jgit.repository
            val latestCommitWithTag = repo.latestTags(Pattern.compile("v\\d+\\.\\d+\\.\\d+"))
            val commitsSinceLastRelease =
                jgit.log().addRange(
                    jgitRepo.resolve(latestCommitWithTag.commitId),
                    jgitRepo.resolve(HEAD)
                ).call()
                    .groupingBy { it.shortMessage.substringBefore(":").trimStart().toLowerCase() }
                    .eachCount()
            val minorIncrement = commitsSinceLastRelease["feat"] ?: 0
            val patchIncrement = commitsSinceLastRelease["fix"] ?: 0

            println("Found $minorIncrement new feature(s) and $patchIncrement bugfix(es) since latest release ${latestCommitWithTag.tags[0]} (${latestCommitWithTag.commitId})")
            if (minorIncrement == 0 && patchIncrement == 0) {
                println("Nothing to release. Skipping the task")
                throw StopExecutionException()
            }

            val currentVersion = this.currentVersion
            val major = currentVersion.majorVersion
            val minor = currentVersion.minorVersion + minorIncrement
            val patch = currentVersion.patchVersion + patchIncrement

            Version.forIntegers(major, minor, patch)
        })

        hooks.pre(
            KotlinClosure1<HookContext, Unit>({
                if ("master" != this.position.branch) {
                    throw GradleException("Releases are only allowed on master branch!")
                }
            })
        )
    }
}
