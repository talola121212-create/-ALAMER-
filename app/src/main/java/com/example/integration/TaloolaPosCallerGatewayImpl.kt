package com.example.integration

import android.util.Log
import com.example.domain.PhoneNormalizer
import com.example.domain.model.*
import com.example.network.TaloolaPosCallerHubApi
import com.example.security.SecureStorageRepository
import org.json.JSONArray
import org.json.JSONObject

class TaloolaPosCallerGatewayImpl(
    private val connection: TaloolaPosConnection,
    private val secureStorage: SecureStorageRepository
) : TaloolaPosCallerGateway {

    companion object {
        private const val TAG = "TaloolaPosGateway"
    }

    override suspend fun getCallerCustomerContext(phone: String): Result<CustomerContext> {
        val norm = PhoneNormalizer.normalize(phone)
        return try {
            val responseStr = connection.hubClient.invoke(
                TaloolaPosCallerHubApi.METHOD_GET_CUSTOMER_CONTEXT,
                norm,
                secureStorage.getSessionToken()
            )
            val json = JSONObject(responseStr)

            if (!json.optBoolean("found", false)) {
                // Unknown customer
                return Result.success(
                    CustomerContext(
                        customerId = "",
                        phone = phone,
                        normalizedPhone = norm,
                        name = "",
                        status = "عميل جديد",
                        lastCall = null,
                        lastOrder = null,
                        orderCount = 0,
                        area = "غير متوفر",
                        address = "لا توجد بيانات سابقة",
                        latitude = null,
                        longitude = null,
                        notes = "",
                        retrievedAtUtc = ""
                    )
                )
            }

            val lastCallObj = json.optJSONObject("lastCall")
            val lastCall = if (lastCallObj != null) {
                CallSummary(
                    callId = lastCallObj.optString("callId", ""),
                    direction = lastCallObj.optString("direction", "INCOMING"),
                    startedAtUtc = lastCallObj.optString("startedAtUtc", "غير متوفر"),
                    endedAtUtc = lastCallObj.optString("endedAtUtc", ""),
                    durationSeconds = lastCallObj.optInt("durationSeconds", 0)
                )
            } else null

            val lastOrderObj = json.optJSONObject("lastOrder")
            val lastOrder = if (lastOrderObj != null) {
                OrderSummary(
                    orderNumber = lastOrderObj.optString("orderNumber", "غير متوفر"),
                    orderType = lastOrderObj.optString("orderType", "غير متوفر"),
                    total = lastOrderObj.optString("total", "غير متوفر"),
                    status = lastOrderObj.optString("status", "غير متوفر"),
                    createdAtUtc = lastOrderObj.optString("createdAtUtc", "")
                )
            } else null

            val context = CustomerContext(
                customerId = json.optString("customerId", ""),
                phone = json.optString("phone", phone),
                normalizedPhone = norm,
                name = json.optString("name", "غير متوفر"),
                status = json.optString("status", "عميل معروف"),
                lastCall = lastCall,
                lastOrder = lastOrder,
                orderCount = json.optInt("orderCount", 0),
                area = json.optString("area", "غير متوفر"),
                address = json.optString("address", "غير متوفر"),
                latitude = if (json.has("latitude") && !json.isNull("latitude")) json.optDouble("latitude") else null,
                longitude = if (json.has("longitude") && !json.isNull("longitude")) json.optDouble("longitude") else null,
                notes = json.optString("notes", ""),
                retrievedAtUtc = json.optString("retrievedAtUtc", "")
            )
            Result.success(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching customer context: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getCallerLastOrders(phone: String): Result<List<OrderSummary>> {
        val norm = PhoneNormalizer.normalize(phone)
        return try {
            val responseStr = connection.hubClient.invoke(
                TaloolaPosCallerHubApi.METHOD_GET_LAST_ORDERS,
                norm,
                secureStorage.getSessionToken()
            )
            val array = JSONArray(responseStr)
            val list = mutableListOf<OrderSummary>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    OrderSummary(
                        orderNumber = obj.optString("orderNumber", "غير متوفر"),
                        orderType = obj.optString("orderType", "غير متوفر"),
                        total = obj.optString("total", "غير متوفر"),
                        status = obj.optString("status", "غير متوفر"),
                        createdAtUtc = obj.optString("createdAtUtc", "")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCallerLocation(phone: String): Result<Pair<Double, Double>?> {
        val norm = PhoneNormalizer.normalize(phone)
        return try {
            val responseStr = connection.hubClient.invoke(
                TaloolaPosCallerHubApi.METHOD_GET_LOCATION,
                norm,
                secureStorage.getSessionToken()
            )
            val json = JSONObject(responseStr)
            if (json.has("latitude") && json.has("longitude") && !json.isNull("latitude")) {
                val lat = json.getDouble("latitude")
                val lng = json.getDouble("longitude")
                Result.success(lat to lng)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recordCallerCall(callEvent: CallEvent): Result<Boolean> {
        return try {
            val json = JSONObject().apply {
                put("callId", callEvent.callId)
                put("phone", callEvent.phone)
                put("normalizedPhone", callEvent.normalizedPhone)
                put("direction", callEvent.direction)
                put("startedAtUtc", callEvent.startedAtUtc)
                put("endedAtUtc", callEvent.endedAtUtc ?: JSONObject.NULL)
                put("durationSeconds", callEvent.durationSeconds)
                put("customerId", callEvent.customerId)
                put("deviceId", callEvent.deviceId)
                put("operatorId", callEvent.operatorId)
                put("createdAtUtc", callEvent.createdAtUtc)
            }
            val responseStr = connection.hubClient.invoke(
                TaloolaPosCallerHubApi.METHOD_RECORD_CALL,
                json.toString(),
                secureStorage.getSessionToken()
            )
            val res = JSONObject(responseStr)
            Result.success(res.optBoolean("success", true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendCallerContextToCashier(context: CustomerContext): Result<Boolean> {
        return try {
            val json = JSONObject().apply {
                put("customerId", context.customerId)
                put("phone", context.phone)
                put("normalizedPhone", context.normalizedPhone)
                put("name", context.name)
                put("area", context.area)
                put("address", context.address)
                put("notes", context.notes)
                put("deviceId", secureStorage.getDeviceId())
                put("operatorAccount", secureStorage.getOperatorAccount())
            }
            val responseStr = connection.hubClient.invoke(
                TaloolaPosCallerHubApi.METHOD_SEND_CONTEXT_TO_CASHIER,
                json.toString(),
                secureStorage.getSessionToken()
            )
            val res = JSONObject(responseStr)
            Result.success(res.optBoolean("success", true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCallerAssistantStatus(): Result<CallAssistantStatus> {
        return try {
            val responseStr = connection.hubClient.invoke(
                TaloolaPosCallerHubApi.METHOD_GET_ASSISTANT_STATUS,
                secureStorage.getDeviceId(),
                secureStorage.getSessionToken()
            )
            val json = JSONObject(responseStr)
            val status = CallAssistantStatus(
                isConnected = true,
                capabilityGranted = json.optBoolean("callerAssistantGranted", true),
                serverId = json.optString("serverId", ""),
                activeCallsCount = json.optInt("activeCallsCount", 0),
                lastHeartbeatUtc = json.optString("lastHeartbeatUtc", "")
            )
            Result.success(status)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createCustomer(
        phone: String,
        name: String,
        area: String,
        address: String
    ): Result<Boolean> {
        return try {
            val payload = JSONObject().apply {
                put("phone", phone)
                put("normalizedPhone", PhoneNormalizer.normalize(phone))
                put("name", name)
                put("area", area)
                put("address", address)
                put("deviceId", secureStorage.getDeviceId())
            }
            val response = connection.hubClient.invoke("CreateCustomerFromCaller", payload.toString(), secureStorage.getSessionToken())
            val res = JSONObject(response)
            Result.success(res.optBoolean("success", true))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
