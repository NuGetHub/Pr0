package com.pr0gramm.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.updatePadding
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.Track
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.fragments.CommentsInboxFragment
import com.pr0gramm.app.ui.fragments.ConversationsFragment
import com.pr0gramm.app.ui.fragments.WrittenCommentsFragment
import com.pr0gramm.app.util.di.instance
import com.pr0gramm.app.util.find
import com.pr0gramm.app.util.startActivity
import kotterknife.bindView


/**
 * The activity that displays the inbox.
 */
class InboxActivity : BaseAppCompatActivity("InboxActivity"), ViewPager.OnPageChangeListener {
    private val userService: UserService by instance()

    private val coordinator: CoordinatorLayout by bindView(R.id.coordinator)
    private val tabLayout: TabLayout by bindView(R.id.tabs)
    private val viewPager: ViewPager by bindView(R.id.pager)

    private lateinit var tabsAdapter: TabsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.noActionBar)
        super.onCreate(savedInstanceState)

        if (!userService.isAuthorized) {
            startActivity<MainActivity>()
            finish()
            return
        }

        setContentView(R.layout.activity_inbox)

        val toolbar = find<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        tabsAdapter = TabsAdapter(this, this.supportFragmentManager)

        InboxType.values().forEach { type ->
            when (type) {
                InboxType.PRIVATE -> tabsAdapter.addTab(R.string.inbox_type_private) { ConversationsFragment() }
                InboxType.COMMENTS_IN -> tabsAdapter.addTab(R.string.inbox_type_comments_in) { CommentsInboxFragment() }
                InboxType.COMMENTS_OUT -> tabsAdapter.addTab(R.string.inbox_type_comments_out) { WrittenCommentsFragment() }
            }
        }

        viewPager.adapter = tabsAdapter

        viewPager.addOnPageChangeListener(this)

        tabLayout.setupWithViewPager(viewPager)

        // restore previously selected tab
        if (savedInstanceState != null) {
            viewPager.currentItem = savedInstanceState.getInt("tab")
        } else {
            handleNewIntent(intent)
        }

        // track if we've clicked the notification!
        if (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            Track.inboxNotificationClosed("clicked")
        }

        intent.getStringExtra(EXTRA_CONVERSATION_NAME)?.let { name ->
            ConversationActivity.start(this, name, skipInbox = true)
        }

        coordinator.setOnApplyWindowInsetsListener { v, insets ->
            coordinator.updatePadding(top = insets.systemWindowInsetTop)
            viewPager.updatePadding(bottom = insets.systemWindowInsetBottom)
            insets.consumeSystemWindowInsets()
            // insets.replaceSystemWindowInsets(insets.systemWindowInsetLeft, 0, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item) || when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> false
        }
    }

    override suspend fun onStartImpl() {
        super.onStartImpl()

        Track.inboxActivity()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNewIntent(intent)
    }

    private fun handleNewIntent(intent: Intent?) {
        val extras = intent?.extras ?: return
        showInboxType(InboxType.values()[extras.getInt(EXTRA_INBOX_TYPE, 0)])
    }

    private fun showInboxType(type: InboxType?) {
        if (type != null && type.ordinal < tabsAdapter.count) {
            viewPager.currentItem = type.ordinal
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        onTabChanged()
    }

    private fun onTabChanged() {
        val index = viewPager.currentItem
        if (index >= 0 && index < tabsAdapter.count) {
            title = tabsAdapter.getPageTitle(index)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", viewPager.currentItem)
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {

    }

    override fun onPageSelected(position: Int) {
        onTabChanged()
    }

    override fun onPageScrollStateChanged(state: Int) {

    }

    companion object {
        const val EXTRA_INBOX_TYPE = "InboxActivity.inboxType"
        const val EXTRA_FROM_NOTIFICATION = "InboxActivity.fromNotification"
        const val EXTRA_MESSAGE_UNREAD_TIMESTAMP = "InboxActivity.maxMessageId"
        const val EXTRA_CONVERSATION_NAME = "InboxActivity.conversationName"
    }
}
