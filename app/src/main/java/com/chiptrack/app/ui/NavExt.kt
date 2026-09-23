package com.chiptrack.app.ui

import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

fun Fragment.navigateOnce(actionId: Int) {
    val navController = findNavController()
    val current = navController.currentDestination ?: return
    if (current.getAction(actionId) != null) {
        navController.navigate(actionId)
    }
}
