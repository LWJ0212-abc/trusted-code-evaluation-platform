package lwj.code.server.bootstrap.config;



import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class SpringDocConfig {

    @Bean
    public OpenAPI openAPI(){
        log.info("开始加载openAPI配置");
        return new OpenAPI()
                .info(new Info()
                        .title("可信代码 API 文档")
                        .version("1.0")
                        .description("后端 API 接口介绍"))
                .components(new Components().addSecuritySchemes(
                        "platformSession",
                        new SecurityScheme()
                                .name("platform_session")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                ));
    }

}
