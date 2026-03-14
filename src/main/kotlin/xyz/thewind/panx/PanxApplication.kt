package xyz.thewind.panx

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PanxApplication

fun main(args: Array<String>) {
    runApplication<PanxApplication>(*args)
}
