package com.example.pawcareai.dashboard

import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.example.pawcareai.DashboardActivity
import com.example.pawcareai.R
import com.example.pawcareai.data.AppRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

//everything a screen class needs from the dashboard, passed to each screen in one object:
//the activity (for navigation), the repository (for data) and helpers to draw and show messages
class DashboardPage(
    val dashboard: DashboardActivity,
    val repository: AppRepository,
    private val toolbar: MaterialToolbar,
    private val contentContainer: FrameLayout,
    private val bottomNavigation: BottomNavigationView
)
{
    //shared builders for cards and forms
    val views: ViewMaker = ViewMaker(dashboard)
    val form: FormBuilder = FormBuilder(dashboard, views)

    //1.screen setup

    //show the screen name in the top bar
    fun setTitle(titleResource: Int)
    {
        toolbar.title = dashboard.getString(titleResource)
    }

    //load a screen layout from res/layout (for example screen_pets.xml)
    fun inflate(layoutResource: Int): LinearLayout
    {
        return dashboard.layoutInflater.inflate(layoutResource, contentContainer, false) as LinearLayout
    }

    //create an empty screen with the normal padding, used by the loading and error screens
    fun blankScreen(): LinearLayout
    {
        val root: LinearLayout = views.column(20)
        root.setPadding(views.dp(20), views.dp(18), views.dp(20), views.dp(32))
        return root
    }

    //4.output

    //replace whatever is in the content area with this screen, inside a scroll view
    fun show(root: LinearLayout)
    {
        contentContainer.removeAllViews()
        val scrollView: ScrollView = ScrollView(dashboard)
        scrollView.isFillViewport = true
        scrollView.addView(root)
        contentContainer.addView(scrollView)
    }

    //show a short message at the bottom of the screen
    fun toast(message: String?)
    {
        Toast.makeText(dashboard, message, Toast.LENGTH_LONG).show()
    }

    //shown when a save or delete request to laravel failed
    fun showDatabaseUpdateError()
    {
        toast(dashboard.getString(R.string.database_update_failed))
    }

    //ask "Delete ...?" and run onConfirm only when the user taps Delete
    fun confirmDelete(title: String, message: String, onConfirm: () -> Unit)
    {
        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(dashboard)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setNegativeButton(R.string.action_keep, null)
        builder.setPositiveButton(R.string.action_delete) { dialog, which ->
            onConfirm()
        }
        builder.show()
    }

    //5.navigation

    //true while the activity is open, so late laravel answers do not draw on a closed screen
    fun isOpen(): Boolean
    {
        return !dashboard.isFinishing && !dashboard.isDestroyed
    }

    //true when this bottom tab is the one selected now
    fun isTabSelected(tabId: Int): Boolean
    {
        return isOpen() && bottomNavigation.selectedItemId == tabId
    }

    //switch to another bottom tab, the same as the user tapping it
    fun openTab(tabId: Int)
    {
        bottomNavigation.selectedItemId = tabId
    }
}
