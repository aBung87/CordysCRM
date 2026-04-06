package cn.cordys.common.security;

import cn.cordys.common.util.CommonBeanFactory;
import cn.cordys.crm.system.domain.UserKey;
import cn.cordys.crm.system.service.UserKeyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyHandlerTests {

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(CommonBeanFactory.class, "context", null);
    }

    @Test
    void skillHeadersAuthenticateWithPlainAccessAndSecretKeys() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        UserKeyService userKeyService = mock(UserKeyService.class);
        ReflectionTestUtils.setField(CommonBeanFactory.class, "context", applicationContext);
        when(applicationContext.getBean(UserKeyService.class)).thenReturn(userKeyService);

        UserKey userKey = new UserKey();
        userKey.setAccessKey("access-key");
        userKey.setSecretKey("secret-key");
        userKey.setCreateUser("user-1");
        userKey.setEnable(true);
        userKey.setForever(true);
        when(userKeyService.getUserKey("access-key")).thenReturn(userKey);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiKeyHandler.X_ACCESS_KEY, "access-key");
        request.addHeader(ApiKeyHandler.X_SECRET_KEY, "secret-key");

        assertThat(ApiKeyHandler.isApiKeyCall(request)).isTrue();
        assertThat(ApiKeyHandler.getUser(request)).isEqualTo("user-1");
    }
}
