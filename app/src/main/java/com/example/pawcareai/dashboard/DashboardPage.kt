package com.example.pawcareai.dashboard

import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pawcareai.R
import com.example.pawcareai.data.AppRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

//the parts of DashboardActivity that every screen class draws into
class DashboardPage(
    val activity: AppCompatActivity,
    val repository: AppRepository,
    private val toolbar: MaterialToolbar,
    private val contentContainer: FrameLayout,
    private val bottomNavigation: BottomNavigationView
)
{
    //1.screen setup

    //show the title of the screen being drawn
    fun setTitle(title: String)
    {
        toolbar.title = title
    }

    //inflate a screen layout without attaching it yet
    fun inflate(layout: Int): LinearLayout
    {
        return activity.layoutInflater.inflate(layout, contentContainer, false) as LinearLayout
    }

    //create the content area with the existing screen spacing
    fun blankScreen(): LinearLayout
    {
        val root = activity.vertical(20)
        root.setPadding(activity.dp(20), activity.dp(18), activity.dp(20), activity.dp(32))
        return root
    }

    //3.process

    //handle the result of a database update
    fun <T> handleDatabaseResult(result: Result<T>, onSuccess: (T) -> Unit)
    {
        result.onSuccess(onSuccess)
            .onFailure { toast(activity.getString(R.string.database_update_failed)) }
    }

    //confirm before deleting a record
    fun confirmDelete(title: String, message: String, action: () -> Unit)
    {
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.action_keep, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> action() }
            .show()
    }

    //4.output

    //display the selected module inside the content container
    fun show(root: LinearLayout)
    {
        contentContainer.removeAllViews()
        val scrollView = ScrollView(activity)
        scrollView.isFillViewport = true
        scrollView.addView(root)
        contentContainer.addView(scrollView)
    }

    //show a long message at the bottom of the screen
    fun toast(message: String?)
    {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }

    //5.navigation

    //check that the activity can still be drawn
    val isActive: Boolean
        get()
        {
            return !activity.isFinishing && !activity.isDestroyed
        }

    //check that a refresh still belongs to the tab on screen
    fun isShowing(tabId: Int): Boolean
    {
        return isActive && bottomNavigation.selectedItemId == tabId
    }

    //read the tab the user selected
    val selectedTab: Int
        get()
        {
            return bottomNavigation.selectedItemId
        }

    //switch to another tab
    fun openTab(tabId: Int)
    {
        bottomNavigation.selectedItemId = tabId
    }
}
