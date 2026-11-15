package com.opero.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Trip::class, Client::class, Memo::class, Expense::class], version = 1, exportSchema = false)
abstract class OperoDb : RoomDatabase() {
    abstract fun trips(): TripDao
    abstract fun clients(): ClientDao
    abstract fun memos(): MemoDao
    abstract fun expenses(): ExpenseDao

    companion object {
        fun create(context: Context): OperoDb =
            Room.databaseBuilder(context, OperoDb::class.java, "opero.db").build()
    }
}
