package dev.ahmad.routerbattery

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Best-effort 10-minute chain: each run schedules the next one. */
object Refresher {
    private const val NAME = "router_refresh"
    private const val INTERVAL_MIN = 10L

    fun now(c: Context) = enqueue(c, 0, ExistingWorkPolicy.REPLACE)
    fun next(c: Context) = enqueue(c, INTERVAL_MIN, ExistingWorkPolicy.APPEND_OR_REPLACE)
    fun cancel(c: Context) { WorkManager.getInstance(c).cancelUniqueWork(NAME) }

    private fun enqueue(c: Context, delayMin: Long, policy: ExistingWorkPolicy) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(delayMin, TimeUnit.MINUTES).build()
        WorkManager.getInstance(c).enqueueUniqueWork(NAME, policy, req)
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        withContext(Dispatchers.IO) {
            try {
                val ip = Store.ip(c)
                val pw = Store.password(c)
                if (pw.isBlank()) Store.saveError(c, "Tap to set up")
                else Store.saveReading(c, RouterApi.fetch(c, ip, pw))
            } catch (e: IOException) {
                Store.saveError(c, "Router not reachable")
            } catch (e: RouterException) {
                Store.saveError(c, e.message ?: "Error")
            } catch (e: Exception) {
                Store.saveError(c, "Error: ${e.javaClass.simpleName}")
            }
        }
        BatteryWidget().updateAll(c)
        if (GlanceAppWidgetManager(c).getGlanceIds(BatteryWidget::class.java).isNotEmpty()) {
            Refresher.next(c)
        }
        return Result.success()
    }
}

class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.setBusy(context, true)
        BatteryWidget().updateAll(context)
        Refresher.now(context)
    }
}
