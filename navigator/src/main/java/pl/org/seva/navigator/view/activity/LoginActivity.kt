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

@file:Suppress("DEPRECATION")

package pl.org.seva.navigator.view.activity

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast

import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

import javax.inject.Inject

import pl.org.seva.navigator.NavigatorApplication
import pl.org.seva.navigator.R
import pl.org.seva.navigator.model.database.firebase.FirebaseWriter

@Suppress("DEPRECATION")
class LoginActivity :
        AppCompatActivity(),
        GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks {

    @Inject
    lateinit var firebaseWriter: FirebaseWriter

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var authStateListener: (firebaseAuth : FirebaseAuth) -> Unit

    private var googleApiClient: GoogleApiClient? = null

    private var progressDialog: ProgressDialog? = null
    private var performedAction: Boolean = false
    private var logoutWhenReady: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as NavigatorApplication).component.inject(this)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

        googleApiClient = GoogleApiClient.Builder(this)
                .enableAutoManage(this, this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .addConnectionCallbacks(this)
                .build()

        firebaseAuth = FirebaseAuth.getInstance()

        authStateListener = {
            val user = it.currentUser
            if (user != null) {
                Log.d(TAG, "onAuthStateChanged:signed_in:" + user.uid)
                onUserLoggedIn(user)
            } else {
                Log.d(TAG, "onAuthStateChanged:signed_out")
                onUserLoggedOut()
            }
        }


        if (intent.getStringExtra(ACTION) == LOGOUT) {
            logout()
            finish()
            return
        }

        setContentView(R.layout.activity_login)
        findViewById<View>(R.id.sign_in_button).setOnClickListener({ onLoginClicked() })
    }

    private fun login() {
        val signInIntent = Auth.GoogleSignInApi.getSignInIntent(googleApiClient)
        startActivityForResult(signInIntent, SIGN_IN_REQUEST_ID)
    }

    private fun logout() {
        finishWhenStateChanges()
        logoutWhenReady = true
        // Firebase sign out
        firebaseAuth.signOut()
        (application as NavigatorApplication).logout()
        googleApiClient!!.connect()
        finish()
    }

    private fun onUserLoggedIn(user: FirebaseUser) {
        firebaseWriter.login(user)
        (application as NavigatorApplication).login(user)
        if (performedAction) {
            finish()
        }
    }

    private fun onUserLoggedOut() {
        (application as NavigatorApplication).logout()
        if (performedAction) {
            finish()
        }
    }

    public override fun onStart() {
        super.onStart()
        firebaseAuth.addAuthStateListener(authStateListener)
    }

    public override fun onStop() {
        super.onStop()
        hideProgressDialog()
        firebaseAuth.removeAuthStateListener(authStateListener)
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
        finish()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == SIGN_IN_REQUEST_ID) {
            finishWhenStateChanges()
            val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            if (result.isSuccess) {
                // Google Sign In was successful, authenticate with Firebase
                val account = result.signInAccount!!
                firebaseAuthWithGoogle(account)
            } else {
                onUserLoggedOut()
            }
        }
    }

    private fun finishWhenStateChanges() {
        performedAction = true
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.id!!)
        showProgressDialog()

        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(
                        this
                ) {
                    Log.d(TAG, "signInWithCredential:onComplete:" + it.isSuccessful)
                    hideProgressDialog()

                    // If sign in fails, display a message to the user. If sign in succeeds
                    // the auth state listener will be notified and logic to handle the
                    // signed in user can be handled in the listener.
                    if (!it.isSuccessful) {
                        signInFailed(it.exception)
                    }
                }
    }

    private fun signInFailed(ex: Exception?) {
        Log.w(TAG, "signInWithCredential", ex)
        Toast.makeText(this@LoginActivity, R.string.login_authentication_failed, Toast.LENGTH_SHORT)
                .show()
    }

    private fun showProgressDialog() {
        @Suppress("DEPRECATION")
        progressDialog = ProgressDialog(this)
        progressDialog!!.setMessage(getString(R.string.login_loading))
        progressDialog!!.isIndeterminate = true

        progressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
        }
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.d(TAG, "onConnectionFailed:" + connectionResult)
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show()
    }

    fun onLoginClicked() {
        login()
    }

    override fun onConnected(bundle: Bundle?) {
        if (logoutWhenReady) {
            Auth.GoogleSignInApi.signOut(googleApiClient)
        }
    }

    override fun onConnectionSuspended(i: Int) {
        //
    }

    companion object {

        val ACTION = "action"
        val LOGIN = "login"
        val LOGOUT = "logout"

        private val TAG = LoginActivity::class.java.simpleName

        private val SIGN_IN_REQUEST_ID = 9001
    }
}
