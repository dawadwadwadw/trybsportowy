# Phase 3 — Settings + Secret Provisioning (procedure)

> Active-phase procedure. Canonical rules live in `/CLAUDE.md`. Do not start this phase until Phase 2 has passed its checkpoint and the user replied `next`.

---

## §8. Phase 3 — Settings + Secret Provisioning

A minimal Settings screen that lets the user paste in the server URL and the Bearer secret, plus the underlying `SecretsStore`. This is the **one** UI exception to "UI is out of scope" — without it, the app can't reach the server.

### §8.1 Dependencies

Add to `app/build.gradle.kts` (or `.groovy`):

```kotlin
dependencies {
    // EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Or whatever the project's existing Compose Material version is; use the same one.
}
```

### §8.2 `SecretsStore`

`app/src/main/java/<pkg>/settings/SecretsStore.kt`:

```kotlin
class SecretsStore(context: Context) {
    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "secrets", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSecret(secret: String) = prefs.edit().putString(K_SECRET, secret).apply()
    fun getSecret(): String? = prefs.getString(K_SECRET, null)
    fun clearSecret() = prefs.edit().remove(K_SECRET).apply()

    fun saveServerUrl(url: String) = prefs.edit().putString(K_URL, url).apply()
    fun getServerUrl(): String? = prefs.getString(K_URL, null)

    fun markSecretInvalid() = prefs.edit().putBoolean(K_INVALID, true).apply()
    fun clearSecretInvalid() = prefs.edit().putBoolean(K_INVALID, false).apply()
    fun isSecretInvalid(): Boolean = prefs.getBoolean(K_INVALID, false)

    companion object {
        private const val K_SECRET = "android_api_secret"
        private const val K_URL = "server_url"
        private const val K_INVALID = "secret_invalid"
        const val DEFAULT_SERVER_URL = "https://aiserver.tail198ba5.ts.net"
    }
}
```

### §8.3 Settings screen

`SettingsScreen.kt` — a single Compose screen with:

- Text field "Adres serwera" (server URL), pre-filled from `SecretsStore.getServerUrl()` or `DEFAULT_SERVER_URL`.
- Text field "Token dostępu" (Bearer token), pre-filled with masked placeholder if a secret is already saved, empty otherwise. `KeyboardOptions(keyboardType = KeyboardType.Password)`. Visibility toggle (eye icon) only if the user wants to verify what they typed.
- Button "Zapisz" — saves both values, clears the `secretInvalid` flag.
- Button "Testuj połączenie" — calls `GET /api/android/health` with the current (un-saved or saved) inputs, displays a result row below: either "Połączono. Wersja: <sha>" or the error.
- Below: a status indicator if `isSecretInvalid()` returns true: "Token jest nieprawidłowy. Zaktualizuj go i zapisz."

Strings go to `strings.xml`:

```xml
<string name="settings_title">Ustawienia synchronizacji</string>
<string name="settings_server_url_label">Adres serwera</string>
<string name="settings_secret_label">Token dostępu</string>
<string name="settings_save">Zapisz</string>
<string name="settings_test">Testuj połączenie</string>
<string name="settings_test_ok">Połączono. Wersja: %1$s</string>
<string name="settings_test_failed">Błąd połączenia: %1$s</string>
<string name="settings_secret_invalid">Token jest nieprawidłowy. Zaktualizuj go i zapisz.</string>
```

The screen is reachable from a new menu entry in `ProDashboardScreen`'s overflow menu (or wherever the existing app puts settings entries; check first, don't invent navigation). If there is no existing settings hub, add a single `IconButton` (Material `Settings` icon) to the top app bar.

The "Test connection" button uses a temporary `ServerApi` instance constructed with the **un-saved** inputs, so the user can validate before committing. This means `NetworkModule` (Phase 4) must support on-the-fly creation, not only the singleton. Plan for that now.

### §8.4 `verify-no-secrets.sh`

`scripts/verify-no-secrets.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
echo "== Checking no Bearer tokens in committed files =="
# Heuristic: 32+ hex chars OR a long random base64 string close to "Bearer", "Authorization", "token", "secret"
hits=$(git grep -nE '(Bearer\s+[A-Za-z0-9+/=._-]{20,}|ANDROID_API_SECRET\s*=\s*["A-Za-z0-9])' \
       -- ':!docs/' ':!*.md' ':!*.bak' || true)
if [ -n "$hits" ]; then
    echo "POTENTIAL SECRETS COMMITTED:"
    echo "$hits"
    exit 1
fi
echo "CLEAN"

echo "== Checking no fallbackToDestructiveMigration =="
hits=$(grep -RE 'fallbackToDestructive' app/src/main/ || true)
if [ -n "$hits" ]; then
    echo "DESTRUCTIVE MIGRATION FOUND:"
    echo "$hits"
    exit 1
fi
echo "CLEAN"
```

`chmod +x scripts/verify-no-secrets.sh`. Optionally wire it into a pre-commit hook (Husky or a plain `.git/hooks/pre-commit` symlink) — that's nice-to-have, not required by this CLAUDE.md.

### §8.5 Checkpoint

```bash
# 1. App compiles
./gradlew assembleDebug
# expect: BUILD SUCCESSFUL

# 2. Settings screen reachable
# Install debug APK on a connected device or emulator:
./gradlew installDebug
# Manually: open app, navigate to Settings, verify two fields + two buttons render.
# Enter a deliberately wrong token, tap "Testuj połączenie" — expect Polish error.

# 3. Secrets are encrypted on disk
adb shell run-as <package> cat /data/data/<package>/shared_prefs/secrets.xml
# expect: opaque encrypted-looking content, NOT plaintext Bearer string

# 4. Verify-no-secrets script passes
bash scripts/verify-no-secrets.sh
# expect: two CLEAN lines

# 5. .gitignore excludes secret leakage
grep -E "(\\.secret$|local\\.properties|/secrets/)" .gitignore
# expect: at least the listed patterns present
```

Append to `CHANGELOG.md`. Wait for `next`.

---
