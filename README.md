### 使用 VID + STS 的方式点视频

#### 一、准备的材料
- 一个视频文件
- 一个阿里云账号, [官网](https://www.aliyun.com)
- 申请一个阿里云子账号 (用于访问 sts 的 openapi)
- 一个域名 (用于 cname 到阿里云的加速域名)
- 下载阿里云播放器的 sdk ，[官网文档](https://promotion.aliyun.com/ntms/act/video.html)

#### 二、搭建 STS-Server
1. 初始化工程，推荐使用 maven 模板来创建
``` bash
mvn archetype:create -DgroupId=com.aliyun -DartifactId=aliplayerdemo -DpackageName=com.aliyun.demo.aliplayer -DarchetypeArtifactId=maven-archetype-webapp
```

2. 修改 pom.xml，添加依赖项
``` xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<groupId>com.example</groupId>
	<artifactId>demo</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<packaging>jar</packaging>

	<name>demo</name>
	<description>Demo project for Spring Boot</description>

	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>1.5.9.RELEASE</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>

	<properties>
		<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
		<project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
		<java.version>1.8</java.version>
	</properties>

	<dependencies>
        <!--Spring Boot + Spring MVC-->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>

        <!--STS SDK-->
        <dependency>
            <groupId>com.aliyun</groupId>
            <artifactId>aliyun-java-sdk-sts</artifactId>
            <version>2.1.6</version>
        </dependency>
        <dependency>
            <groupId>com.aliyun</groupId>
            <artifactId>aliyun-java-sdk-core</artifactId>
            <version>2.1.7</version>
        </dependency>

        <!--JSON-->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>fastjson</artifactId>
            <version>1.2.39</version>
        </dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
    </dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>


</project>
```
3. 设置配置项 src/main/resources/application.properties
```
# STS Config
ACCESS_KEY_ID=你的子账号饿 access key id
ACCESS_KEY_SECRET=你的子账号的 access key secret
STS_ARN=替换成实际的角色 arn 信息

# STS Open api config
REGION=cn-hangzhou
STS_API_VERSION=2015-04-01
```

4. 添加一个 Controller 用来处理 sts token 的颁发
```java
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

```

5. 添加 Application 入口
```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyApplication {

	public static void main(String[] args) {
		SpringApplication.run(MyApplication.class, args);
	}
}

```

6. 启动服务器后，默认监听 8080 端口，可以打开浏览器访问  http://localhost:8080/sts/get ,查看效果，以上文件均可以在 Demo 中找到

#### 三、上传视频
- 进入视频点播产品[控制台](https://vod.console.aliyun.com)
- 点击左侧菜单中的 视频
- 点击 上传视频 按钮
- 选择准备好的视频，等待上传完成，上传完成之后，可以拿到一个 vid (唯一标识一个视频)

#### 四、集成播放器 sdk 到 Android 工程
1. 新建一个 Android 工程，推荐使用 Android Studio 进行开发
2. 复制 sdk 中的 aar 文件到 libs 目录下
3. 这部分代码内容较多，请参考 Demo 工程进行配置 
