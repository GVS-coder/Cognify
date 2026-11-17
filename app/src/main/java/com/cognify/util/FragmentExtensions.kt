package com.cognify.util

import androidx.fragment.app.Fragment
import com.google.android.material.transition.MaterialSharedAxis

fun Fragment.setupMaterialSharedAxisTransitions(axis: Int = MaterialSharedAxis.Z) {
    enterTransition = MaterialSharedAxis(axis, true)
    returnTransition = MaterialSharedAxis(axis, false)
    exitTransition = MaterialSharedAxis(axis, true)
    reenterTransition = MaterialSharedAxis(axis, false)
}
