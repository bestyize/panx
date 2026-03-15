package xyz.thewind.panx

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PanxApplication

fun main(args: Array<String>) {
    runApplication<PanxApplication>(*args)
}
