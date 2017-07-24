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

    private fun readDataOnce(reference: DatabaseReference): Observable<DataSnapshot> {
        val resultSubject = PublishSubject.create<DataSnapshot>()
        return resultSubject
                .doOnSubscribe {
                    reference.addListenerForSingleValueEvent(RxValueEventListener(resultSubject))
                }
                .take(1)
    }

    private fun readData(reference: DatabaseReference): Observable<DataSnapshot> {
        val resultSubject = PublishSubject.create<DataSnapshot>()
        val value = RxValueEventListener(resultSubject)
        return resultSubject
                .doOnSubscribe { reference.addValueEventListener(value) }
                .doOnDispose { reference.removeEventListener(value) }
    }

    private fun DataSnapshot.toContact() =
            if (exists()) Contact(from64(key), child(DISPLAY_NAME).value as String) else Contact()

    fun peerLocationListener(email: String): Observable<LatLng> {
        return readData(email2Reference(email).child(LAT_LNG))
                .filter { it.value != null }
                .map { it.value!! }
                .map { it as String }
                .map { Fb.string2LatLng(it) }
    }

    fun friendshipRequestedListener(): Observable<Contact> {
        return createContactObservable(FRIENDSHIP_REQUESTED, true)
    }

    fun friendshipAcceptedListener(): Observable<Contact> {
        return createContactObservable(FRIENDSHIP_ACCEPTED, true)
    }

    fun friendshipDeletedListener(): Observable<Contact> {
        return createContactObservable(FRIENDSHIP_DELETED, true)
    }

    private fun createContactObservable(tag: String, deleteOnRead: Boolean): Observable<Contact> {
        val reference = currentUserReference().child(tag)
        return readDataOnce(reference)
                .concatMapIterable<DataSnapshot> { it.children }
                .concatWith(childListener(reference))
                .doOnNext { if (deleteOnRead) reference.child(it.key).removeValue() }
                .map { it.toContact() }
    }

    private fun childListener(reference: DatabaseReference): Observable<DataSnapshot> {
        val result = ReplaySubject.create<DataSnapshot>()
        reference.addChildEventListener(RxChildEventListener(result))
        return result.hide()
    }


    fun readFriendsOnce(): Observable<Contact> {
        val reference = currentUserReference().child(FRIENDS)
        return readDataOnce(reference)
                .concatMapIterable { it.children }
                .filter { it.exists() }
                .map { it.toContact() }
    }

    fun readContactOnceForEmail(email: String): Observable<Contact> {
        return readDataOnce(email2Reference(email))
                .map { it.toContact() }
    }
}
