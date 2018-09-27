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
 *
 * If you like this program, consider donating bitcoin: 36uxha7sy4mv6c9LdePKjGNmQe8eK16aX6
 */

package pl.org.seva.navigator.navigation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.crashlytics.android.Crashlytics
import com.google.android.gms.maps.SupportMapFragment

import com.google.android.material.snackbar.Snackbar
import io.fabric.sdk.android.Fabric
import kotlinx.android.synthetic.main.activity_navigation.*
import org.apache.commons.io.IOUtils

import pl.org.seva.navigator.R
import pl.org.seva.navigator.contact.*
import pl.org.seva.navigator.contact.room.ContactsDatabase
import pl.org.seva.navigator.credits.creditsActivity
import pl.org.seva.navigator.data.fb.fbWriter
import pl.org.seva.navigator.main.*
import pl.org.seva.navigator.profile.*
import pl.org.seva.navigator.settings.settingsActivity

class NavigationActivity : AppCompatActivity() {

    private var backClickTime = 0L

    private var isLocationPermissionGranted = false

    private var dialog: Dialog? = null
    private var snackbar: Snackbar? = null

    private var exitApplicationToast: Toast? = null

    private lateinit var viewHolder: NavigationViewHolder

    private val isLoggedIn get() = isLoggedIn()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fabric.with(this, Crashlytics())
        setContentView(R.layout.activity_navigation)
        supportActionBar!!.title = getString(R.string.navigation_activity_label)
        viewHolder = navigationView {
            init(savedInstanceState, root, intent.getStringExtra(CONTACT_EMAIL_EXTRA))
            checkLocationPermission = this@NavigationActivity::ifLocationPermissionGranted
            persistCameraPositionAndZoom = this@NavigationActivity::persistCameraPositionAndZoom
        }
        fab.setOnClickListener { onAddContactClicked() }
        checkLocationPermission()
        activityRecognition.listen(lifecycle) { state ->
            when (state) {
                ActivityRecognitionSource.STATIONARY -> hud_stationary.visibility = View.VISIBLE
                ActivityRecognitionSource.MOVING -> hud_stationary.visibility = View.GONE
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra(CONTACT_EMAIL_EXTRA)?.apply {
            val contact = contactsStore[this]
            viewHolder.contact = contact
            contact.persist()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        peerObservable.clearPeerListeners()
    }

    override fun onResume() {
        super.onResume()
        checkLocationPermission(
                onGranted = {
                    snackbar?.dismiss()
                    if (!isLoggedIn) {
                        showLoginSnackbar()
                    }
                },
                onDenied = {})
        invalidateOptionsMenu()

        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { viewHolder ready it }
    }

    private fun onAddContactClicked() {
        viewHolder.stopWatchingPeer()
        if (!isLocationPermissionGranted) {
            checkLocationPermission()
        }
        else if (isLoggedIn) {
            startActivityForResult(
                    Intent(this, ContactsActivity::class.java),
                    CONTACTS_ACTIVITY_REQUEST_ID)
        }
        else {
            showLoginSnackbar()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            CONTACTS_ACTIVITY_REQUEST_ID -> {
                if (resultCode == Activity.RESULT_OK) {
                    val contact: Contact? = data?.getParcelableExtra(CONTACT_EXTRA)
                    contact.persist()
                    viewHolder.contact = contact
                }
                viewHolder.updateHud()
            }
            DELETE_PROFILE_REQUEST_ID -> {
                if (resultCode == Activity.RESULT_OK) {
                    deleteProfile()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private inline fun ifLocationPermissionGranted(f: () -> Unit) =
            checkLocationPermission(onGranted = f, onDenied = {})

    private inline fun checkLocationPermission(
            onGranted: () -> Unit = ::onLocationPermissionGranted,
            onDenied: () -> Unit = ::requestLocationPermission) =
                if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        isLocationPermissionGranted = true
                        onGranted.invoke()
                }
                else { onDenied.invoke() }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.navigation, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_help).isVisible =
                !isLocationPermissionGranted || !isLoggedIn
        menu.findItem(R.id.action_logout).isVisible = isLoggedIn
        menu.findItem(R.id.action_delete_user).isVisible = isLoggedIn
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        fun help(caption: Int, file: String, action: () -> Unit): Boolean {
                dialog = Dialog(this).apply {
                setContentView(R.layout.dialog_help)
                val web = findViewById<WebView>(R.id.web)
                web.settings.defaultTextEncodingName = UTF_8
                findViewById<Button>(R.id.action_button).setText(caption)
                val content = IOUtils.toString(assets.open(file), UTF_8)
                        .replace(APP_VERSION_PLACEHOLDER, versionName)
                        .replace(APP_NAME_PLACEHOLDER, getString(R.string.app_name))
                web.loadDataWithBaseURL(ASSET_DIR, content, PLAIN_TEXT, UTF_8, null)

                findViewById<View>(R.id.action_button).setOnClickListener {
                    action()
                    dismiss()
                }
                show()
            }
            return true
        }

        fun showLocationPermissionHelp() = help(
                R.string.dialog_settings_button,
                HELP_LOCATION_PERMISSION_EN,
                action = ::onSettingsClicked)

        fun showLoginHelp() = help(R.string.dialog_login_button, HELP_LOGIN_EN, action = ::login)

        return when (item.itemId) {
            R.id.action_logout -> logout()
            R.id.action_delete_user -> deleteProfileActivity(DELETE_PROFILE_REQUEST_ID)
            R.id.action_help -> if (!isLocationPermissionGranted) {
                showLocationPermissionHelp()
            }
            else if (!isLoggedIn) {
                showLoginHelp()
            } else true
            R.id.action_settings -> settingsActivity()
            R.id.action_credits -> creditsActivity()

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onSettingsClicked() {
        dialog?.dismiss()
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun requestLocationPermission() {
        permissions().request(
                this,
                Permissions.LOCATION_PERMISSION_REQUEST_ID,
                arrayOf(Permissions.PermissionRequest(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        onGranted = ::onLocationPermissionGranted,
                        onDenied = ::onLocationPermissionDenied)))
    }

    @SuppressLint("MissingPermission")
    private fun onLocationPermissionGranted() {
        invalidateOptionsMenu()
        viewHolder.locationPermissionGranted()
        if (isLoggedIn) {
            (application as NavigatorApplication).startService()
        }
    }

    private fun onLocationPermissionDenied() {
        showLocationPermissionSnackbar()
    }

    private fun showLocationPermissionSnackbar() {
        snackbar = Snackbar.make(
                coordinator,
                R.string.snackbar_permission_request_denied,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.snackbar_retry)  { requestLocationPermission() }
                .apply { show() }
    }

    private fun showLoginSnackbar() {
        snackbar = Snackbar.make(
                coordinator,
                R.string.snackbar_please_log_in,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.snackbar_login) { login() }
                .apply { show() }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray) =
            permissions().onRequestPermissionsResult(requestCode, permissions, grantResults)

    private fun login() {
        dialog?.dismiss()
        loginActivity(LoginActivity.LOGIN)
    }

    private fun logout(): Boolean {
        null.persist()
        viewHolder.stopWatchingPeer()
        loginActivity(LoginActivity.LOGOUT)
        return true
    }

    private fun deleteProfile() {
        viewHolder.stopWatchingPeer()
        contactsStore.clear()
        instance<ContactsDatabase>().contactDao.deleteAll()
        setDynamicShortcuts(this)
        fbWriter.deleteMe()
        logout()
    }

    @SuppressLint("CommitPrefEdits")
    private fun persistCameraPositionAndZoom() =
            with (PreferenceManager.getDefaultSharedPreferences(this).edit()) {
                putFloat(ZOOM_PROPERTY, viewHolder.zoom)
                putFloat(LATITUDE_PROPERTY, viewHolder.lastCameraPosition.latitude.toFloat())
                putFloat(LONGITUDE_PROPERTY, viewHolder.lastCameraPosition.longitude.toFloat())
                apply()
            }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(SAVED_PEER_LOCATION, viewHolder.peerLocation)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() = if (System.currentTimeMillis() - backClickTime < DOUBLE_CLICK_MS) {
        (application as NavigatorApplication).stopService()
        exitApplicationToast?.cancel()
        super.onBackPressed()
    } else {
        exitApplicationToast?.cancel()
        exitApplicationToast =
                Toast.makeText(
                        this,
                        R.string.tap_back_second_time,
                        Toast.LENGTH_SHORT).apply { show() }
        backClickTime = System.currentTimeMillis()
    }

    companion object {

        private const val DELETE_PROFILE_REQUEST_ID = 0
        private const val CONTACTS_ACTIVITY_REQUEST_ID = 1

        private const val UTF_8 = "UTF-8"
        private const val ASSET_DIR = "file:///android_asset/"
        private const val PLAIN_TEXT = "text/html"
        private const val APP_VERSION_PLACEHOLDER = "[app_version]"
        private const val APP_NAME_PLACEHOLDER = "[app_name]"
        private const val HELP_LOCATION_PERMISSION_EN = "help_location_permission_en.html"
        private const val HELP_LOGIN_EN = "help_login_en.html"

        const val CONTACT_EXTRA = "contact"
        const val CONTACT_EMAIL_EXTRA = "contact_email"

        const val SAVED_PEER_LOCATION = "saved_peer_location"

        const val ZOOM_PROPERTY = "navigation_map_zoom"
        const val LATITUDE_PROPERTY = "navigation_map_latitude"
        const val LONGITUDE_PROPERTY = "navigation_map_longitude"
        const val DEFAULT_ZOOM = 7.5f

        /** Length of time that will be taken for a double click.  */
        private const val DOUBLE_CLICK_MS: Long = 1000
    }
}
