import de.florianreuth.baseproject.configureSourceSet

plugins {
    id("base.java")
    id("base.maven_publish")
    id("publishing.reposilite")
    id("publishing.maven_central")
    id("base.application")
    id("configuration.shaded_dependencies")
}

configureSourceSet("example")

dependencies {
    shadedDependencies(libs.rsyntaxtextarea)
    shadedDependencies(libs.flatlaf)
}
