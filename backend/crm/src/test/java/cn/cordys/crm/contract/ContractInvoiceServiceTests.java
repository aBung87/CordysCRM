package cn.cordys.crm.contract;

import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.service.BaseService;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.contract.domain.Contract;
import cn.cordys.crm.contract.domain.ContractInvoice;
import cn.cordys.crm.contract.domain.ContractInvoiceSnapshot;
import cn.cordys.crm.contract.dto.request.ContractInvoiceApprovalRequest;
import cn.cordys.crm.contract.dto.request.ContractInvoiceUpdateRequest;
import cn.cordys.crm.contract.mapper.ExtContractInvoiceMapper;
import cn.cordys.crm.contract.service.BusinessTitleService;
import cn.cordys.crm.contract.service.ContractInvoiceFieldService;
import cn.cordys.crm.contract.service.ContractInvoiceService;
import cn.cordys.crm.opportunity.constants.ApprovalState;
import cn.cordys.crm.system.constants.DictModule;
import cn.cordys.crm.system.dto.response.ModuleFormConfigDTO;
import cn.cordys.crm.system.service.DictService;
import cn.cordys.crm.system.service.LogService;
import cn.cordys.crm.system.service.ModuleFormCacheService;
import cn.cordys.crm.system.service.ModuleFormService;
import cn.cordys.mybatis.BaseMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractInvoiceServiceTests {

    @BeforeAll
    static void initTranslator() {
        ReflectionTestUtils.setField(Translator.class, "messageSource", new StaticMessageSource());
    }

    @Mock
    private ContractInvoiceFieldService invoiceFieldService;
    @Mock
    private BaseMapper<ContractInvoice> invoiceMapper;
    @Mock
    private BaseService baseService;
    @Mock
    private ModuleFormService moduleFormService;
    @Mock
    private BaseMapper<ContractInvoiceSnapshot> snapshotBaseMapper;
    @Mock
    private ExtContractInvoiceMapper extContractInvoiceMapper;
    @Mock
    private BaseMapper<ContractInvoice> contractInvoiceMapper;
    @Mock
    private ModuleFormCacheService moduleFormCacheService;
    @Mock
    private BaseMapper<Contract> contractMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private LogService logService;
    @Mock
    private BusinessTitleService businessTitleService;
    @Mock
    private DictService dictService;

    @InjectMocks
    private ContractInvoiceService contractInvoiceService;

    @Test
    void getShouldReturnNullWhenInvoiceMissing() {
        when(contractInvoiceMapper.selectByPrimaryKey("missing")).thenReturn(null);

        assertNull(contractInvoiceService.get("missing"));
        verify(moduleFormCacheService, never()).getBusinessFormConfig(any(), any());
    }

    @Test
    void getWithDataPermissionCheckShouldThrowBusinessErrorWhenInvoiceMissing() {
        when(contractInvoiceMapper.selectByPrimaryKey("missing")).thenReturn(null);

        GenericException exception = assertThrows(
                GenericException.class,
                () -> contractInvoiceService.getWithDataPermissionCheck("missing", "admin", "100001")
        );

        Assertions.assertEquals(Translator.get("resource.not.exist"), exception.getMessage());
        verify(dataScopeService, never()).checkDataPermission(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void updateShouldThrowBusinessErrorWhenInvoiceMissing() {
        ContractInvoiceUpdateRequest request = new ContractInvoiceUpdateRequest();
        request.setId("missing");
        request.setContractId("contract-id");
        request.setModuleFields(List.of(new BaseModuleFieldValue("field-id", "value")));
        request.setModuleFormConfigDTO(new ModuleFormConfigDTO());

        when(invoiceMapper.selectByPrimaryKey("missing")).thenReturn(null);

        GenericException exception = assertThrows(
                GenericException.class,
                () -> contractInvoiceService.update(request, "admin", "100001")
        );

        Assertions.assertEquals(Translator.get("invoice.not.exist"), exception.getMessage());
        verify(dataScopeService, never()).checkDataPermission(anyString(), anyString(), anyString(), anyString());
        verify(invoiceMapper, never()).update(any());
    }

    @Test
    void approvalShouldNotFailWhenSnapshotMissing() {
        ContractInvoice invoice = new ContractInvoice();
        invoice.setId("invoice-id");
        invoice.setName("Invoice A");
        invoice.setOwner("admin");
        invoice.setApprovalStatus(ApprovalState.APPROVING.toString());

        ContractInvoiceApprovalRequest request = new ContractInvoiceApprovalRequest();
        request.setId("invoice-id");
        request.setApprovalStatus(ApprovalState.APPROVED.toString());

        when(invoiceMapper.selectByPrimaryKey("invoice-id")).thenReturn(invoice);
        when(dictService.isDictConfigEnable(DictModule.INVOICE_APPROVAL.name(), "100001")).thenReturn(true);
        when(snapshotBaseMapper.selectListByLambda(any())).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> contractInvoiceService.approvalContractInvoice(request, "admin", "100001"));

        verify(dataScopeService).checkDataPermission("admin", "100001", "admin", "CONTRACT_INVOICE:APPROVAL");
        verify(invoiceMapper).update(invoice);
        verify(logService).add(any());
    }
}
