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

package org.dconnectbot.data

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * @author Dmitry Tkachuk
 * @since 27.03.2020
 */
interface AuthConnection {

    @GET("ws/1/proxy/get_settings/0.1/1/{USERNAME}/{PASSWORD}")
    fun getcredentials(@Path("USERNAME") username: String, @Path("PASSWORD") password: String): Call<List<Credentials>>

    @GET("ws/1/proxy/proxy_test/{PORTNUMBER}")
    fun proxytest(@Path("PORTNUMBER") port: Int): Call<Int>

    @POST("ws/1/proxy/log_error2")
    fun posterror(@Body err: ErrorBody): Call<Int>

}