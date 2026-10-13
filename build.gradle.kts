plugins {
    id("publishing.florianreuth")
    id("base.application")
    id("configuration.shaded_dependencies")
}

configureSourceSet("example")

dependencies {
    shadedDependencies(libs.rsyntaxtextarea)
    shadedDependencies(libs.flatlaf)
}
