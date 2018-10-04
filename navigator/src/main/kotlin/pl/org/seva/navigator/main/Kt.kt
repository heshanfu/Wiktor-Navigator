/*
 * Copyright (C) 2018 Wiktor Nizio
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
 *
 * If you like this program, consider donating bitcoin: bc1qncxh5xs6erq6w4qz3a7xl7f50agrgn3w58dsfp
 */

package pl.org.seva.navigator.main

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.annotation.NonNull
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

infix fun <T> Context.start(clazz: Class<T>): Boolean {
    startActivity(Intent(this, clazz))
    return true
}

fun <T> Context.start(clazz: Class<T>, f: Intent.() -> Intent): Boolean {
    startActivity(Intent(this, clazz).run(f))
    return true
}

fun <T> FragmentActivity.startForResult(clazz: Class<T>, requestCode: Int) {
    startActivityForResult(Intent(this, clazz), requestCode)
}

val prefs get() = instance<SharedPreferences>()

val applicationContext get() = instance<Context>()

fun context() = instance<Context>()

val versionName get(): String =
    context().packageManager.getPackageInfo(context().packageName, 0).versionName

fun <T> LiveData<T>.observe(owner: LifecycleOwner, f: (T) -> Unit) =
        observe(owner, Observer<T> { f(it) })

//@MainThread
//fun observe(@NonNull owner: LifecycleOwner, @NonNull observer: Observer<in T>) {
//    assertMainThread("observe")
//    if (owner.lifecycle.currentState == DESTROYED) {
//        // ignore
//        return
//    }
//    val wrapper = LifecycleBoundObserver(owner, observer)
//    val existing = mObservers.putIfAbsent(observer, wrapper)
//    if (existing != null && !existing!!.isAttachedTo(owner)) {
//        throw IllegalArgumentException("Cannot add the same observer" + " with different lifecycles")
//    }
//    if (existing != null) {
//        return
//    }
//    owner.lifecycle.addObserver(wrapper)
//}