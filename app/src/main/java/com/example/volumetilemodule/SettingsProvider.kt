package com.example.volumetilemodule

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class SettingsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.volumetilemodule.settings"
        const val PREFS_NAME = "module_prefs"
        
        private const val CODE_PREFS = 1
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "prefs", CODE_PREFS)
        }
        
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/prefs")
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        if (uriMatcher.match(uri) != CODE_PREFS) return null
        
        val context = context ?: return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val cursor = MatrixCursor(arrayOf("key", "value"))
        
        // Add all preferences to cursor
        cursor.addRow(arrayOf("enable_slide", prefs.getBoolean("enable_slide", true).toString()))
        cursor.addRow(arrayOf("enable_long_press", prefs.getBoolean("enable_long_press", true).toString()))
        cursor.addRow(arrayOf("enable_single_tap", prefs.getBoolean("enable_single_tap", false).toString()))
        cursor.addRow(arrayOf("slide_sensitivity", prefs.getInt("slide_sensitivity", 20).toString()))
        cursor.addRow(arrayOf("long_press_delay", prefs.getInt("long_press_delay", 500).toString()))
        
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
