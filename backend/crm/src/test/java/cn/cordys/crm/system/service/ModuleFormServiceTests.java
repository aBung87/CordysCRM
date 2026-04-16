package cn.cordys.crm.system.service;

import cn.cordys.crm.system.domain.ModuleFieldBlob;
import cn.cordys.crm.system.dto.field.DatasourceField;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.mybatis.BaseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuleFormServiceTests {

    @Mock
    private BaseMapper<ModuleFieldBlob> moduleFieldBlobMapper;

    @InjectMocks
    private ModuleFormService moduleFormService;

    @Test
    void flattenSourceRefFieldsShouldIgnoreDatasourceFieldsWithoutRefFields() {
        DatasourceField datasourceField = new DatasourceField();
        datasourceField.setId("invoiceBusinessTitle");
        datasourceField.setType("DATA_SOURCE");
        datasourceField.setShowFields(List.of("name"));
        datasourceField.setRefFields(null);

        List<BaseField> fields = List.of(datasourceField);
        when(moduleFieldBlobMapper.selectByIds(List.of("name"))).thenReturn(List.of());

        assertDoesNotThrow(() -> {
            List<BaseField> result = moduleFormService.flattenSourceRefFields(fields, new HashMap<>());
            assertEquals(1, result.size());
            assertEquals("invoiceBusinessTitle", result.getFirst().getId());
        });
    }
}
