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

package pl.org.seva.navigator.view.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.MenuItem
import android.view.View

import javax.inject.Inject

import pl.org.seva.navigator.NavigatorApplication
import pl.org.seva.navigator.R
import pl.org.seva.navigator.model.Contact
import pl.org.seva.navigator.model.ContactsStore
import pl.org.seva.navigator.model.database.firebase.FirebaseWriter
import pl.org.seva.navigator.source.MyLocationSource
import pl.org.seva.navigator.view.adapter.ContactAdapter
import pl.org.seva.navigator.view.builder.dialog.FriendshipDeleteDialogBuilder

class ContactsActivity : AppCompatActivity() {

    @Inject
    lateinit var myLocationSource: MyLocationSource
    @Inject
    lateinit var contactsStore: ContactsStore
    @Inject
    lateinit var firebaseWriter: FirebaseWriter

    private val contactsRecyclerView by lazy { findViewById<RecyclerView>(R.id.contacts) }
    private val contactAdapter = ContactAdapter()
    private val component by lazy { (application as NavigatorApplication).component }
    private val fab by lazy { findViewById<View>(R.id.fab) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        component.inject(this)
        setContentView(R.layout.activity_contacts)
        fab.setOnClickListener { onFabClicked() }

        contactsStore.addContactsUpdatedListener { onContactsUpdated() }

        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowHomeEnabled(true)
        }

        initContactsRecyclerView()
    }

    private fun onFabClicked() {
        startActivity(Intent(this, SearchActivity::class.java))
    }

    private fun initContactsRecyclerView() {
        contactsRecyclerView.setHasFixedSize(true)
        val lm = LinearLayoutManager(this)
        contactsRecyclerView.layoutManager = lm
        component.inject(contactAdapter)
        contactAdapter.addClickListener { onContactClicked(it) }
        contactAdapter.addLongClickListener { onContactLongClicked(it) }
        contactsRecyclerView.adapter = contactAdapter
    }

    private fun onContactClicked(contact: Contact) {
        val intent = Intent(this, NavigationActivity::class.java)

        if (contact.email() != NavigatorApplication.email) {
            intent.putExtra(NavigationActivity.CONTACT, contact)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun onContactLongClicked(contact: Contact) {
        FriendshipDeleteDialogBuilder(this)
                .setContact(contact)
                .setOnConfirmedAction { onDeleteFriendConfirmed(contact) }
                .build()
                .show()
    }

    private fun onDeleteFriendConfirmed(contact: Contact) {
        firebaseWriter.deleteFriendship(contact)
        contactsStore.delete(contact)
        contactAdapter.notifyDataSetChanged()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun onContactsUpdated() {
        contactAdapter.notifyDataSetChanged()
    }
}
