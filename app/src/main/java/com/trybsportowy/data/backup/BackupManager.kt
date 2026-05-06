package com.trybsportowy.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.trybsportowy.data.local.DailyReadinessEntity
import com.trybsportowy.data.local.sanitize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class BackupManager {

    private val gson = Gson()
    private val fileName = "trybsportowy_backup.json"

    suspend fun exportToDownloads(context: Context, data: List<DailyReadinessEntity>): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = gson.toJson(data)
            val contentResolver = context.contentResolver
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(jsonString)
                    }
                }
                Log.d("BackupManager", "Backup exported successfully to: $uri")
                true
            } else {
                Log.e("BackupManager", "Failed to create MediaStore entry")
                false
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "Error exporting backup", e)
            false
        }
    }

    suspend fun importFromJson(context: Context, uri: Uri): List<DailyReadinessEntity>? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val jsonString = reader.readText()
                    val listType = object : TypeToken<List<DailyReadinessEntity>>() {}.type
                    val raw: List<DailyReadinessEntity> = gson.fromJson(jsonString, listType)
                    val data = raw.map { it.sanitize() }
                    Log.d("BackupManager", "Backup imported successfully, size: ${data.size}")
                    data
                }
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "Error importing backup", e)
            null
        }
    }
}
