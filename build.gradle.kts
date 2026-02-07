import de.florianreuth.baseproject.configureApplication
import de.florianreuth.baseproject.configureExampleSourceSet
import de.florianreuth.baseproject.configureShadedDependencies
import de.florianreuth.baseproject.setupProject
import de.florianreuth.baseproject.setupPublishing

plugins {
    id("de.florianreuth.baseproject")
}

setupProject()
setupPublishing()
configureExampleSourceSet()
configureApplication()

val shade = configureShadedDependencies()

dependencies {
    shade("com.fifesoft:rsyntaxtextarea:3.6.1")
    shade("com.formdev:flatlaf:3.7")
}
