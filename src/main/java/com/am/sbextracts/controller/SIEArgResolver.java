package com.am.sbextracts.controller;

import com.am.sbextracts.vo.SlackInteractiveEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Configuration
public class SIEArgResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterAnnotation(SIE.class) != null;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {

        SlackInteractiveEvent slackInteractiveEvent = new SlackInteractiveEvent();
        slackInteractiveEvent.setTriggerId(webRequest.getParameter("trigger_id"));
        slackInteractiveEvent.setUserId(webRequest.getParameter("user_id"));
        return slackInteractiveEvent;
    }
}
