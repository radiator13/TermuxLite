package com.termux.lite

import java.io.File

/**
 * Android runtime environment vars that stripped-down shells otherwise miss.
 * Without BOOTCLASSPATH/DEX2OATBOOTCLASSPATH, app_process-based tools such as
 * Shizuku rish fail silently because ART cannot assemble its runtime.
 */
object AndroidEnv {

    private const val ART_APEX = "/apex/com.android.art"
    private const val I18N_APEX = "/apex/com.android.i18n"

    /** Order mirrors AOSP init.environ.rc: core libs first, ICU4J third. */
    private val CORE_JARS = arrayOf(
        "$ART_APEX/javalib/core-oj.jar",
        "$ART_APEX/javalib/core-libart.jar",
        "$I18N_APEX/javalib/core-icu4j.jar",
        "$ART_APEX/javalib/okhttp.jar",
        "$ART_APEX/javalib/bouncycastle.jar",
        "$ART_APEX/javalib/apache-xml.jar"
    )

    /** Command-line-only framework jars that are NOT part of BOOTCLASSPATH. */
    private val TOOL_JAR_SKIP = Regex(
        "/(am|wm|bmgr|bu|abx|content|pm|svc|requestsync|uiautomator|monkey|input|settings|incident)\\.jar$"
    )

    /**
     * Updatable APEX modules whose javalib jars join the boot classpath.
     * Apps cannot enumerate /apex (readdir denied) nor read
     * apex-info-list.xml, but CAN list a named module's javalib/, so we
     * probe every AOSP module known to ship jars there. Missing one only
     * costs its classes (e.g. virt -> virtualization); unknown future
     * modules degrade gracefully since ART boots on core+framework alone.
     */
    private val APEX_MODULES = arrayOf(
        "adbd", "adservices", "appsearch", "art", "btservices", "cellbroadcast",
        "conscrypt", "healthfitness", "i18n", "media", "mediaprovider",
        "ondevicepersonalization", "os", "permission", "profiling", "rkpd",
        "remotekeyprovisioning", "sdkext", "statsd", "tethering", "uwb",
        "virt", "wifi"
    )

    /**
     * Tier 1 - ground truth. Android init exports BOOTCLASSPATH into the
     * environment of every process it spawns, including this app, and
     * /proc/self/environ is always readable for our own uid. Reading it
     * yields the device's exact, current boot classpath with zero
     * assumptions. Returns null when absent or implausibly short.
     */
    private fun inheritedVar(name: String): String? = try {
        File("/proc/self/environ")
            .readBytes()
            .toString(Charsets.ISO_8859_1)
            .split('\u0000')
            .firstOrNull { it.startsWith("$name=") }
            ?.substring(name.length + 1)
            ?.takeIf { it.split(":").size >= 3 && it.all { c -> c.code >= 32 } }
    } catch (_: Exception) {
        null
    }

    /**
     * Tier 2 - reconstruction. Core libs first (order mirrors AOSP
     * init.environ.rc, ICU4J third), then /system/framework, then every
     * probeable APEX javalib. LinkedHashSet keeps first occurrence so the
     * explicit core entries win over duplicates rediscovered under
     * art/i18n APEXes.
     */
    private fun reconstructedBootClasspath(): String {
        val parts = LinkedHashSet<String>()
        for (j in CORE_JARS) if (File(j).isFile) parts.add(j)
        try {
            File("/system/framework")
                .listFiles { f -> f.isFile && f.name.endsWith(".jar") }
                ?.sortedBy { it.name }
                ?.forEach { if (!TOOL_JAR_SKIP.containsMatchIn(it.absolutePath)) parts.add(it.absolutePath) }
        } catch (_: Exception) {
        }
        for (m in APEX_MODULES) {
            try {
                File("/apex/com.android.$m/javalib")
                    .listFiles { f -> f.isFile && f.name.endsWith(".jar") }
                    ?.sortedBy { it.name }
                    ?.forEach { parts.add(it.absolutePath) }
            } catch (_: Exception) {
            }
        }
        return parts.joinToString(":")
    }

    /**
     * Tier 3 - last resort. Even with nothing but the ART core jars,
     * app_process boots and simple tools like rish work.
     */
    private fun coreOnlyBootClasspath(): String =
        CORE_JARS.filter { File(it).isFile }.joinToString(":")

    fun bootClasspath(): String =
        inheritedVar("BOOTCLASSPATH")
            ?: reconstructedBootClasspath().takeIf { it.split(":").size >= 6 }
            ?: coreOnlyBootClasspath()

    private fun dex2oatBootClasspath(): String =
        inheritedVar("DEX2OATBOOTCLASSPATH")
            ?: CORE_JARS.filter { File(it).isFile }.joinToString(":")

    /** Extra env entries merged into every new terminal session. */
    fun sessionEnvExtras(): Array<String> = arrayOf(
        "ANDROID_DATA=/data",
        "ANDROID_ROOT=/system",
        "ANDROID_ART_ROOT=$ART_APEX",
        "ANDROID_I18N_ROOT=$I18N_APEX",
        "ANDROID_TZDATA_ROOT=/apex/com.android.tzdata",
        "BOOTCLASSPATH=${bootClasspath()}",
        "DEX2OATBOOTCLASSPATH=${dex2oatBootClasspath()}"
    )
}
