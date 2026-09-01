plugins {
    java
    `maven-publish`

    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("com.diffplug.spotless") version "8.4.0"
    id("net.ltgt.errorprone") version "5.1.0"
}

group = providers.environmentVariable("GROUP").orElse("com.github.aliceblackrose").get()
version = providers.environmentVariable("VERSION").orElse("1.0.2fa").get()

val pluginVersion = version.toString()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    withSourcesJar()
}

paperweight.reobfArtifactConfiguration =
    io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

dependencies {
    paperweight.paperDevBundle("26.1.2.build.+")

    errorprone("com.google.errorprone:error_prone_core:2.49.0")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = providers.environmentVariable("ARTIFACT").orElse("osmium").get()

            pom {
                name.set("Osmium")
                description.set("A clean-room, ModelEngine-style Blockbench renderer for Paper Minecraft servers.")
                url.set("https://github.com/aliceblackrose/osmium")

                licenses {
                    license {
                        name.set("GNU General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/gpl-3.0.html")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/aliceblackrose/osmium.git")
                    developerConnection.set("scm:git:ssh://git@github.com/aliceblackrose/osmium.git")
                    url.set("https://github.com/aliceblackrose/osmium")
                }
            }
        }
    }
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }

    format("misc") {
        target("*.md", "*.yml", "*.yaml", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)

    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all",
        ),
    )
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"

    inputs.property("version", pluginVersion)

    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}
