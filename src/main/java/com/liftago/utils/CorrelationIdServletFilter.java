package com.liftago.utils;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * @author maciekr
 */
@WebFilter(urlPatterns = {"/*"}, initParams = {@WebInitParam(name = "UseShortFormInLogs", value = "false")})
public class CorrelationIdServletFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdServletFilter.class);
    public static final String USE_SHORT_FORM_PARAM = "UseShortFormInLogs";
    public static final String CORRELATION_ID_HEADER_NAME = "X-Liftago-CorrelationId";
    public static final String CORRELATION_ID_QUERY_PARAM_NAME = "correlationId";
    static public final String CORRELATION_ID_MDC_KEY = "correlationId";

    private boolean useSortFormInLogs = true;

    static private final InheritableThreadLocal<String> correlationId = new InheritableThreadLocal<String>();

    public static String getCorrelationId() {
        return correlationId.get();
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String useShortFormString = filterConfig.getInitParameter(USE_SHORT_FORM_PARAM);
        if (useShortFormString != null) {
            useSortFormInLogs = new Boolean(useShortFormString);
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        System.out.println("DO-FILTER");
        try {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            HttpServletResponse response = (HttpServletResponse) servletResponse;

            String correlationIdQueryParamValue = servletRequest.getParameter(CORRELATION_ID_QUERY_PARAM_NAME);
            log.debug("correlationIdQueryParamValue(" + CORRELATION_ID_QUERY_PARAM_NAME + ") = " + correlationIdQueryParamValue);

            //always prefer correlationId in the header
            String correlationIdHeaderValue = request.getHeader(CORRELATION_ID_HEADER_NAME);
            correlationIdHeaderValue = (StringUtils.isEmpty(correlationIdHeaderValue) && !StringUtils.isEmpty(correlationIdQueryParamValue))
                    ? correlationIdQueryParamValue : correlationIdHeaderValue;
            log.debug("correlationIdHeaderValue(" + CORRELATION_ID_HEADER_NAME
                    + ") = [" + correlationIdHeaderValue + "] NOTE: will generate a NEW correlationId if NULL !!!!!!!!!");

            String correlationIdRaw = (!StringUtils.isEmpty(correlationIdHeaderValue))
                    ? correlationIdHeaderValue : UUID.randomUUID().toString();

            log.debug("Request: (" + request.getRequestURI() + ") is MARKED as :" + correlationIdRaw);

            String correlationIdLogForm = (useSortFormInLogs) ? getGUIDShortForm(correlationIdRaw) : correlationIdRaw;
            log.debug("correlationIdLogForm= " + correlationIdLogForm);
            MDC.put(CORRELATION_ID_MDC_KEY, correlationIdLogForm);

            correlationId.set(correlationIdRaw);

            chain.doFilter(request, response);

        } finally {
            correlationId.remove();
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    @Override
    public void destroy() {
    }

    private static String getGUIDShortForm(String guid) {
        String shortForm = guid.replaceAll("-", "");
        if (shortForm.length() < 12) return guid;
        return shortForm.substring(0, 12);
    }
}