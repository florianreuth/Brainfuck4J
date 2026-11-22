plugins {
    id("base.application")
    id("configuration.shaded_dependencies")
}

dependencies {
    shadedDependencies(projects.brainfuck4jLib)
}
