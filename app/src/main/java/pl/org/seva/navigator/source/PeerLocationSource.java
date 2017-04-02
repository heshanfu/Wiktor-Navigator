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

package pl.org.seva.navigator.source;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import pl.org.seva.navigator.database.firebase.FirebaseReader;
import pl.org.seva.navigator.receiver.PeerLocationReceiver;

public class PeerLocationSource {

    @SuppressWarnings("WeakerAccess")
    @Inject
    FirebaseReader firebaseReader;

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    @Inject PeerLocationSource() {
    }

    public void addPeerLocationReceiver(String email, PeerLocationReceiver peerLocationReceiver) {
        compositeDisposable.add(
                firebaseReader
                        .peerLocationListener(email)
                        .subscribe(peerLocationReceiver::onPeerLocationReceived));
    }

    public void clearPeerLocationReceivers() {
        compositeDisposable.clear();
    }
}
