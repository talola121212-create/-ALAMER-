package com.example.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.domain.PhoneNormalizer
import com.example.domain.model.CustomerContext
import com.example.domain.model.OrderSummary

@Composable
fun CreateCustomerDialog(
    phone: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, area: String, address: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إنشاء عميل جديد في TaloolaPos",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = PhoneNormalizer.formatForDisplay(phone),
                    onValueChange = {},
                    label = { Text("رقم الهاتف") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم العميل *") },
                    placeholder = { Text("مثال: علي حسن") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_name_input")
                )

                OutlinedTextField(
                    value = area,
                    onValueChange = { area = it },
                    label = { Text("المنطقة") },
                    placeholder = { Text("مثال: المنصور، الكرادة، زيونة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_area_input")
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("العنوان التفصيلي") },
                    placeholder = { Text("الشارع، أقرب نقطة دالة، رقم الزقاق") },
                    modifier = Modifier.fillMaxWidth().testTag("customer_address_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), area.trim(), address.trim())
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_customer_button")
            ) {
                Text("حفظ في TaloolaPos")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun CustomerDetailsDialog(
    customer: CustomerContext,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تفاصيل العميل - TaloolaPos",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = customer.name.ifBlank { "عميل جديد" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "الهاتف: ${PhoneNormalizer.formatForDisplay(customer.phone)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "عدد الطلبات السابقة: ${customer.orderCount}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "المنطقة: ${customer.area.ifBlank { "غير متوفر" }}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "العنوان: ${customer.address.ifBlank { "غير متوفر" }}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (customer.hasCoordinates) {
                    Text(
                        text = "الإحداثيات: ${customer.latitude}, ${customer.longitude}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (customer.notes.isNotBlank()) {
                    Text(
                        text = "ملاحظات الدلفري: ${customer.notes}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun LastOrderDialog(
    order: OrderSummary,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تفاصيل آخر طلب",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "رقم الطلب: ${order.orderNumber}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = "نوع الطلب: ${order.orderType}")
                Text(
                    text = "الإجمالي: ${order.total}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(text = "الحالة: ${order.status}")
                if (order.createdAtUtc.isNotBlank()) {
                    Text(text = "تاريخ الطلب: ${order.createdAtUtc}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("تم")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
