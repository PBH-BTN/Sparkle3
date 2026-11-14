package com.ghostchu.btn.sparkle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghostchu.btn.sparkle.controller.admin.dto.AfdianMessageImportModels.ImportRequest;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.service.tasks.TaskStatusService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AfdianMessageImportServiceTest {
    private final AfdianMessageClient client = mock(AfdianMessageClient.class);
    private final AfdianMessageLicenseWriter writer = mock(AfdianMessageLicenseWriter.class);
    private final TaskStatusService tasks = mock(TaskStatusService.class);
    private final TaskStatusService.TaskExecution execution = mock(TaskStatusService.TaskExecution.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AfdianEmailBindingService emailBinding = mock(AfdianEmailBindingService.class);
    private final AfdianMessageImportService service = new AfdianMessageImportService(
            client, new PbhLicenseDecoder(), writer, mock(AfdianSettingsService.class), tasks, emailBinding);

    @Test
    void scansEveryDialogAndEveryMessagePageIncludingOverlappingPages() throws Exception {
        when(tasks.begin(anyString(), anyString())).thenReturn(execution);
        when(client.dialogs("token", 1)).thenReturn(json.readTree("{\"total_page\":2,\"list\":[{\"user\":{\"user_id\":\"a\"}}]}"));
        when(client.dialogs("token", 2)).thenReturn(json.readTree("{\"total_page\":2,\"list\":[{\"user\":{\"user_id\":\"b\"}}]}"));
        var pair = PbhLicenseDecoderTest.pair();
        String key = PbhLicenseDecoderTest.encode(pair, PbhLicenseDecoderTest.payload(""));
        String secondKey = PbhLicenseDecoderTest.encode(pair, PbhLicenseDecoderTest.payload(",\"hidden\":\"second\""));
        when(client.messages("token", "a", "0")).thenReturn(json.readTree(
                "{\"has_more\":1,\"list\":[{\"message\":{\"msg_id\":30,\"content\":\"第一份：" + key + "；第二份：" + secondKey + " 后缀\"}},{\"message\":{\"msg_id\":20,\"content\":\"plain\"}}]}"));
        when(client.messages("token", "a", "20")).thenReturn(json.readTree(
                "{\"has_more\":0,\"list\":[{\"message\":{\"msg_id\":20,\"content\":\"plain\"}},{\"message\":{\"msg_id\":10,\"content\":{\"text\":\"" + key + "\"}}}]}"));
        when(client.messages("token", "b", "0")).thenReturn(json.readTree("{\"has_more\":0,\"list\":[]}"));
        when(writer.register(eq("a"), anyString(), any(), eq(42L))).thenReturn("IMPORTED", "IMPORTED", "DUPLICATE");
        service.run("token", List.of(pair.getPublic()), 42L);
        assertEquals("COMPLETED", service.status().state());
        assertEquals(2, service.status().dialogs());
        assertEquals(3, service.status().messages());
        assertEquals(2, service.status().imported());
        assertEquals(1, service.status().duplicates());
        verify(writer).register(eq("a"), eq("30"), any(), eq(42L));
        verify(writer).register(eq("a"), eq("10"), any(), eq(42L));
        verify(execution).success();
    }

    @Test
    void marksIncompleteScanFailedInsteadOfSilentlyTruncatingRepeatedPages() throws Exception {
        when(tasks.begin(anyString(), anyString())).thenReturn(execution);
        when(client.dialogs(eq("token"), anyInt())).thenReturn(json.readTree("{\"has_more\":1,\"list\":[]}"));
        service.run("token", List.of(), 42L);
        assertEquals("FAILED", service.status().state());
        verify(execution).failure(anyString());
        verify(execution, never()).success();
    }

    @Test
    void matchesMultipleLicensesToPrecedingCardsAcrossPagesAndUsesOriginalCardForResentKeys() throws Exception {
        when(tasks.begin(anyString(), anyString())).thenReturn(execution);
        when(client.dialogs("token", 1)).thenReturn(json.readTree("{\"total_page\":1,\"list\":[{\"user\":{\"user_id\":\"a\"}}]}"));
        var pair = PbhLicenseDecoderTest.pair();
        String original = PbhLicenseDecoderTest.encode(pair, PbhLicenseDecoderTest.payload(""));
        String second = PbhLicenseDecoderTest.encode(pair, PbhLicenseDecoderTest.payload(",\"hidden\":\"second\""));
        String third = PbhLicenseDecoderTest.encode(pair, PbhLicenseDecoderTest.payload(",\"hidden\":\"third\""));
        // Afdian returns each page oldest-first, even though pages move backwards in history.
        when(client.messages("token", "a", "0")).thenReturn(json.readTree(
                "{\"has_more\":1,\"list\":[" + card(50, "order-b") + ","
                        + message(60, original + "，另一份：" + second) + "]}"));
        when(client.messages("token", "a", "50")).thenReturn(json.readTree(
                "{\"has_more\":0,\"list\":[" + card(10, "order-a") + ","
                        + message(20, original + "，另一份：" + third) + "," + message(30, "感谢支持") + "]}"));
        when(writer.register(eq("a"), anyString(), any(), eq(42L), any())).thenReturn("IMPORTED");
        service.run("token", List.of(pair.getPublic()), 42L);
        assertEquals("COMPLETED", service.status().state());
        assertEquals(3, service.status().imported());
        assertEquals(1, service.status().duplicates());
        verify(writer).register(eq("a"), eq("20"), argThat(l -> l.key().equals(original)), eq(42L),
                argThat(card -> card.orderId().equals("order-a") && card.sku().equals("sku-1")));
        verify(writer).register(eq("a"), eq("20"), argThat(l -> l.key().equals(third)), eq(42L),
                argThat(card -> card.orderId().equals("order-a")));
        verify(writer).register(eq("a"), eq("60"), argThat(l -> l.key().equals(second)), eq(42L),
                argThat(card -> card.orderId().equals("order-b")));
        verify(writer, never()).register(anyString(), anyString(), any(), any());
    }

    private String card(int id, String order) {
        return "{\"type\":\"receive\",\"message\":{\"msg_id\":" + id
                + ",\"type\":2,\"content\":{\"out_trade_no\":\"" + order
                + "\",\"total_amount\":\"50.00\",\"product_type\":1,\"sku_detail\":[{\"sku_id\":\"sku-1\"}]}}}";
    }

    private String message(int id, String content) throws Exception {
        return "{\"type\":\"send\",\"message\":{\"msg_id\":" + id + ",\"type\":4,\"content\":"
                + json.writeValueAsString(content) + "}}";
    }

    @Test
    void receiptRemarkCanLinkAnAccountEvenWithoutLicenseMessages() throws Exception {
        when(tasks.begin(anyString(), anyString())).thenReturn(execution);
        when(client.dialogs("token", 1)).thenReturn(json.readTree("{\"total_page\":1,\"list\":[{\"user\":{\"user_id\":\"a\"}}]}"));
        when(client.messages("token", "a", "0")).thenReturn(json.readTree("""
                {"has_more":0,"list":[{"type":"receive","message":{"msg_id":10,"type":2,"content":{
                  "out_trade_no":"order-1","total_amount":"50.00","remark":"请绑定 buyer@example.test"}}}]}
                """));
        service.run("token", List.of(), 42L);
        assertEquals("COMPLETED", service.status().state());
        verify(emailBinding).bind("a", "order-1", "请绑定 buyer@example.test");
        verifyNoInteractions(writer);
    }

    @Test
    void rejectsInvalidCredentialsBeforeStartingAnyTask() {
        assertThrows(BusinessException.class, () -> service.start(new ImportRequest("abc;other=value", null), 42L));
        assertThrows(BusinessException.class, () -> service.start(new ImportRequest("", null), 42L));
        assertEquals("IDLE", service.status().state());
        verifyNoInteractions(client, tasks, writer);
    }

    @Test
    void refusesOverlappingScansWithConflictAndAllowsAnotherScanAfterCompletion() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var completed = new java.util.concurrent.CountDownLatch(1);
        when(tasks.begin(anyString(), anyString())).thenReturn(execution);
        doAnswer(invocation -> { completed.countDown(); return null; }).when(execution).success();
        when(client.dialogs("token", 1)).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            return json.readTree("{\"has_more\":0,\"list\":[]}");
        });
        service.start(new ImportRequest("token", null), 42L);
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var error = assertThrows(BusinessException.class,
                    () -> service.start(new ImportRequest("token", null), 42L));
            assertEquals(409, error.getStatusCode().value());
        } finally {
            release.countDown();
        }
        assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals("COMPLETED", service.status().state());
        assertDoesNotThrow(() -> service.start(new ImportRequest("token", null), 42L));
    }
}
