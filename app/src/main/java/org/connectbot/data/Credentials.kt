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

package org.connectbot.data

/**
 * @author Dmitry Tkachuk
 * @since 27.03.2020
 */
data class Credentials(val host: String, val user: String, val pass: String,
                       val port: Int, val portForwarding: List<PortForwarding>)

data class PortForwarding(val nickname: String, val isRemote: Boolean, val sourcePort: Int,
                          val destinationHost: String, val destinationPort: Int)