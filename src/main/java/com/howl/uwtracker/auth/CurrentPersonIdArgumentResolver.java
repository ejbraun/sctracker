package com.howl.uwtracker.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import com.howl.uwtracker.web.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentPersonIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentPersonId.class) && parameter.getParameterType() == Long.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpSession session = webRequest.getNativeRequest(HttpServletRequest.class).getSession(false);
        Long personId = session == null ? null : (Long) session.getAttribute(SessionKeys.PERSON_ID);
        if (personId == null) {
            // Should be unreachable in practice — SessionAuthInterceptor already rejects this request.
            throw new ApiException(HttpStatus.UNAUTHORIZED, "not authenticated");
        }
        return personId;
    }
}
