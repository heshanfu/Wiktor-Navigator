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

package pl.org.seva.navigator.main

import android.app.Application
import com.google.firebase.auth.FirebaseUser
import org.kodein.di.Kodein
import org.kodein.di.conf.global
import pl.org.seva.navigator.debug.debug

class NavigatorApplication : Application() {

    init {
        Kodein.global.addImport(module { application = this@NavigatorApplication })
    }

    private val bootstrap: Bootstrap get() = instance()

    override fun onCreate() {
        super.onCreate()
        bootstrap.boot()
    }

    override fun onTerminate() {
        super.onTerminate()
        debug().stop()
    }

    fun login(user: FirebaseUser) = bootstrap.login(user)
    fun logout() = bootstrap.logout()
    fun stopService() = bootstrap.stopNavigatorService()
    fun startService() = bootstrap.startNavigatorService()
}
