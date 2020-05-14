package org.dconnectbot

import android.util.Log
import org.dconnectbot.data.AuthConnection
import org.dconnectbot.data.ErrorBody
import org.dconnectbot.util.PreferenceConstants
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ErrorReport {

    companion object {
        private val mretrofit = Retrofit.Builder()
                .baseUrl(PreferenceConstants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        private val errReport = mretrofit.create(AuthConnection::class.java)
    }

    fun sendreport(err: ErrorBody) {
        errReport.posterror(err).enqueue(object : Callback<Int> {
            override fun onResponse(call: Call<Int>, response: Response<Int>) {
                response.body()?.let {
                    Log.d("app", "The message has been sent")
                }
            }

            override fun onFailure(call: Call<Int>, t: Throwable) {
                Log.d("app", "The message has failed $t")
            }
        })
    }
}