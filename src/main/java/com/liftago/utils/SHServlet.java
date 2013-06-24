package com.liftago.utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@WebServlet(urlPatterns = {"/splunk"}, asyncSupported = false)
public class SHServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(SHServlet.class);

    private static ExecutorService worker = Executors.newFixedThreadPool(1);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final String correlationId = CorrelationIdServletFilter.getCorrelationId();
        worker.submit(new Runnable() {
            @Override
            public void run() {
                MDC.put("correlationId", correlationId);
                int i = 0;
                System.out.println("let's do some logging!");
                while (true) {
                    LOGGER.info(String.valueOf(++i));
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                }
            }
        });

        PrintWriter writer = resp.getWriter();
        writer.append("OK");
        writer.flush();
    }
}
