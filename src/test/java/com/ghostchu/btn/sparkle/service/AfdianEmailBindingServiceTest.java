package com.ghostchu.btn.sparkle.service;

import com.ghostchu.btn.sparkle.entity.User;
import com.ghostchu.btn.sparkle.exception.BusinessException;
import com.ghostchu.btn.sparkle.mapper.AfdianEmailBindingMapper;
import com.ghostchu.btn.sparkle.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AfdianEmailBindingServiceTest {
    private final AfdianEmailBindingMapper mapper = mock(AfdianEmailBindingMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final AfdianEmailBindingService service = new AfdianEmailBindingService(mapper, users);

    private User stubUser() {
        User user = new User().setId(42L).setEmail("buyer@example.test");
        when(mapper.findUsers("buyer@example.test")).thenReturn(List.of(user));
        when(users.selectForUpdate(42L)).thenReturn(user);
        when(mapper.insertLegacy(42L, "afdian-user", "order-1")).thenReturn(1);
        when(mapper.insertMember(42L, "afdian-user", "order-1")).thenReturn(1);
        return user;
    }

    @Test
    void bindsBothAccountModelsOnceAndKeepsRepeatedSyncIdempotent() throws Exception {
        stubUser();
        assertTrue(service.bind("afdian-user", "order-1", "请绑定buyer@example.test"));
        when(mapper.hasExternalBinding("afdian-user")).thenReturn(true);
        assertFalse(service.bind("afdian-user", "order-1", "buyer@example.test"));
        verify(mapper, times(1)).insertLegacy(42L, "afdian-user", "order-1");
        verify(mapper, times(1)).insertMember(42L, "afdian-user", "order-1");
    }

    @Test
    void doesNotOverrideEitherSideOfAnExistingBinding() throws Exception {
        stubUser();
        when(mapper.hasUserBinding(42L)).thenReturn(true);
        assertFalse(service.bind("afdian-user", "order-1", "buyer@example.test"));
        verify(mapper, never()).insertLegacy(anyLong(), anyString(), anyString());
        when(mapper.hasExternalBinding("afdian-user")).thenReturn(true);
        assertFalse(service.bind("afdian-user", "order-1", "buyer@example.test"));
    }

    @Test
    void skipsMissingAmbiguousBannedAndChangedEmailAccounts() throws Exception {
        assertFalse(service.bind("afdian-user", "order-1", "buyer@example.test"));
        User user = stubUser();
        when(mapper.findUsers("buyer@example.test")).thenReturn(List.of(user, new User().setId(43L)));
        assertFalse(service.bind("afdian-user", "order-1", "buyer@example.test"));
        when(mapper.findUsers("buyer@example.test")).thenReturn(List.of(user));
        user.setBannedAt(java.time.OffsetDateTime.now());
        assertFalse(service.bind("afdian-user", "order-1", "buyer@example.test"));
        user.setBannedAt(null).setEmail("changed@example.test");
        assertFalse(service.bind("afdian-user", "order-1", "buyer@example.test"));
        verify(mapper, never()).insertLegacy(anyLong(), anyString(), anyString());
    }

    @Test
    void conflictingSecondWriteRollsBackBothWritesThroughSpringTransactionProxy() {
        stubUser();
        when(mapper.insertMember(42L, "afdian-user", "order-1")).thenReturn(0);
        var tx = mock(org.springframework.transaction.PlatformTransactionManager.class);
        var status = new org.springframework.transaction.support.SimpleTransactionStatus();
        when(tx.getTransaction(any())).thenReturn(status);
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(tx,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var proxy = (AfdianEmailBindingService) factory.getProxy();
        assertThrows(BusinessException.class, () -> proxy.bind("afdian-user", "order-1", "buyer@example.test"));
        verify(tx).rollback(status);
        verify(tx, never()).commit(any());
    }
}
