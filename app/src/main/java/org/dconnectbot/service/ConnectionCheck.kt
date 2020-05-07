package org.dconnectbot.service

import android.os.Handler
import org.dconnectbot.data.AuthConnection
import org.dconnectbot.util.PreferenceConstants
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.*


class ConnectionCheck(val mport: Int, val mhandler: Handler) : TimerTask() {

    companion object {
        private val mretrofit = Retrofit.Builder()
                .baseUrl(PreferenceConstants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        private val connection = mretrofit.create(AuthConnection::class.java)
    }

    override fun run() {
        connection.proxytest(mport).enqueue(object : Callback<Int> {
            override fun onResponse(call: Call<Int>, response: Response<Int>) {
                response.body()?.let {
                    mhandler.sendEmptyMessage(it)
                }
            }

            override fun onFailure(call: Call<Int>, t: Throwable) {
                mhandler.sendEmptyMessage(0)
            }
        })
    }

}