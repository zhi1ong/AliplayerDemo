package com.example.demo;

import com.alibaba.fastjson.JSON;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.aliyuncs.sts.model.v20150401.AssumeRoleRequest;
import com.aliyuncs.sts.model.v20150401.AssumeRoleResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * STS Server
 *
 * @author zhilong <a href="mailto:zhilong.lzl@alibaba-inc.com">Contact me.</a>
 * @version 1.0
 * @since 2018/1/8 下午8:13
 */
@Controller
@RequestMapping("/sts")
public class StsController {
    private static final Logger logger = LoggerFactory.getLogger("sts-server");

    @Value("${REGION}")
    private String region;
    @Value("${STS_API_VERSION}")
    private String stsApiVersion;
    @Value("${ACCESS_KEY_ID}")
    private String accessKeyId;
    @Value("${ACCESS_KEY_SECRET}")
    private String accessKeySecret;
    @Value("${STS_ARN}")
    private String roleArn;
    private static final String ROLE_SESSION_NAME = "aliplayer-001";

    @ResponseBody
    @RequestMapping("/get")
    public String getToken() {
        // 此处必须为 HTTPS
        ProtocolType protocolType = ProtocolType.HTTPS;
        try {
            final AssumeRoleResponse response = assumeRole(accessKeyId, accessKeySecret,
                    roleArn, ROLE_SESSION_NAME, protocolType);
            logger.info("Expiration: {}", response.getCredentials().getExpiration());
            logger.info("Access Key Id: {}", response.getCredentials().getAccessKeyId());
            logger.info("Access Key Secret: {}", response.getCredentials().getAccessKeySecret());
            logger.info("Security Token: {}", response.getCredentials().getSecurityToken());

            return JSON.toJSONString(response.getCredentials());
        } catch (ClientException e) {
            logger.error("Failed to get a token, code = {}, message = {}.", e.getErrCode(), e.getErrMsg());
        }

        return "fail";
    }

    private AssumeRoleResponse assumeRole(String accessKeyId, String accessKeySecret,
                                                 String roleArn, String roleSessionName,
                                                 ProtocolType protocolType) throws ClientException {
        // 创建一个 Aliyun Acs Client, 用于发起 OpenAPI 请求
        IClientProfile profile = DefaultProfile.getProfile(region, accessKeyId, accessKeySecret);
        DefaultAcsClient client = new DefaultAcsClient(profile);
        // 创建一个 AssumeRoleRequest 并设置请求参数
        final AssumeRoleRequest request = new AssumeRoleRequest();
        request.setVersion(stsApiVersion);
        request.setMethod(MethodType.POST);
        request.setProtocol(protocolType);
        request.setRoleArn(roleArn);
        request.setRoleSessionName(roleSessionName);
        // 发起请求，并得到response
        return client.getAcsResponse(request);
    }
}
