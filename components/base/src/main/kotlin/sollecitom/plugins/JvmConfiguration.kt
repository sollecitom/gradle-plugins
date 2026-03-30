package sollecitom.plugins

/** JVM arguments applied to main and test executions by convention plugins. */
object JvmConfiguration {
    /** Additional JVM arguments for production/main tasks. */
    val mainArgs: List<String> = listOf()
    /** Additional JVM arguments for test tasks. */
    val testArgs: List<String> = listOf()
}