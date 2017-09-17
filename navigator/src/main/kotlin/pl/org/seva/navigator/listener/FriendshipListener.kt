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

package pl.org.seva.navigator.listener

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.github.salomonbrys.kodein.conf.KodeinGlobalAware
import com.github.salomonbrys.kodein.instance

import java.util.Random

import pl.org.seva.navigator.data.ContactsStore
import pl.org.seva.navigator.data.firebase.FbWriter
import pl.org.seva.navigator.data.model.Contact
import pl.org.seva.navigator.data.ParcelableInt
import pl.org.seva.navigator.data.room.ContactsDatabase
import pl.org.seva.navigator.data.room.entity.ContactEntity
import pl.org.seva.navigator.shortcuts.setDynamicShortcuts
import pl.org.seva.navigator.view.notification.friendshipRequestedNotification

class FriendshipListener : KodeinGlobalAware {

    private val store: ContactsStore = instance()
    private val contactDao = instance<ContactsDatabase>().contactDao
    private val fbWriter: FbWriter = instance()

    private lateinit var context: Context
    private val nm get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun init(context: Context) {
        this.context = context
    }

    fun onPeerRequestedFriendship(contact: Contact) {
        context.registerReceiver(FriendshipReceiver(), IntentFilter(FRIENDSHIP_REQUESTED_INTENT))
        val notificationId = ParcelableInt(Random().nextInt())
        nm.friendshipRequested(contact, notificationId)
    }

    fun onPeerAcceptedFriendship(contact: Contact) {
        store.add(contact)
        contactDao.insert(ContactEntity(contact))
        fbWriter.addFriendship(contact)
        setDynamicShortcuts(context)
    }

    fun onPeerDeletedFriendship(contact: Contact) {
        store.delete(contact)
        contactDao.delete(ContactEntity(contact))
        setDynamicShortcuts(context)
    }

    private fun acceptFriend(contact: Contact) {
        fbWriter.acceptFriendship(contact)
        if (store.contains(contact)) {
            return
        }
        store.add(contact)
        contactDao.insert(ContactEntity(contact))
    }

    private fun NotificationManager.friendshipRequested(contact: Contact, notificationId: ParcelableInt) =
            notify(notificationId.value, friendshipRequestedNotification(context) {
                this.contact = contact
                this.nid = notificationId
            })

    private inner class FriendshipReceiver: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val nid = intent.getParcelableExtra<ParcelableInt>(NOTIFICATION_ID).value
            nm.cancel(nid)
            context.unregisterReceiver(this)
            val contact = intent.getParcelableExtra<Contact>(CONTACT_EXTRA)
            val action = intent.getParcelableExtra<ParcelableInt>(ACTION).value
            if (action == ACCEPTED_ACTION) {
                acceptFriend(contact)
                setDynamicShortcuts(context)
            }
        }
    }

    companion object {

        val FRIENDSHIP_REQUESTED_INTENT = "friendship_requested_intent"
        val NOTIFICATION_ID = "notification_id"
        val ACTION = "friendship_action"
        val CONTACT_EXTRA = "contact_extra"
        val ACCEPTED_ACTION = 0
        val REJECTED_ACTION = 1
    }
}
