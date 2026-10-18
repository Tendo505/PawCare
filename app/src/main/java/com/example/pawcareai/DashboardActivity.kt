package com.example.pawcareai

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.pawcareai.dashboard.DashboardPage
import com.example.pawcareai.dashboard.DashboardScreens
import com.example.pawcareai.dashboard.actionButton
import com.example.pawcareai.dashboard.emptyCard
import com.example.pawcareai.dashboard.label
import com.example.pawcareai.dashboard.withTopMargin
import com.example.pawcareai.data.ApiRequestException
import com.example.pawcareai.data.AppRepository
import com.example.pawcareai.data.UserAccount
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

//hosts the toolbar, bottom navigation and the content area each tab is drawn into
class DashboardActivity : AppCompatActivity()
{
    //screen components and the tab screens
    private lateinit var repository: AppRepository
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var page: DashboardPage
    private lateinit var screens: DashboardScreens

    //select a photo and pass it to the ai scan screen
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null)
        {
            screens.onPhotoSelected(uri)
        }
    }

    //1.screen setup

    //create the dashboard and restore the signed-in account
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        repository = AppRepository(this)
        if (!repository.hasSession)
        {
            returnToLogin()
            return
        }
        setContentView(R.layout.activity_dashboard)
        val contentContainer: FrameLayout = findViewById(R.id.contentContainer)
        toolbar = findViewById(R.id.topAppBar)
        setToolbarActions()

        bottomNavigation = findViewById(R.id.bottomNavigation)
        page = DashboardPage(this, repository, toolbar, contentContainer, bottomNavigation)
        screens = DashboardScreens(page) { imagePicker.launch("image/*") }
        setNavigationActions()
        showLoading()
        restoreAccountRecords()
    }

    //3.process

    //step 1: restore the signed-in account
    private fun restoreAccountRecords()
    {
        repository.restoreSession(::onSessionRestored)
    }

    //step 2: load every record once the account is confirmed
    private fun onSessionRestored(result: Result<UserAccount>)
    {
        if (isFinishing || isDestroyed) return
        result.onSuccess { repository.refreshData(::onRecordsLoaded) }
            .onFailure(::showSessionError)
    }

    //step 3: draw the selected tab and check the services
    private fun onRecordsLoaded(result: Result<Unit>)
    {
        if (isFinishing || isDestroyed) return
        result.onSuccess {
            screens.render(bottomNavigation.selectedItemId)
            screens.refreshSystemHealth()
        }
            .onFailure { showDatabaseError() }
    }

    //return to login for an expired token, otherwise offer a retry
    private fun showSessionError(error: Throwable)
    {
        if (error is ApiRequestException && error.statusCode == 401)
        {
            returnToLogin()
        }
        else
        {
            showDatabaseError()
        }
    }

    //4.output

    //display progress while records are loading
    private fun showLoading()
    {
        toolbar.title = getString(R.string.app_name)
        val root = page.blankScreen()
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(ProgressBar(this))
        root.addView(label(getString(R.string.loading_records), 15, true, R.color.paw_text).withTopMargin(14))
        page.show(root)
    }

    //retry loading records after a connection error
    private fun showDatabaseError()
    {
        val root = page.blankScreen()
        root.addView(emptyCard(getString(R.string.records_load_failed_title), getString(R.string.records_load_failed_message)))
        root.addView(actionButton(getString(R.string.action_try_again)) {
            showLoading()
            restoreAccountRecords()
        }.withTopMargin(12))
        page.show(root)
    }

    //5.navigation

    //confirm sign-out before returning to login
    private fun setToolbarActions()
    {
        toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_logout)
            {
                confirmSignOut()
                true
            }
            else
            {
                false
            }
        }
    }

    //sign out after the user confirms
    private fun confirmSignOut()
    {
        val signOutDialog = MaterialAlertDialogBuilder(this)
        signOutDialog.setTitle(R.string.sign_out_title)
        signOutDialog.setMessage(R.string.sign_out_message)
        signOutDialog.setNegativeButton(R.string.action_cancel, null)
        signOutDialog.setPositiveButton(R.string.sign_out) { _, _ ->
            repository.logout { returnToLogin() }
        }
        signOutDialog.show()
    }

    //switch the content container from the selected tab
    private fun setNavigationActions()
    {
        bottomNavigation.setOnItemSelectedListener { item ->
            screens.render(item.itemId)
            true
        }
    }

    //return to authentication after logout
    private fun returnToLogin()
    {
        val loginIntent = Intent(this, LoginActivity::class.java)
        startActivity(loginIntent)
        finish()
    }
}
