package cn.cordys.common.security;

import cn.cordys.common.util.CodingUtils;
import cn.cordys.common.util.CommonBeanFactory;
import cn.cordys.crm.system.domain.UserKey;
import cn.cordys.crm.system.service.UserKeyService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.Objects;

public class ApiKeyHandler {

    public static final String AUTHORIZATION = "Authorization";
    public static final String X_ACCESS_KEY = "X-Access-Key";
    public static final String X_SECRET_KEY = "X-Secret-Key";

    public static String getUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String accessKey = request.getHeader(X_ACCESS_KEY);
        String secretKey = request.getHeader(X_SECRET_KEY);
        if (StringUtils.isNotBlank(accessKey) || StringUtils.isNotBlank(secretKey)) {
            return getUserByHeaderKeys(accessKey, secretKey);
        }

        String authorization = request.getHeader(AUTHORIZATION);
        if (StringUtils.isBlank(authorization)) {
            return null;
        }

        String[] authParts = authorization.split(":", 2);
        if (authParts.length < 2) {
            return null;
        }

        accessKey = authParts[0];
        String signature = authParts[1];

        if (StringUtils.isBlank(accessKey) || StringUtils.isBlank(signature)) {
            return null;
        }

        return getUser(accessKey, signature);
    }

    public static Boolean isApiKeyCall(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if (StringUtils.isNotBlank(request.getHeader(X_ACCESS_KEY)) || StringUtils.isNotBlank(request.getHeader(X_SECRET_KEY))) {
            return true;
        }
        String authorization = request.getHeader(AUTHORIZATION);
        return !StringUtils.isBlank(authorization) && authorization.split(":").length >= 2;
    }

    public static String getUser(String accessKey, String signature) {
        if (StringUtils.isBlank(accessKey) || StringUtils.isBlank(signature)) {
            return null;
        }

        UserKey userKey = Objects.requireNonNull(CommonBeanFactory.getBean(UserKeyService.class)).getUserKey(accessKey);
        if (userKey == null) {
            throw new RuntimeException("invalid accessKey");
        }

        validateUserKey(userKey);

        String signatureDecrypt;
        try {
            signatureDecrypt = CodingUtils.aesDecrypt(signature, userKey.getSecretKey(), accessKey.getBytes());
        } catch (Throwable t) {
            throw new RuntimeException("invalid signature", t);
        }

        String[] signatureArray = StringUtils.split(StringUtils.trimToNull(signatureDecrypt), "|");
        if (signatureArray.length < 2) {
            throw new RuntimeException("invalid signature");
        }

        if (!Strings.CS.equals(accessKey, signatureArray[0])) {
            throw new RuntimeException("invalid signature");
        }

        long signatureTime;
        try {
            signatureTime = Long.parseLong(signatureArray[signatureArray.length - 1]);
        } catch (Exception e) {
            throw new RuntimeException("invalid signature time", e);
        }

        if (Math.abs(System.currentTimeMillis() - signatureTime) > 1800000) {
            throw new RuntimeException("expired signature");
        }

        return userKey.getCreateUser();
    }

    private static String getUserByHeaderKeys(String accessKey, String secretKey) {
        if (StringUtils.isBlank(accessKey) || StringUtils.isBlank(secretKey)) {
            return null;
        }

        UserKey userKey = Objects.requireNonNull(CommonBeanFactory.getBean(UserKeyService.class)).getUserKey(accessKey);
        if (userKey == null) {
            throw new RuntimeException("invalid accessKey");
        }

        validateUserKey(userKey);

        if (!Strings.CS.equals(secretKey, userKey.getSecretKey())) {
            throw new RuntimeException("invalid secretKey");
        }

        return userKey.getCreateUser();
    }

    private static void validateUserKey(UserKey userKey) {
        if (BooleanUtils.isFalse(userKey.getEnable())) {
            throw new RuntimeException("accessKey is disabled");
        }

        if (BooleanUtils.isFalse(userKey.getForever())
                && (userKey.getExpireTime() == null || userKey.getExpireTime() < System.currentTimeMillis())) {
            throw new RuntimeException("accessKey is expired");
        }
    }
}
