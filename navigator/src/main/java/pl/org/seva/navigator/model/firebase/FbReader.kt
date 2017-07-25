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

package pl.org.seva.navigator.model.firebase

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference

import javax.inject.Inject
import javax.inject.Singleton

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject
import pl.org.seva.navigator.model.Contact

@Singleton
class FbReader @Inject
internal constructor() : Fb() {

    fun peerLocationListener(email: String): Observable<LatLng> {
        return email.toReference().child(LAT_LNG).listen()
                .filter { it.value != null }
                .map { it.value!! }
                .map { it as String }
                .map { it.toLatLng() }
    }

    fun friendshipRequestedListener(): Observable<Contact> {
        return FRIENDSHIP_REQUESTED.contactListener()
    }

    fun friendshipAcceptedListener(): Observable<Contact> {
        return FRIENDSHIP_ACCEPTED.contactListener()
    }

    fun friendshipDeletedListener(): Observable<Contact> {
        return FRIENDSHIP_DELETED.contactListener()
    }

    fun readFriends(): Observable<Contact> {
        val reference = currentUserReference().child(FRIENDS)
        return reference.read()
                .concatMapIterable { it.children }
                .filter { it.exists() }
                .map { it.toContact() }
    }

    fun seekContact(email: String): Observable<Contact> =
            email.toReference().read().map { it.toContact() }

    private fun String.contactListener(): Observable<Contact> {
        val reference = currentUserReference().child(this)
        return reference.read()
                .concatMapIterable<DataSnapshot> { it.children }
                .concatWith(reference.childListener())
                .doOnNext { reference.child(it.key).removeValue() }
                .map { it.toContact() }
    }

    private fun DatabaseReference.childListener(): Observable<DataSnapshot> {
        val result = ReplaySubject.create<DataSnapshot>()
        addChildEventListener(RxChildEventListener(result))
        return result.hide()
    }

    private fun DatabaseReference.read(): Observable<DataSnapshot> {
        val resultSubject = PublishSubject.create<DataSnapshot>()
        return resultSubject
                .doOnSubscribe { addListenerForSingleValueEvent(RxValueEventListener(resultSubject)) }
                .take(READ_ONCE)
    }

    private fun DatabaseReference.listen(): Observable<DataSnapshot> {
        val resultSubject = PublishSubject.create<DataSnapshot>()
        val value = RxValueEventListener(resultSubject)
        return resultSubject
                .doOnSubscribe { addValueEventListener(value) }
                .doOnDispose { removeEventListener(value) }
    }

    private fun DataSnapshot.toContact() =
            if (exists()) Contact(key.from64(), child(DISPLAY_NAME).value as String) else Contact()

    companion object {
        val READ_ONCE = 1L
    }
}
