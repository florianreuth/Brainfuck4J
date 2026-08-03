import de.florianreuth.baseproject.core.configureApplication
import de.florianreuth.baseproject.core.configureShadedDependencies
import de.florianreuth.baseproject.core.configureSourceSet
import de.florianreuth.baseproject.setupProject
import de.florianreuth.baseproject.setupPublishing

plugins {
    id("de.florianreuth.baseproject")
}

setupProject()
setupPublishing()
configureApplication()

configureSourceSet("example")

val shade = configureShadedDependencies()

dependencies {
    shade("com.fifesoft:rsyntaxtextarea:4.0.0")
    shade("com.formdev:flatlaf:3.7.2")
}
