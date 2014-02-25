package org.overlord.rtgov.ui.server.interceptors;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.security.Principal;

import javax.interceptor.InterceptorBinding;

public interface IUserContext {

    Principal getUserPrincipal();

    class Holder {
        private static final ThreadLocal<IUserContext> securityContextHolder = new ThreadLocal<IUserContext>();

        private Holder() {
        }

        public static void setPrincipal(final Principal principal) {
            setSecurityContext(new IUserContext() {

                @Override
                public Principal getUserPrincipal() {
                    return principal;
                }

            });
        }

        public static Principal getUserPrincipal() {
            IUserContext securityContext = getSecurityContext();
            return (securityContext != null ? securityContext.getUserPrincipal() : null);
        }

        public static IUserContext getSecurityContext() {
            return securityContextHolder.get();
        }

        public static void setSecurityContext(IUserContext securityContext) {
            securityContextHolder.set(securityContext);
        }

        public static void removeSecurityContext() {
            securityContextHolder.remove();
        }
    }

    @InterceptorBinding
    @Target({ ElementType.METHOD, ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Binding {
    }

}
