package com.ironlog.di

import android.content.Context
import com.ironlog.data.local.IronLogDatabase
import com.ironlog.data.repository.IronLogRepository
import com.ironlog.domain.session.ActiveWorkoutManager
import com.ironlog.domain.session.RestTimerNotifier

/** Manual dependency container (no DI framework needed for an app this size). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: IronLogDatabase by lazy { IronLogDatabase.build(appContext) }
    val repository: IronLogRepository by lazy { IronLogRepository(database) }
    val restTimerNotifier: RestTimerNotifier by lazy { RestTimerNotifier(appContext) }
    val activeWorkout: ActiveWorkoutManager by lazy { ActiveWorkoutManager(repository, restTimerNotifier) }
}
