package com.example.mock

import com.example.domain.PhoneNormalizer
import com.example.domain.model.CallAssistantStatus
import com.example.domain.model.CallEvent
import com.example.domain.model.CallSummary
import com.example.domain.model.CustomerContext
import com.example.domain.model.OrderSummary
import com.example.integration.TaloolaPosCallerGateway
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

class MockTaloolaPosCallerGateway : TaloolaPosCallerGateway {

    private val mockDatabase = ConcurrentHashMap<String, CustomerContext>()

    init {
        // Mock Customer 1: Known regular customer (Mohammed Ahmed)
        val customer1Norm = "07751234567"
        mockDatabase[customer1Norm] = CustomerContext(
            customerId = "CUST-1001",
            phone = "07751234567",
            normalizedPhone = customer1Norm,
            name = "محمد أحمد",
            status = "عميل معروف",
            lastCall = CallSummary(
                callId = "CALL-8841",
                direction = "INCOMING",
                startedAtUtc = "24/09/2026 - 08:15 م",
                endedAtUtc = "24/09/2026 - 08:17 م",
                durationSeconds = 120
            ),
            lastOrder = OrderSummary(
                orderNumber = "#10254",
                orderType = "دلفري",
                total = "18,500 د.ع",
                status = "تم التوصيل",
                createdAtUtc = "24/09/2026"
            ),
            orderCount = 14,
            area = "المنصور",
            address = "شارع الأميرات، قرب مول بابليون، زقاق 12",
            latitude = 33.3088,
            longitude = 44.3562,
            notes = "يفضل تقليل الفلفل الحار، زبون مميز ودائم الطلب",
            retrievedAtUtc = "2026-09-24T15:28:00Z"
        )

        // Mock Customer 2: Known customer (Haidar Ali)
        val customer2Norm = "07751230000"
        mockDatabase[customer2Norm] = CustomerContext(
            customerId = "CUST-1002",
            phone = "07751230000",
            normalizedPhone = customer2Norm,
            name = "حيدر علي",
            status = "عميل معروف",
            lastCall = CallSummary(
                callId = "CALL-8790",
                direction = "INCOMING",
                startedAtUtc = "23/09/2026 - 02:40 م",
                endedAtUtc = "23/09/2026 - 02:42 م",
                durationSeconds = 95
            ),
            lastOrder = OrderSummary(
                orderNumber = "#10190",
                orderType = "سفري",
                total = "32,000 د.ع",
                status = "مكتمل",
                createdAtUtc = "23/09/2026"
            ),
            orderCount = 6,
            area = "الكرادة",
            address = "ساحة الواثق، مجمع النور الطبي، الطابق 2",
            latitude = 33.3120,
            longitude = 44.4250,
            notes = "اتصال قبل التجهيز بـ 10 دقائق، يرغب بصلصات إضافية",
            retrievedAtUtc = "2026-09-23T11:40:00Z"
        )

        // Customer 3: 07801234567 is explicitly unassigned to represent new/unknown caller
    }

    override suspend fun getCallerCustomerContext(phone: String): Result<CustomerContext> {
        delay(150) // Simulate fast network round-trip
        val norm = PhoneNormalizer.normalize(phone)
        val customer = mockDatabase[norm]
        return if (customer != null) {
            Result.success(customer)
        } else {
            // Unknown customer response
            val newCustomer = CustomerContext(
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
                notes = "لا توجد ملاحظات سابقة لهذا الرقم",
                retrievedAtUtc = "2026-09-24T15:28:52Z"
            )
            Result.success(newCustomer)
        }
    }

    override suspend fun getCallerLastOrders(phone: String): Result<List<OrderSummary>> {
        val norm = PhoneNormalizer.normalize(phone)
        val customer = mockDatabase[norm]
        return if (customer?.lastOrder != null) {
            Result.success(listOf(customer.lastOrder))
        } else {
            Result.success(emptyList())
        }
    }

    override suspend fun getCallerLocation(phone: String): Result<Pair<Double, Double>?> {
        val norm = PhoneNormalizer.normalize(phone)
        val customer = mockDatabase[norm]
        return if (customer?.hasCoordinates == true) {
            Result.success(customer.latitude!! to customer.longitude!!)
        } else {
            Result.success(null)
        }
    }

    override suspend fun recordCallerCall(callEvent: CallEvent): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun sendCallerContextToCashier(context: CustomerContext): Result<Boolean> {
        delay(100)
        return Result.success(true)
    }

    override suspend fun getCallerAssistantStatus(): Result<CallAssistantStatus> {
        return Result.success(
            CallAssistantStatus(
                isConnected = true,
                capabilityGranted = true,
                serverId = "taloola-mock-v3",
                activeCallsCount = 1,
                lastHeartbeatUtc = "2026-09-24T15:28:52Z"
            )
        )
    }

    override suspend fun createCustomer(
        phone: String,
        name: String,
        area: String,
        address: String
    ): Result<Boolean> {
        val norm = PhoneNormalizer.normalize(phone)
        val newRecord = CustomerContext(
            customerId = "CUST-" + (1000 + mockDatabase.size + 1),
            phone = phone,
            normalizedPhone = norm,
            name = name,
            status = "عميل معروف",
            lastCall = null,
            lastOrder = null,
            orderCount = 0,
            area = area.ifBlank { "غير متوفر" },
            address = address.ifBlank { "غير متوفر" },
            latitude = null,
            longitude = null,
            notes = "تم إنشاء العميل من هاتف البدالة",
            retrievedAtUtc = "2026-09-24T15:28:52Z"
        )
        mockDatabase[norm] = newRecord
        return Result.success(true)
    }
}
