package com.pr0gramm.app.ui

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import com.pr0gramm.app.R
import com.pr0gramm.app.RequestCodes
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.ThemeHelper.primaryColorDark
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.sync.SyncWorker
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.withViewDisabled
import com.pr0gramm.app.ui.dialogs.ErrorDialogFragment.Companion.showErrorString
import com.pr0gramm.app.util.*
import com.pr0gramm.app.util.di.injector
import com.pr0gramm.app.util.di.instance
import kotterknife.bindView

typealias Callback = () -> Unit

/**
 */
class LoginActivity : BaseAppCompatActivity("LoginActivity") {
    private val userService: UserService by instance()
    private val prefs: SharedPreferences by instance()

    private val usernameView: EditText by bindView(R.id.username)
    private val passwordView: EditText by bindView(R.id.password)
    private val submitView: Button by bindView(R.id.login)

    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.whiteAccent)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        // restore last username
        val defaultUsername = prefs.getString(PREF_USERNAME, "")
        if (!defaultUsername.isNullOrEmpty()) {
            usernameView.setText(defaultUsername)
        }

        submitView.setOnClickListener { onLoginClicked() }

        find<View>(R.id.register).setOnClickListener { onRegisterClicked() }
        find<View>(R.id.password_recovery).setOnClickListener { onPasswordRecoveryClicked() }

        updateActivityBackground()

        usernameView.addTextChangedListener { updateSubmitViewEnabled() }
        passwordView.addTextChangedListener { updateSubmitViewEnabled() }
    }

    private fun updateSubmitViewEnabled() {
        val usernameSet = usernameView.text.isNotBlank()
        val passwordSet = passwordView.text.isNotBlank()
        submitView.isEnabled = usernameSet && passwordSet
    }

    private fun updateActivityBackground() {
        val style = ThemeHelper.theme.whiteAccent

        @DrawableRes
        val drawableId = theme.obtainStyledAttributes(style, R.styleable.AppTheme).use {
            it.getResourceId(R.styleable.AppTheme_loginBackground, 0)
        }

        if (drawableId == 0)
            return

        val fallbackColor = ContextCompat.getColor(this, primaryColorDark)
        val background = createBackgroundDrawable(drawableId, fallbackColor)
        ViewCompat.setBackground(findViewById(R.id.content), background)
    }

    private fun createBackgroundDrawable(drawableId: Int, fallbackColor: Int): Drawable {
        return WrapCrashingDrawable(fallbackColor,
                ResourcesCompat.getDrawable(resources, drawableId, theme)!!)
    }


    private fun enableView(enable: Boolean) {
        usernameView.isEnabled = enable
        passwordView.isEnabled = enable
        submitView.isEnabled = enable
    }

    private fun onLoginClicked() {
        val username = usernameView.text.toString()
        val password = passwordView.text.toString()

        if (username.isEmpty()) {
            usernameView.error = getString(R.string.must_not_be_empty)
            return
        }

        if (password.isEmpty()) {
            passwordView.error = getString(R.string.must_not_be_empty)
            return
        }

        // store last username
        prefs.edit().putString(PREF_USERNAME, username).apply()

        launchWithErrorHandler(busyIndicator = true) {
            withViewDisabled(usernameView, passwordView, submitView) {
                handleLoginResult(userService.login(username, password))
            }
        }
    }

    private fun handleLoginResult(response: UserService.LoginResult) {
        when (response) {
            is UserService.LoginResult.Success -> {
                SyncWorker.scheduleNextSync(this)
                Track.loginSuccessful()

                // signal success
                setResult(Activity.RESULT_OK)
                finish()
            }

            is UserService.LoginResult.Banned -> {
                val date = response.ban.endTime?.let { date ->
                    DurationFormat.timeToPointInTime(this, date, short = false)
                }

                val reason = response.ban.reason
                val message = if (date == null) {
                    getString(R.string.banned_forever, reason)
                } else {
                    getString(R.string.banned, date, reason)
                }

                showErrorString(supportFragmentManager, message)
            }

            is UserService.LoginResult.Failure -> {
                Track.loginFailed()

                val msg = getString(R.string.login_not_successful)
                showErrorString(supportFragmentManager, msg)
                enableView(true)
            }
        }
    }

    private fun onRegisterClicked() {
        Track.registerLinkClicked()

        val uri = Uri.parse("https://pr0gramm.com/pr0mium/iap?iap=true")
        BrowserHelper.openCustomTab(this, uri)
    }

    private fun onPasswordRecoveryClicked() {
        val intent = Intent(this, RequestPasswordRecoveryActivity::class.java)
        startActivity(intent)
    }

    class DoIfAuthorizedHelper(private val fragment: androidx.fragment.app.Fragment) {
        private var retry: Callback? = null

        fun onActivityResult(requestCode: Int, resultCode: Int) {
            if (requestCode == RequestCodes.AUTHORIZED_HELPER) {
                if (resultCode == Activity.RESULT_OK) {
                    retry?.invoke()
                }

                retry = null
            }
        }

        /**
         * Executes the given runnable if a user is signed in. If not, this method shows
         * the login screen. After a successful login, the given 'retry' runnable will be called.
         */
        fun run(runnable: Callback, retry: Callback? = null): Boolean {
            val context = fragment.context ?: return false

            val userService: UserService = context.injector.instance()
            return if (userService.isAuthorized) {
                runnable()
                true

            } else {
                this.retry = retry

                val intent = Intent(context, LoginActivity::class.java)
                startActivityForResult(intent, RequestCodes.AUTHORIZED_HELPER)
                false
            }
        }

        fun runWithRetry(runnable: Callback): Boolean {
            return run(runnable, runnable)
        }

        fun run(runnable: Runnable, retry: Runnable? = null): Boolean {
            return run({ runnable.run() }, { retry?.run() })
        }

        private fun startActivityForResult(intent: Intent, requestCode: Int) {
            fragment.startActivityForResult(intent, requestCode)
        }
    }

    companion object {
        private const val PREF_USERNAME = "LoginDialogFragment.username"

        /**
         * Executes the given runnable if a user is signed in. If not, this method
         * will show a login screen.
         */
        fun helper(fragment: androidx.fragment.app.Fragment) = DoIfAuthorizedHelper(fragment)
    }
}
