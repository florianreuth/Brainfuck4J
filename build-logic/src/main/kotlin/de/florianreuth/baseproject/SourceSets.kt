package de.florianreuth.baseproject

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the

/**
 * Creates a source set that sees the main source set and is visible to it; its output is added to the jar.
 *
 * @param name the name of the new source set
 */
fun Project.configureLinkedSourceSet(name: String) {
    val sourceSets = the<SourceSetContainer>()

    val main = sourceSets.getByName("main")
    val newSet = sourceSets.create(name) {
        compileClasspath += main.output + main.compileClasspath
        runtimeClasspath += compileClasspath

        main.runtimeClasspath += output + runtimeClasspath
    }

    tasks.named<Jar>("jar") {
        from(newSet.output)
    }
}

/**
 * Creates a source set that sees the main source set; its output is not added to the jar.
 *
 * @param name the name of the new source set
 */
fun Project.configureSourceSet(name: String) {
    val sourceSets = the<SourceSetContainer>()

    val main = sourceSets.getByName("main")
    val sourceSet = sourceSets.create(name)

    sourceSet.compileClasspath += main.output + main.compileClasspath
    sourceSet.runtimeClasspath += sourceSet.compileClasspath
}
