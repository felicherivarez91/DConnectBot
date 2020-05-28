/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2020 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dconnectbot.service;

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import org.dconnectbot.ConsoleActivity
import org.dconnectbot.data.HostStorage
import org.dconnectbot.service.TerminalManager.TerminalBinder
import org.dconnectbot.util.HostDatabase

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class AutoStartJobService : JobService(), BridgeDisconnectedListener {

    private lateinit var bound: TerminalManager
    private var hostdb: HostStorage? = null
    private var requested: Uri? = null

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            bound = (service as TerminalBinder).service

            // let manager know about our event handling services
            bound.disconnectListener = this@AutoStartJobService
            bound.isResizeAllowed = true
            requested = hostdb?.getHosts(false)?.get(0)?.uri
            val requestedNickname: String? = requested?.fragment
            var requestedBridge = bound.getConnectedBridge(requestedNickname)


            // If we didn't find the requested connection, try opening it
            if (requestedNickname != null && requestedBridge == null) {
                var portforwarding = hostdb?.getPortForwardsForHost(hostdb?.getHosts(false)?.get(0))
                var pass = hostdb?.getHosts(false)?.get(0)?.password
                var email = hostdb?.getHosts(false)?.get(0)?.getemail()
                try {
                    Log.d(ConsoleActivity.TAG, String.format("We couldnt find an existing bridge with URI=%s (nickname=%s), so creating one now", requested.toString(), requestedNickname))
                    requestedBridge = bound.openConnection(requested, portforwarding?.get(0), pass, email)
                } catch (e: Exception) {
                    Log.e(ConsoleActivity.TAG, "Problem while trying to create new requested bridge from URI", e)
                }
            }

        }

        override fun onServiceDisconnected(className: ComponentName) {
            //  bound = null
        }
    }

    override fun onStartJob(p0: JobParameters?): Boolean {
        Toast.makeText(this, "Boot has been completed", Toast.LENGTH_LONG).show()
        ConnectionNotifier.getInstance().showRunningNotification(this)
        hostdb = HostDatabase.get(this)
        bindService(Intent(this, TerminalManager::class.java), connection, Context.BIND_AUTO_CREATE)
        return true
    }


    override fun onStopJob(p0: JobParameters?): Boolean {
        return false
    }

    override fun onDisconnected(bridge: TerminalBridge?) {

    }

}