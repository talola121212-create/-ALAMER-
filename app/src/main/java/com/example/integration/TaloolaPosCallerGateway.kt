package com.example.integration

import com.example.domain.model.CallAssistantStatus
import com.example.domain.model.CallEvent
import com.example.domain.model.CustomerContext
import com.example.domain.model.OrderSummary

interface TaloolaPosCallerGateway {
    suspend fun getCallerCustomerContext(phone: String): Result<CustomerContext>
    suspend fun getCallerLastOrders(phone: String): Result<List<OrderSummary>>
    suspend fun getCallerLocation(phone: String): Result<Pair<Double, Double>?>
    suspend fun recordCallerCall(callEvent: CallEvent): Result<Boolean>
    suspend fun sendCallerContextToCashier(context: CustomerContext): Result<Boolean>
    suspend fun getCallerAssistantStatus(): Result<CallAssistantStatus>
    suspend fun createCustomer(phone: String, name: String, area: String, address: String): Result<Boolean>
}
