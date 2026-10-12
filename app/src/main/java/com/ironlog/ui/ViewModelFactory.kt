package com.ironlog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ironlog.di.AppContainer

/** Builds a ViewModel with access to the [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    container: AppContainer,
    key: String? = null,
    crossinline build: (AppContainer) -> VM,
): VM {
    val factory = remember(container) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = build(container) as T
        }
    }
    return viewModel(key = key, factory = factory)
}
