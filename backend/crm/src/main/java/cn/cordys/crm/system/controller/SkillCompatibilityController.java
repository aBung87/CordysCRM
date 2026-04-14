package cn.cordys.crm.system.controller;

import cn.cordys.common.constants.FormKey;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.pager.PagerWithOption;
import cn.cordys.common.utils.ConditionFilterUtils;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.contract.service.BusinessTitleService;
import cn.cordys.crm.customer.dto.request.CustomerContactPageRequest;
import cn.cordys.crm.customer.dto.response.CustomerContactGetResponse;
import cn.cordys.crm.customer.dto.response.CustomerContactListResponse;
import cn.cordys.crm.customer.service.CustomerContactService;
import cn.cordys.crm.system.constants.UserViewResourceType;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.dto.response.ModuleFormConfigDTO;
import cn.cordys.crm.system.dto.response.UserViewListResponse;
import cn.cordys.crm.system.service.ModuleFormCacheService;
import cn.cordys.crm.system.service.UserViewService;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.shiro.SecurityUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Skill Compatibility")
public class SkillCompatibilityController {

    @Resource
    private ModuleFormCacheService moduleFormCacheService;
    @Resource
    private CustomerContactService customerContactService;
    @Resource
    private UserViewService userViewService;
    @Resource
    private BusinessTitleService businessTitleService;
    @Resource
    private cn.cordys.common.service.DataScopeService dataScopeService;

    @GetMapping("/settings/fields")
    @Operation(summary = "Skill compatibility: resolve module field metadata")
    public List<BaseField> getFields(@RequestParam String module) {
        requireModulePermission(module);
        return resolveFormConfig(module).getFields();
    }

    @GetMapping("/contact/module/form")
    @Operation(summary = "Skill compatibility: contact module form")
    public ModuleFormConfigDTO getContactModuleForm() {
        SecurityUtils.getSubject().checkPermission(PermissionConstants.CUSTOMER_MANAGEMENT_CONTACT_READ);
        return moduleFormCacheService.getBusinessFormConfig(FormKey.CONTACT.getKey(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/contact/page")
    @Operation(summary = "Skill compatibility: contact page list")
    public PagerWithOption<List<CustomerContactListResponse>> contactPage(@Validated @RequestBody CustomerContactPageRequest request) {
        SecurityUtils.getSubject().checkPermission(PermissionConstants.CUSTOMER_MANAGEMENT_CONTACT_READ);
        ConditionFilterUtils.parseCondition(request, FormKey.CONTACT.getKey());
        DeptDataPermissionDTO deptDataPermission = dataScopeService.getDeptDataPermission(
                SessionUtils.getUserId(),
                OrganizationContext.getOrganizationId(),
                request.getViewId(),
                PermissionConstants.CUSTOMER_MANAGEMENT_CONTACT_READ
        );
        return customerContactService.list(request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId(), deptDataPermission);
    }

    @GetMapping({"/contact/get/{id}", "/contact/{id}"})
    @Operation(summary = "Skill compatibility: contact detail")
    public CustomerContactGetResponse contactGet(@org.springframework.web.bind.annotation.PathVariable String id) {
        SecurityUtils.getSubject().checkPermission(PermissionConstants.CUSTOMER_MANAGEMENT_CONTACT_READ);
        return customerContactService.get(id);
    }

    @GetMapping("/contact/view/list")
    @Operation(summary = "Skill compatibility: contact views")
    public List<UserViewListResponse> contactViewList() {
        SecurityUtils.getSubject().checkPermission(PermissionConstants.CUSTOMER_MANAGEMENT_CONTACT_READ);
        return userViewService.list(UserViewResourceType.CUSTOMER_CONTACT.name(), SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    private ModuleFormConfigDTO resolveFormConfig(String module) {
        String organizationId = OrganizationContext.getOrganizationId();
        return switch (module) {
            case "lead", "pool/lead", "lead-pool" ->
                    moduleFormCacheService.getBusinessFormConfig(FormKey.CLUE.getKey(), organizationId);
            case "account", "pool/account", "account-pool" ->
                    moduleFormCacheService.getBusinessFormConfig(FormKey.CUSTOMER.getKey(), organizationId);
            case "contact" ->
                    moduleFormCacheService.getBusinessFormConfig(FormKey.CONTACT.getKey(), organizationId);
            case "opportunity" ->
                    moduleFormCacheService.getBusinessFormConfig(FormKey.OPPORTUNITY.getKey(), organizationId);
            case "contract" ->
                    moduleFormCacheService.getBusinessFormConfig(FormKey.CONTRACT.getKey(), organizationId);
            case "product" ->
                    moduleFormCacheService.getBusinessFormConfig(FormKey.PRODUCT.getKey(), organizationId);
            case "invoice" ->
                    moduleFormCacheService.getBusinessFormConfig(FormKey.INVOICE.getKey(), organizationId);
            case "contract/payment-plan" ->
                    moduleFormCacheService.getBusinessFormConfig(FormKey.CONTRACT_PAYMENT_PLAN.getKey(), organizationId);
            case "contract/payment-record" ->
                    moduleFormCacheService.getBusinessFormConfig(FormKey.CONTRACT_PAYMENT_RECORD.getKey(), organizationId);
            case "opportunity/quotation" ->
                    moduleFormCacheService.getBusinessFormConfig(FormKey.QUOTATION.getKey(), organizationId);
            case "order" ->
                    moduleFormCacheService.getBusinessFormConfig(FormKey.ORDER.getKey(), organizationId);
            case "contract/business-title" ->
                    businessTitleService.getBusinessFormConfig();
            default -> throw new IllegalArgumentException("Unsupported module: " + module);
        };
    }

    private void requireModulePermission(String module) {
        String permission = switch (module) {
            case "lead", "lead-pool" -> PermissionConstants.CLUE_MANAGEMENT_READ;
            case "pool/lead" -> PermissionConstants.CLUE_MANAGEMENT_POOL_READ;
            case "account", "account-pool" -> PermissionConstants.CUSTOMER_MANAGEMENT_READ;
            case "pool/account" -> PermissionConstants.CUSTOMER_MANAGEMENT_POOL_READ;
            case "contact" -> PermissionConstants.CUSTOMER_MANAGEMENT_CONTACT_READ;
            case "opportunity" -> PermissionConstants.OPPORTUNITY_MANAGEMENT_READ;
            case "contract" -> PermissionConstants.CONTRACT_READ;
            case "product" -> PermissionConstants.PRODUCT_MANAGEMENT_READ;
            case "invoice" -> PermissionConstants.CONTRACT_INVOICE_READ;
            case "contract/payment-plan" -> PermissionConstants.CONTRACT_PAYMENT_PLAN_READ;
            case "contract/payment-record" -> PermissionConstants.CONTRACT_PAYMENT_RECORD_READ;
            case "opportunity/quotation" -> PermissionConstants.OPPORTUNITY_QUOTATION_READ;
            case "contract/business-title" -> PermissionConstants.CONTRACT_BUSINESS_TITLE_READ;
            case "order" -> PermissionConstants.ORDER_READ;
            default -> throw new IllegalArgumentException("Unsupported module: " + module);
        };
        SecurityUtils.getSubject().checkPermission(permission);
    }
}
