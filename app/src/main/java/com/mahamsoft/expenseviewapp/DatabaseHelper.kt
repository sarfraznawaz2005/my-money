package com.mahamsoft.expenseviewapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "keywords.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_KEYWORDS = "keywords"
        private const val COLUMN_ID = "id"
        private const val COLUMN_KEYWORD = "keyword"
        private const val COLUMN_TYPE = "type"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableStatement = """
            CREATE TABLE $TABLE_KEYWORDS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_KEYWORD TEXT NOT NULL,
                $COLUMN_TYPE TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTableStatement)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        //db.execSQL("DROP TABLE IF EXISTS $TABLE_KEYWORDS")
        onCreate(db)
    }

    fun addKeyword(keyword: Keyword): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_KEYWORD, keyword.keyword)
            put(COLUMN_TYPE, keyword.type)
        }
        val result = db.insert(TABLE_KEYWORDS, null, values)
        db.close()
        return result != -1L
    }

    fun getAllKeywords(): List<Keyword> {
        val keywords = mutableListOf<Keyword>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_KEYWORDS", null)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val keyword = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_KEYWORD))
                val type = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE))
                keywords.add(Keyword(id, keyword, type))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return keywords
    }

    fun updateKeyword(keyword: Keyword): Boolean {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_KEYWORD, keyword.keyword)
            put(COLUMN_TYPE, keyword.type)
        }
        val result = db.update(TABLE_KEYWORDS, values, "$COLUMN_ID = ?", arrayOf(keyword.id.toString()))
        db.close()
        return result > 0
    }

    fun deleteKeyword(id: Int): Boolean {
        val db = this.writableDatabase
        val result = db.delete(TABLE_KEYWORDS, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return result > 0
    }
}
