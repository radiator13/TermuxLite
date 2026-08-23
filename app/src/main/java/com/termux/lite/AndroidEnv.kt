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

    private val DEX2OAT_JARS = CORE_JARS

    /** Command-line-only framework jars that are NOT part of BOOTCLASSPATH. */
    private val TOOL_JAR_SKIP = Regex(
        "/(am|wm|bmgr|bu|abx|content|pm|svc|requestsync|uiautomator|monkey|input|settings|incident)\\.jar$"
    )

    /** Updatable APEX modules whose javalib jars join the boot classpath when present. */
    private val APEX_MODULES = arrayOf(
        "conscrypt", "media", "mediaprovider", "statsd", "permission", "sdkext",
        "wifi", "tethering", "adservices", "ondevicepersonalization", "healthfitness",
        "btservices", "uwb", "cellbroadcast", "remotekeyprovisioning", "rkpd"
    )

    fun bootClasspath(): String {
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
        val serviceArt = File("$ART_APEX/javalib/service-art.jar")
        if (serviceArt.isFile) parts.add(serviceArt.absolutePath)
        return parts.joinToString(":")
    }

    private fun dex2oatBootClasspath(): String =
        DEX2OAT_JARS.filter { File(it).isFile }.joinToString(":")

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
