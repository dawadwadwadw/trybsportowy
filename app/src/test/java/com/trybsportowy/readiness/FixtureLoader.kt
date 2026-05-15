package com.trybsportowy.readiness

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Loads the shared algorithm fixtures (`algorithm_v1_fixtures.json`) that must stay
 * byte-identical with the server's copy (CLAUDE.md §4.1.2). Test-only.
 */
data class FixtureFile(
    @SerializedName("algorithm_version") val algorithmVersion: String,
    @SerializedName("rounding_mode") val roundingMode: String,
    @SerializedName("fixtures") val fixtures: List<Fixture>
)

data class Fixture(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("input") val input: List<FixtureInput>,
    @SerializedName("expected") val expected: FixtureExpected
)

data class FixtureInput(
    @SerializedName("day_offset") val dayOffset: Int,
    @SerializedName("sleep_code") val sleepCode: String,
    @SerializedName("hrv_code") val hrvCode: String,
    @SerializedName("physical_load_code") val physicalLoadCode: String,
    @SerializedName("work_code") val workCode: String,
    @SerializedName("alcohol_code") val alcoholCode: String,
    @SerializedName("nutrition_code") val nutritionCode: String,
    @SerializedName("cns_drain") val cnsDrain: Int,
    @SerializedName("body_drain") val bodyDrain: Int
)

data class FixtureExpected(
    @SerializedName("base_score") val baseScore: Double,
    @SerializedName("readiness_score") val readinessScore: Double,
    @SerializedName("total_cns") val totalCns: Double,
    @SerializedName("total_body") val totalBody: Double,
    @SerializedName("overload_triggered") val overloadTriggered: Boolean,
    @SerializedName("d_code") val dCode: Double
)

object FixtureLoader {
    private const val RESOURCE = "/algorithm_v1_fixtures.json"

    fun load(): FixtureFile {
        val stream = FixtureLoader::class.java.getResourceAsStream(RESOURCE)
            ?: error("Fixture resource not found on test classpath: $RESOURCE")
        stream.bufferedReader().use { reader ->
            return Gson().fromJson(reader, FixtureFile::class.java)
        }
    }
}
