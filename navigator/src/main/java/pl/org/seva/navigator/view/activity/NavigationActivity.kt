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

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import android.view.View

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

import javax.inject.Inject

import pl.org.seva.navigator.R
import pl.org.seva.navigator.NavigatorApplication
import pl.org.seva.navigator.model.Contact
import pl.org.seva.navigator.model.ContactsCache
import pl.org.seva.navigator.presenter.PermissionsUtils
import pl.org.seva.navigator.source.PeerLocationSource

class NavigationActivity : AppCompatActivity() {

    @Inject
    lateinit var peerLocationSource: PeerLocationSource
    @Inject
    lateinit var contactsCache: ContactsCache
    @Inject
    lateinit var permissionsUtils: PermissionsUtils

    private var mapFragment: MapFragment? = null
    private var map: GoogleMap? = null
    private var contact: Contact? = null

    private val fab by lazy { findViewById<View>(R.id.fab) }

    private var peerLocation: LatLng? = null

    private var animateCamera = true
    private var moveCameraToPeerLocation = true
    private var zoom = 0.0f
    private var mapContainerId: Int = 0
    private var locationPermissionGranted = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        zoom = PreferenceManager.getDefaultSharedPreferences(this)
                .getFloat(ZOOM_PROPERTY_NAME, DEFAULT_ZOOM)
        savedInstanceState?.let {
            animateCamera = false
            peerLocation = savedInstanceState.getParcelable<LatLng>(SAVED_PEER_LOCATION)
            peerLocation?.let {  moveCameraToPeerLocation() }
        }

        (application as NavigatorApplication).graph.inject(this)
        setContentView(R.layout.activity_navigation)

        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setDisplayShowHomeEnabled(true)
        }

        contact = intent.getParcelableExtra<Contact>(CONTACT)
        contact?.let {
            contactsCache.addContactsUpdatedListener(it.email(), { this.onContactsUpdated() })
        }
        mapContainerId = findViewById<View>(R.id.map_container).id
    }

    private fun moveCameraToPeerLocation() {
        val cameraPosition = CameraPosition.Builder()
                .target(peerLocation).zoom(zoom).build()
        if (animateCamera) {
            map!!.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        } else {
            map!!.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
        moveCameraToPeerLocation = false
        animateCamera = false
    }

    @SuppressLint("MissingPermission")
    private fun onMapReady(googleMap: GoogleMap) {
        processLocationPermission()
        map = googleMap
        map!!.setOnCameraIdleListener { onCameraIdle() }
        contact?.let {
            peerLocationSource.addPeerLocationListener(it.email(), { this.onPeerLocationReceived(it) })
        }
    }

    private fun processLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true
            map?.isMyLocationEnabled = true
        } else {
            permissionsUtils.permissionGrantedListener()
                    .filter { it.first == PermissionsUtils.LOCATION_PERMISSION_REQUEST_ID }
                    .filter { it.second == Manifest.permission.ACCESS_FINE_LOCATION }
                    .subscribe { onLocationPermissionGranted() }
            ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PermissionsUtils.LOCATION_PERMISSION_REQUEST_ID)
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray) {
        permissionsUtils.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @SuppressLint("MissingPermission")
    private fun onLocationPermissionGranted() {
        locationPermissionGranted = true
        map?.isMyLocationEnabled = true
    }

    private fun onCameraIdle() {
        if (!moveCameraToPeerLocation) {
            zoom = map!!.cameraPosition.zoom
        }
        PreferenceManager.getDefaultSharedPreferences(this).edit().putFloat(ZOOM_PROPERTY_NAME, zoom).apply()
    }

    override fun onPause() {
        super.onPause()
        peerLocationSource.clearPeerLocationListeners()
    }

    override fun onResume() {
        super.onResume()
        prepareMapFragment()
    }

    private fun prepareMapFragment() {
        val fm = fragmentManager
        mapFragment = fm.findFragmentByTag(MAP_FRAGMENT_TAG) as MapFragment?
        mapFragment?:let {
            mapFragment = MapFragment()
            fm.beginTransaction().add(mapContainerId, mapFragment, MAP_FRAGMENT_TAG).commit()
        }
        mapFragment!!.getMapAsync( { onMapReady(it) })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        deleteMapFragment()
        outState.putParcelable(SAVED_PEER_LOCATION, peerLocation)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        deleteMapFragment()
        super.onDestroy()
    }

    private fun deleteMapFragment() {
        mapFragment?.let {
            fragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
            mapFragment = null
        }
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

    private fun onPeerLocationReceived(latLng: LatLng) {
        peerLocation = latLng
        putPeerMarkerOnMap()
        if (moveCameraToPeerLocation) {
            moveCameraToPeerLocation()
        }
    }

    private fun onContactsUpdated() {
        contact = null
        peerLocationSource.clearPeerLocationListeners()
        clearMap()
    }

    private fun clearMap() {
        map!!.clear()
    }

    private fun putPeerMarkerOnMap() {
        map?.let {
            clearMap()
            it.addMarker(MarkerOptions()
                    .position(peerLocation!!)
                    .title(contact!!.name()))
                    .setIcon(BitmapDescriptorFactory.defaultMarker(MARKER_HUE))
        }
    }

    companion object {


        /** Calculated from #00bfa5, or A700 Teal. */
        private val MARKER_HUE = 34.0f

        val CONTACT = "contact"

        private val MAP_FRAGMENT_TAG = "map"

        private val SAVED_PEER_LOCATION = "saved_peer_location"

        private val ZOOM_PROPERTY_NAME = "navigation_map_zoom"
        private val DEFAULT_ZOOM = 7.5f
    }
}
