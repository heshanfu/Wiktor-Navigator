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

package pl.org.seva.navigator.source

import com.github.salomonbrys.kodein.conf.KodeinGlobalAware
import com.github.salomonbrys.kodein.instance
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import pl.org.seva.navigator.data.model.Contact
import pl.org.seva.navigator.data.firebase.FbReader
import pl.org.seva.navigator.listener.FriendshipListener

class FriendshipSource : KodeinGlobalAware {

    private val fbReader: FbReader = instance()

    private val cd = CompositeDisposable()

    fun addFriendshipListener(friendshipListener: FriendshipListener) {
        cd.addAll(
                fbReader
                        .friendshipRequestedListener()
                        .subscribe{ friendshipListener.onPeerRequestedFriendship(it) },
                fbReader
                        .friendshipAcceptedListener()
                        .subscribe { friendshipListener.onPeerAcceptedFriendship(it) },
                fbReader
                        .friendshipDeletedListener()
                        .subscribe { friendshipListener.onPeerDeletedFriendship(it) })
    }

    fun downloadFriendsFromCloud(onFriendFound: (Contact) -> Unit, onCompleted: () -> Unit): Disposable =
            fbReader.readFriends().doOnComplete(onCompleted).subscribe{ onFriendFound(it) }

    fun clearFriendshipListeners() = cd.clear()
}
