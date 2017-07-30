/*
 * Copyright (C) 2017 Wiktor Nizio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pl.org.seva.navigator.model.sqlite

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

import pl.org.seva.navigator.model.Contact

class SqlWriter {

    private lateinit var helper: DbHelper

    fun setHelper(helper: DbHelper) {
        this.helper = helper
    }

    fun addFriend(contact: Contact) {
        val db = helper.writableDatabase
        val cv = ContentValues()
        cv.put(DbHelper.EMAIL_COLUMN_NAME, contact.email!!)
        cv.put(DbHelper.NAME_COLUMN_NAME, contact.name!!)
        db.insertWithOnConflict(
                DbHelper.FRIENDS_TABLE_NAME,
                null,
                cv,
                SQLiteDatabase.CONFLICT_IGNORE)
        db.close()
    }

    fun deleteFriend(contact: Contact) {
        val db = helper.writableDatabase
        val query = "${DbHelper.EMAIL_COLUMN_NAME} = ?"
        val args = arrayOf(contact.email!!)
        db.delete(DbHelper.FRIENDS_TABLE_NAME, query, args)
        db.close()
    }

    fun deleteAllFriends() {
        val db = helper.writableDatabase
        db.delete(DbHelper.FRIENDS_TABLE_NAME, null, null)
    }
}
