package com.gogoga.backend.service;

import com.gogoga.backend.config.PowerProperties;
import com.gogoga.backend.dto.PowerConsumeRequestDTO;
import com.gogoga.backend.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MtPaidApiPowerConsumeServiceTest {

    @Mock
    private MtPowerConsumeService mtPowerConsumeService;

    @Test
    void shouldSwallowInsufficientPowerInRequiresNewTransaction() {
        MtPaidApiPowerConsumeService service = new MtPaidApiPowerConsumeService(
                mtPowerConsumeService,
                new PowerProperties(),
                new NoopTransactionManager()
        );
        when(mtPowerConsumeService.consume(any())).thenThrow(new BusinessException(400, "当前算力余额不足"));

        assertDoesNotThrow(() -> service.tryConsumeOrderDetailQuery(1L, "10001"));

        ArgumentCaptor<PowerConsumeRequestDTO> requestCaptor = ArgumentCaptor.forClass(PowerConsumeRequestDTO.class);
        verify(mtPowerConsumeService).consume(requestCaptor.capture());
        assertEquals(MtPaidApiPowerConsumeService.CONSUME_SOURCE_ORDER_DETAIL, requestCaptor.getValue().getSourceType());
    }

    private static class NoopTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
