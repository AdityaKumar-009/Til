package com.flivoro.tile8auncher.features

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object LauncherBackupManager {
    private const val FORMAT_VERSION = 1

    fun exportTo(context: Context, uri: Uri): Result<Unit> = runCatching {
        val prefs = context.applicationContext.getSharedPreferences(
            LauncherFeatureStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val values = JSONObject()
        prefs.all.forEach { (key, value) ->
            val entry = JSONObject()
            when (value) {
                is String -> {
                    entry.put("type", "string")
                    entry.put("value", value)
                }
                is Boolean -> {
                    entry.put("type", "boolean")
                    entry.put("value", value)
                }
                is Int -> {
                    entry.put("type", "int")
                    entry.put("value", value)
                }
                is Long -> {
                    entry.put("type", "long")
                    entry.put("value", value)
                }
                is Float -> {
                    entry.put("type", "float")
                    entry.put("value", value.toDouble())
                }
                is Set<*> -> {
                    entry.put("type", "stringSet")
                    val array = JSONArray()
                    value.filterIsInstance<String>().sorted().forEach(array::put)
                    entry.put("value", array)
                }
                else -> return@forEach
            }
            values.put(key, entry)
        }
        val root = JSONObject().apply {
            put("format", "MosaicLauncherBackup")
            put("version", FORMAT_VERSION)
            put("preferences", values)
        }
        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
            it.write(root.toString(2))
        }
    }

    fun importFrom(context: Context, uri: Uri): Result<Unit> = runCatching {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        require(root.optString("format") == "MosaicLauncherBackup") { "Unsupported backup file" }
        require(root.optInt("version") in 1..FORMAT_VERSION) { "Unsupported backup version" }
        val values = root.getJSONObject("preferences")
        val prefs = context.applicationContext.getSharedPreferences(
            LauncherFeatureStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        val editor = prefs.edit().clear()
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = values.optJSONObject(key) ?: continue
            when (entry.optString("type")) {
                "string" -> editor.putString(key, entry.optString("value"))
                "boolean" -> editor.putBoolean(key, entry.optBoolean("value"))
                "int" -> editor.putInt(key, entry.optInt("value"))
                "long" -> editor.putLong(key, entry.optLong("value"))
                "float" -> editor.putFloat(key, entry.optDouble("value").toFloat())
                "stringSet" -> {
                    val array = entry.optJSONArray("value") ?: JSONArray()
                    val set = buildSet {
                        for (i in 0 until array.length()) {
                            array.optString(i).takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                    editor.putStringSet(key, set)
                }
            }
        }
        editor.commit()
        IconPackManager.clearCaches()
    }
}
