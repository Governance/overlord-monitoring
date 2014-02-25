package org.overlord.rtgov.ui.server.interceptors;

import static org.jboss.errai.bus.server.api.RpcContext.getServletRequest;

import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.servlet.http.HttpServletRequest;

@Interceptor
@IUserContext.Binding
public class UserContextInterceptor {

    @AroundInvoke
    public Object manageTransaction(InvocationContext invocationContext) throws Exception {
        HttpServletRequest servletRequest = (HttpServletRequest) getServletRequest();
        IUserContext.Holder.setPrincipal(servletRequest.getUserPrincipal());
        try {
            return invocationContext.proceed();
        } finally {
            IUserContext.Holder.removeSecurityContext();
        }
    }
}
