package io.github.acedroidx.frp

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * CHML FRP隧道配置数据类
 */
@Parcelize
data class ChmlFrpConfig(
    val id: Int = 0,
    val name: String = "",
    val localip: String = "127.0.0.1",
    val type: String = "tcp",
    val nport: Int = 0,
    val dorp: String = "",
    val node: String = "",
    val state: String = "true",
    val userid: Int = 0,
    val encryption: String = "false",
    val compression: String = "false",
    val ap: String = " ",
    val uptime: String = "",
    val client_version: String = "",
    val today_traffic_in: Int = 0,
    val today_traffic_out: Int = 0,
    val cur_conns: Int = 0,
    val nodestate: String = "",
    val ip: String = ""
) : Parcelable

/**
 * API响应数据类
 */
data class ApiResponse(
    val msg: String,
    val code: Int,
    val data: List<ChmlFrpConfig>,
    val state: String
)

/**
 * CHML FRP配置响应
 */
data class ConfigResponse(
    val status: Int,
    val success: Boolean,
    val message: String,
    val cfg: String
)
