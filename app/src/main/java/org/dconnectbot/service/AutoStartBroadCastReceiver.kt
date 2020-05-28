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

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import org.dconnectbot.HostListActivity

class AutoStartBroadCastReceiver : BroadcastReceiver() {

    override fun onReceive(p0: Context?, p1: Intent?) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            if (p1?.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
                p0?.let {
                    val serviceComponent = ComponentName(p0, AutoStartJobService::class.java)
                    val builder = JobInfo.Builder(0, serviceComponent)
                    lateinit var jobScheduler: JobScheduler
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        jobScheduler = p0.getSystemService(JobScheduler::class.java)
                    }
                    jobScheduler.schedule(builder.build())
                }
            }
        } else {
            val intent = Intent(p0, HostListActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            p0?.startActivity(intent)
        }
    }
}