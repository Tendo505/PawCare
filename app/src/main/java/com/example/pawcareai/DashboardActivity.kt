package com.example.pawcareai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.pawcareai.dashboard.AiScanScreen
import com.example.pawcareai.dashboard.DashboardPage
import com.example.pawcareai.dashboard.HealthScreen
import com.example.pawcareai.dashboard.HomeScreen
import com.example.pawcareai.dashboard.PetsScreen
import com.example.pawcareai.data.ApiRequestException
import com.example.pawcareai.data.AppRepository
import com.example.pawcareai.data.UserAccount
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

//the main screen after login: a top bar, a bottom bar with four tabs, and a content area in between
//each tab is one file that draws the tab and opens its own pop-up forms:
//HomeScreen, PetsScreen, HealthScreen and AiScanScreen
class DashboardActivity : AppCompatActivity()
{
    //data, views and the four tab screens
    private lateinit var repository: AppRepository
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var page: DashboardPage
    private lateinit var homeScreen: HomeScreen
    private lateinit var petsScreen: PetsScreen
    private lateinit var healthScreen: HealthScreen
    private lateinit var aiScanScreen: AiScanScreen

    //opens the phone's photo picker; android requires this to be created with the activity
    private val photoPicker: ActivityResultLauncher<String> = registerForActivityResult(ActivityResultContracts.GetContent()) { photo: Uri? ->
        if (photo != null)
        {
            aiScanScreen.onPhotoSelected(photo)
        }
    }

    //1.screen setup

    //create the dashboard, then restore the signed-in account
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
        toolbar = findViewById(R.id.topAppBar)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        val contentContainer: FrameLayout = findViewById(R.id.contentContainer)
        page = DashboardPage(this, repository, toolbar, contentContainer, bottomNavigation)
        createScreens()

        setToolbarActions()
        setNavigationActions()
        showLoading()
        restoreAccountRecords()
    }

    //create the four tab screens once
    //health needs pets (for "Add a pet first"); home needs pets and health (for its quick action buttons)
    private fun createScreens()
    {
        petsScreen = PetsScreen(page)
        healthScreen = HealthScreen(page, petsScreen)
        homeScreen = HomeScreen(page, petsScreen, healthScreen)
        aiScanScreen = AiScanScreen(page)
    }

    //3.process

    //step 1: ask laravel who owns the saved token
    private fun restoreAccountRecords()
    {
        repository.restoreSession { result ->
            onSessionRestored(result)
        }
    }

    //step 2: the account is confirmed, so load every record; an expired token goes back to login
    private fun onSessionRestored(result: Result<UserAccount>)
    {
        if (isFinishing || isDestroyed)
        {
            return
        }
        if (result.isSuccess)
        {
            repository.refreshData { dataResult ->
                onRecordsLoaded(dataResult)
            }
            return
        }

        val error: Throwable? = result.exceptionOrNull()
        if (error is ApiRequestException && error.statusCode == 401)
        {
            returnToLogin()
        }
        else
        {
            showLoadError()
        }
    }

    //step 3: the records are loaded, so draw the selected tab and check the services
    private fun onRecordsLoaded(result: Result<Unit>)
    {
        if (isFinishing || isDestroyed)
        {
            return
        }
        if (result.isSuccess)
        {
            showTab(bottomNavigation.selectedItemId)
            homeScreen.refreshConnection()
        }
        else
        {
            showLoadError()
        }
    }

    //4.output

    //a spinner while the records load
    private fun showLoading()
    {
        page.setTitle(R.string.app_name)
        val root: LinearLayout = page.blankScreen()
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(ProgressBar(this))
        page.views.addWithTopMargin(root, page.views.label(getString(R.string.loading_records), 15, true, R.color.paw_text), 14)
        page.show(root)
    }

    //a message and a "Try again" button when the records could not be loaded
    private fun showLoadError()
    {
        val root: LinearLayout = page.blankScreen()
        root.addView(page.views.messageCard(getString(R.string.records_load_failed_title), getString(R.string.records_load_failed_message)))
        val retryButton: MaterialButton = page.views.wideButton(getString(R.string.action_try_again))
        retryButton.setOnClickListener {
            showLoading()
            restoreAccountRecords()
        }
        page.views.addWithTopMargin(root, retryButton, 12)
        page.show(root)
    }

    //5.navigation

    //open the phone's photo picker, showing images only
    fun choosePhoto()
    {
        photoPicker.launch("image/*")
    }

    //draw the screen for a bottom tab
    private fun showTab(tabId: Int)
    {
        if (tabId == R.id.nav_home)
        {
            homeScreen.render()
        }
        else if (tabId == R.id.nav_pets)
        {
            petsScreen.render()
        }
        else if (tabId == R.id.nav_health)
        {
            healthScreen.render()
        }
        else if (tabId == R.id.nav_ai)
        {
            aiScanScreen.render()
        }
    }

    //tapping a bottom tab draws its screen
    private fun setNavigationActions()
    {
        bottomNavigation.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
    }

    //the "Sign out" item in the top bar menu asks before signing out
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

    //sign out after the user confirms, then go back to the login screen
    private fun confirmSignOut()
    {
        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(this)
        builder.setTitle(R.string.sign_out_title)
        builder.setMessage(R.string.sign_out_message)
        builder.setNegativeButton(R.string.action_cancel, null)
        builder.setPositiveButton(R.string.sign_out) { dialog, which ->
            repository.logout {
                returnToLogin()
            }
        }
        builder.show()
    }

    //open the login screen and close the dashboard
    private fun returnToLogin()
    {
        val loginIntent: Intent = Intent(this, LoginActivity::class.java)
        startActivity(loginIntent)
        finish()
    }
}
