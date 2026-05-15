# Phase 4 — Networking Layer (procedure)

> Active-phase procedure. Canonical rules live in `/CLAUDE.md`. Do not start this phase until Phase 3 has passed its checkpoint and the user replied `next`.

---

## §9. Phase 4 — Networking Layer

Retrofit + OkHttp + Moshi, with auth interceptor, logging redactor, and DTOs matching the server contract from §2.

### §9.1 Dependencies

```kotlin
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")           // or 5.x if the project is on it
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")          // or KSP if the project uses it
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
```

(Use whichever DI / KSP/KAPT convention the project already uses. Do not introduce a new tool just for this.)

### §9.2 DTOs

`app/src/main/java/<pkg>/sync/api/dto/DailyReadinessDto.kt`:

```kotlin
@JsonClass(generateAdapter = true)
data class DailyReadinessDto(
    @Json(name = "date_timestamp") val dateTimestamp: Long,
    @Json(name = "tz") val tz: String,
    @Json(name = "sleep_code") val sleepCode: String,
    @Json(name = "hrv_code") val hrvCode: String,
    @Json(name = "physical_load_code") val physicalLoadCode: String,
    @Json(name = "work_code") val workCode: String,
    @Json(name = "alcohol_code") val alcoholCode: String,
    @Json(name = "nutrition_code") val nutritionCode: String,
    @Json(name = "cns_drain") val cnsDrain: Int,
    @Json(name = "body_drain") val bodyDrain: Int,
    @Json(name = "drain_tags") val drainTags: String  // JSON-encoded array, per server
)
```

Mirror the other DTOs (`ReadinessRecordDto`, `ComputedDto`, `SyncResponseDto`, `HealthDto`) exactly from §2.2. Use snake_case JSON keys via `@Json(name = ...)`. Field names in Kotlin are camelCase. **Never expose the DTO out of the `sync` package** — entities, not DTOs, cross into UI/ViewModel layers. Add a `toEntity()` / `fromEntity()` mapper layer in `SyncRepository`.

### §9.3 `ServerApi`

```kotlin
interface ServerApi {
    @POST("api/android/sync")
    suspend fun sync(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body records: List<DailyReadinessDto>
    ): Response<SyncResponseDto>

    @GET("api/android/readiness")
    suspend fun getReadiness(@Query("days") days: Int): List<ReadinessRecordDto>

    @GET("api/android/health")
    suspend fun health(): HealthDto
}
```

`Response<T>` only on `sync()` so the caller can inspect HTTP status (200 vs 207). The others throw `HttpException` on non-2xx, which is fine.

### §9.4 `AuthInterceptor`

```kotlin
class AuthInterceptor(private val secrets: SecretsStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val secret = secrets.getSecret() ?: return chain.proceed(original)
        val req = original.newBuilder()
            .addHeader("Authorization", "Bearer $secret")
            .build()
        return chain.proceed(req)
    }
}
```

### §9.5 `HeaderRedactor` / logging

In `NetworkModule`:

```kotlin
val logger = HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
            else HttpLoggingInterceptor.Level.BASIC
    redactHeader("Authorization")
    redactHeader("Idempotency-Key")
}
```

`redactHeader` is OkHttp 4+ native. The test asserts that for any request with `Authorization: Bearer xyz`, the recorded log output does NOT contain `xyz` and DOES contain a redaction marker (`██` or `<redacted>`).

### §9.6 `NetworkModule`

```kotlin
object NetworkModule {
    fun createApi(secrets: SecretsStore, baseUrl: String? = null): ServerApi {
        val url = (baseUrl ?: secrets.getServerUrl() ?: SecretsStore.DEFAULT_SERVER_URL)
            .let { if (it.endsWith("/")) it else "$it/" }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(secrets))
            .addInterceptor(loggingInterceptor())
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ServerApi::class.java)
    }
}
```

Used by `SettingsViewModel.testConnection(url, secret)` with overrides, and by `SyncRepository` with the saved values.

### §9.7 Manifest

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

If `targetSdk >= 28` and the user is on a self-signed LAN (192.168.55.106 path), Tailscale URL avoids this — but if a Network Security Config is needed for the LAN URL, add `app/src/main/res/xml/network_security_config.xml`. Default approach is **Tailscale hostname only**, which has a real LE cert and needs no NSC. Document this in `docs/MASTER_BLUEPRINT.md`'s appendix or here:

> The phone reaches the server via the Tailscale hostname (default `https://aiserver.tail198ba5.ts.net`). That hostname has a real Let's Encrypt cert. Direct LAN access via `https://192.168.55.106` requires trusting the server's local CA, which is out of scope.

### §9.8 Unit tests

`app/src/test/java/<pkg>/sync/api/AuthInterceptorTest.kt` — uses MockWebServer. Two tests:
1. With a saved secret, every request carries `Authorization: Bearer <secret>`.
2. Logging output for a request with `Authorization: Bearer SECRET` contains the redaction marker and does **not** contain the literal string `SECRET`.

`app/src/test/java/<pkg>/sync/api/CanonicalPayloadTest.kt` — asserts that `canonicalize(dtos)` produces deterministic, sorted-key JSON. (`canonicalize` itself lives in `SyncRepository.kt`; Phase 5 implements it, but you can stub it here.)

### §9.9 Checkpoint

```bash
# 1. App compiles
./gradlew assembleDebug

# 2. Networking tests pass
./gradlew test --tests "*AuthInterceptorTest*"
# expect: 2 tests passed

# 3. Manual "Testuj połączenie" works against the live server
# On device: open Settings, enter https://aiserver.tail198ba5.ts.net and a valid secret, tap Testuj.
# expect: "Połączono. Wersja: <sha>"
# Then enter a wrong secret, tap Testuj — expect "Błąd połączenia: 401 ..."

# 4. No Authorization leak in logs
adb logcat -d | grep -E "Bearer\s+[A-Za-z0-9]{10,}" || echo CLEAN
# expect: CLEAN

# 5. Phone is on Tailscale (sanity check)
# On device, with Tailscale active:
adb shell ping -c 1 aiserver.tail198ba5.ts.net 2>&1 | head -3
# expect: name resolves; ping may be blocked by tailnet ACL but should not be DNS_PROBE_FINISHED
```

Append to `CHANGELOG.md`. Wait for `next`.

---
