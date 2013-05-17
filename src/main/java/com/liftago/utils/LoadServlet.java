package com.liftago.utils;

import com.rabbitmq.client.*;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@WebServlet(urlPatterns = {"/load"}, asyncSupported = true)
public class LoadServlet extends HttpServlet {

    private static final int MAX_CONN_COUNT = 20000;
    private static final int MAX_MSG_PER_CONN = 5;
    private static final int WORKER_COUNT = 10;
    private static AtomicInteger connCount = new AtomicInteger(0);
    private static AtomicLong totalTime = new AtomicLong(0);
    private static AtomicBoolean interrupted = new AtomicBoolean(false);
    private static final AtomicInteger doAmqpStuffCount = new AtomicInteger(1);

    private static ExecutorService executor = Executors.newFixedThreadPool(WORKER_COUNT);
    private static ExecutorService amqpConnectionThreads = Executors.newFixedThreadPool(1);

    private static ConnectionFactory factory = new ConnectionFactory();
    static {
        factory.setVirtualHost("/");
        factory.setHost("37.61.197.137");
        factory.setPort(5672);
        factory.setUsername("rabbit");
        factory.setPassword("MQ6prd06");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter writer = resp.getWriter();
        writer.append("Connections count: " + connCount.get());
        writer.append("\n");
        writer.append("AVG: " + (totalTime.longValue() / doAmqpStuffCount.longValue() == 0 ? -1 : doAmqpStuffCount.longValue()));
        writer.flush();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.println("Main thread: " + Thread.currentThread() + ", Servlet: " + this);
        interrupted.set(false);
        final AsyncContext asyncContext = req.startAsync();
        asyncContext.setTimeout(10000);
        asyncContext.complete();
         //this will be in different threads
        for (int i = 0; i< MAX_CONN_COUNT; i++) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    while (!interrupted.get()) {
                        System.out.println(
                                "MEMORY : Total: " + Runtime.getRuntime().totalMemory() +
                                        ", Max: " + Runtime.getRuntime().maxMemory() +
                                        ", Free: " + Runtime.getRuntime().freeMemory());
                        System.out.println("THREAD COUNT "  + Thread.activeCount());
                        System.out.println("CONNECTION COUNT " + connCount.get());
                        try {
                            if (connCount.get() >= MAX_CONN_COUNT)
                                break;
                            totalTime.addAndGet(doAmqpStuff());
                            doAmqpStuffCount.incrementAndGet();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.println("Stopping load servlet...");
        interrupted.set(true);
        System.out.println("Stopped");
    }

    private long doAmqpStuff() throws Exception {
        Connection connection = factory.newConnection(amqpConnectionThreads);
        connCount.incrementAndGet();
        System.out.println("Opened fresh connection " + connection);

        Channel channel = connection.createChannel();
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        channel.queueDeclare(uuid, false, true, true, null);
        channel.queueBind(uuid, "amq.topic", uuid);

        final CountDownLatch count = new CountDownLatch(MAX_MSG_PER_CONN);

        final AtomicLong elapsedTime = new AtomicLong(0);

        for (int i = 0; i< MAX_MSG_PER_CONN; i++)
            channel.basicPublish("amq.topic", uuid,
                    new AMQP.BasicProperties.Builder().timestamp(new Date()).build(),
                    (uuid + "_num:" + i).getBytes());

        channel.basicConsume(uuid, true, uuid,
                new DefaultConsumer(channel) {
                    @Override
                    public void handleDelivery(String consumerTag,
                                               Envelope envelope,
                                               AMQP.BasicProperties properties,
                                               byte[] body)
                            throws IOException {
                        System.out.println(Thread.currentThread() + " CONSUMING...");
                        count.countDown();
                        elapsedTime.addAndGet(System.currentTimeMillis() - properties.getTimestamp().getTime());
                    }
                });

        if (count.await(60, TimeUnit.SECONDS))
            System.out.println(uuid + ", " + new Date() + ": ALL GOOD. AVG: " + (elapsedTime.longValue() / MAX_MSG_PER_CONN));
        else
            System.out.println(uuid + ", " + new Date() + ": TIMEOUT! Current count " + count.getCount());

        return elapsedTime.longValue();
    }
}
