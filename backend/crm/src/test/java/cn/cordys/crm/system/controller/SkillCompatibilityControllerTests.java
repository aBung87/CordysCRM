package cn.cordys.crm.system.controller;

import cn.cordys.common.constants.FormKey;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.pager.PagerWithOption;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.clue.controller.ClueController;
import cn.cordys.crm.clue.dto.response.ClueGetResponse;
import cn.cordys.crm.clue.service.ClueService;
import cn.cordys.crm.contract.service.BusinessTitleService;
import cn.cordys.crm.customer.dto.request.CustomerContactPageRequest;
import cn.cordys.crm.customer.dto.response.CustomerContactGetResponse;
import cn.cordys.crm.customer.dto.response.CustomerContactListResponse;
import cn.cordys.crm.customer.service.CustomerContactService;
import cn.cordys.crm.system.constants.UserViewResourceType;
import cn.cordys.crm.system.dto.field.InputField;
import cn.cordys.crm.system.dto.response.ModuleFormConfigDTO;
import cn.cordys.crm.system.dto.response.UserViewListResponse;
import cn.cordys.crm.system.service.ModuleFormCacheService;
import cn.cordys.crm.system.service.UserViewService;
import cn.cordys.security.SessionConstants;
import cn.cordys.security.SessionUser;
import jakarta.servlet.ServletException;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SkillCompatibilityControllerTests {

    private static final String ORGANIZATION_ID = "100001";
    private static final String USER_ID = "skill-user";

    @Mock
    private ModuleFormCacheService moduleFormCacheService;
    @Mock
    private CustomerContactService customerContactService;
    @Mock
    private UserViewService userViewService;
    @Mock
    private BusinessTitleService businessTitleService;
    @Mock
    private cn.cordys.common.service.DataScopeService dataScopeService;
    @Mock
    private ClueService clueService;
    @Mock
    private Subject subject;
    @Mock
    private Session session;

    private MockMvc skillMockMvc;
    private MockMvc clueMockMvc;

    @BeforeEach
    void setUp() {
        SkillCompatibilityController skillCompatibilityController = new SkillCompatibilityController();
        ReflectionTestUtils.setField(skillCompatibilityController, "moduleFormCacheService", moduleFormCacheService);
        ReflectionTestUtils.setField(skillCompatibilityController, "customerContactService", customerContactService);
        ReflectionTestUtils.setField(skillCompatibilityController, "userViewService", userViewService);
        ReflectionTestUtils.setField(skillCompatibilityController, "businessTitleService", businessTitleService);
        ReflectionTestUtils.setField(skillCompatibilityController, "dataScopeService", dataScopeService);
        skillMockMvc = MockMvcBuilders.standaloneSetup(skillCompatibilityController).build();

        ClueController clueController = new ClueController();
        ReflectionTestUtils.setField(clueController, "clueService", clueService);
        clueMockMvc = MockMvcBuilders.standaloneSetup(clueController).build();

        SessionUser sessionUser = new SessionUser();
        sessionUser.setId(USER_ID);
        sessionUser.setOrganizationIds(Set.of(ORGANIZATION_ID));
        sessionUser.setLastOrganizationId(ORGANIZATION_ID);
        when(subject.getSession()).thenReturn(session);
        when(session.getAttribute(SessionConstants.ATTR_USER)).thenReturn(sessionUser);
        lenient().doNothing().when(subject).checkPermission(any(String.class));
        ThreadContext.bind(subject);

        OrganizationContext.setOrganizationId(ORGANIZATION_ID);
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unbindSubject();
        OrganizationContext.clear();
    }

    @Test
    void settingsFieldsEndpointResolvesSkillModuleAlias() throws Exception {
        ModuleFormConfigDTO config = new ModuleFormConfigDTO();
        InputField field = new InputField();
        field.setId("customerName");
        field.setType("INPUT");
        field.setReadable(true);
        config.setFields(List.of(field));
        when(moduleFormCacheService.getBusinessFormConfig(FormKey.CUSTOMER.getKey(), ORGANIZATION_ID)).thenReturn(config);

        skillMockMvc.perform(get("/settings/fields").param("module", "account"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("customerName"));

        verify(subject).checkPermission(PermissionConstants.CUSTOMER_MANAGEMENT_READ);
        verify(moduleFormCacheService).getBusinessFormConfig(FormKey.CUSTOMER.getKey(), ORGANIZATION_ID);
    }

    @Test
    void contactCompatibilityEndpointsReturnSkillFriendlyPayloads() throws Exception {
        ModuleFormConfigDTO formConfig = new ModuleFormConfigDTO();
        formConfig.setFields(List.of());
        when(moduleFormCacheService.getBusinessFormConfig(FormKey.CONTACT.getKey(), ORGANIZATION_ID)).thenReturn(formConfig);

        CustomerContactListResponse listItem = new CustomerContactListResponse();
        listItem.setId("contact-1");
        PagerWithOption<List<CustomerContactListResponse>> pager = new PagerWithOption<>();
        pager.setList(List.of(listItem));
        pager.setCurrent(1);
        pager.setPageSize(10);
        pager.setTotal(1);
        when(dataScopeService.getDeptDataPermission(USER_ID, ORGANIZATION_ID, null, PermissionConstants.CUSTOMER_MANAGEMENT_CONTACT_READ))
                .thenReturn(new DeptDataPermissionDTO());
        when(customerContactService.list(any(CustomerContactPageRequest.class), eq(USER_ID), eq(ORGANIZATION_ID), any(DeptDataPermissionDTO.class)))
                .thenReturn(pager);

        CustomerContactGetResponse detail = new CustomerContactGetResponse();
        detail.setId("contact-1");
        when(customerContactService.get("contact-1")).thenReturn(detail);

        UserViewListResponse view = new UserViewListResponse();
        view.setId("view-1");
        view.setName("Default");
        when(userViewService.list(UserViewResourceType.CUSTOMER_CONTACT.name(), USER_ID, ORGANIZATION_ID))
                .thenReturn(List.of(view));

        skillMockMvc.perform(get("/contact/module/form"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        skillMockMvc.perform(post("/contact/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "current": 1,
                                  "pageSize": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.list[0].id").value("contact-1"));

        skillMockMvc.perform(get("/contact/contact-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("contact-1"));

        skillMockMvc.perform(get("/contact/view/list"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value("view-1"));
    }

    @Test
    void leadDetailAliasWorksForSkillStyleRoutes() throws Exception {
        ClueGetResponse response = new ClueGetResponse();
        response.setId("lead-1");
        when(clueService.getWithDataPermissionCheck("lead-1", USER_ID, ORGANIZATION_ID)).thenReturn(response);

        clueMockMvc.perform(get("/lead/lead-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("lead-1"));

        verify(clueService).getWithDataPermissionCheck("lead-1", USER_ID, ORGANIZATION_ID);
    }
}
