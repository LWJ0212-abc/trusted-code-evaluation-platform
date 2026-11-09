package lwj.code.server.shared.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;


@Getter
public enum ErrorCode {

    /**
     * 请求无效 (HTTP 400 Bad Request)
     * 场景：前端传来的参数缺失、格式错误、类型不匹配，或者缺少必填的请求头。
     * 示例：密码长度不够、邮箱格式错误、缺少验证码等。
     */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),

    /**
     * 身份未注册/用户不存在 (HTTP 401 Unauthorized / 404 Not Found)
     * 场景：用户尝试登录或进行操作时，系统发现该账号（手机号、邮箱或外部ID）在当前系统中尚未注册。
     * 示例：新用户直接使用第三方扫码登录，但该第三方账号还未与本系统的账号绑定。
     */
    IDENTITY_NOT_REGISTERED(HttpStatus.UNAUTHORIZED),

    /**
     * 身份提供方不可用 (HTTP 503 Service Unavailable)
     *
     * 场景：系统在尝试验证用户身份时，依赖的外部第三方认证服务（IdP）挂了或者网络不通。
     * 示例：使用微信、GitHub登录，或者调用内部 SSO 服务时，对方服务器未响应或超时。
     */
    IDENTITY_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

    /**
     * 未认证/未登录 (HTTP 401 Unauthorized)
     *
     * 场景：用户发起的请求缺少身份凭证（Token），或者凭证已过期、被篡改、失效。
     * 示例：未携带任何 Token 直接请求用户信息接口，或者携带了过期的 JWT 尝试访问。
     */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),

    /**
     * 资源不存在或不可访问 (HTTP 404 Not Found)
     *
     * 场景：请求的资源在系统中不存在、已被物理删除，或指向了无效的资源标识。
     * 示例：前端尝试查询一个已经被彻底删除的订单详情，或访问一个不存在的页面。
     */
    RESOURCE_NOT_ACCESSIBLE(HttpStatus.NOT_FOUND),

    /**
     * 无权访问/拒绝访问 (HTTP 403 Forbidden)
     *
     * 场景：用户已经登录了，身份是合法的，但是试图访问没有权限看的数据，或者资源被锁定。
     * 示例：普通用户尝试调用管理员专用的删除接口，或者尝试查看不属于自己的私密信息。
     */
    ACCESS_DENIED(HttpStatus.FORBIDDEN),


    /**
     * 内部服务器错误 (HTTP 500 Internal Server Error)
     * 场景：后端代码发生了未捕获的异常（如空指针、除以零），或者数据库连接失败、Redis 宕机等基础设施故障。
     * 示例：前端请求完全正确，但是后端因为代码 Bug 崩溃了，系统作为兜底返回此错误。
     */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);


    private final HttpStatus status;

    ErrorCode(HttpStatus status){
        this.status=status;
    }

}
