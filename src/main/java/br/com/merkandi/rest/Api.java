package br.com.merkandi.rest;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.vavr.control.Try;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

@Path("api")
@WebListener
public class Api implements ServletContextListener
{
    private static long WAIT_DURATION_IN_MILLISECONDS = 0;
    private static final String BULKHEAD_A_NAME = "bulkhead-a";
    private static final String BULKHEAD_B_NAME = "bulkhead-b";
    private static Integer BULKHEAD_A_MAX_CONCURRENT_CALLS = 8;
    private static Integer BULKHEAD_B_MAX_CONCURRENT_CALLS = 2;
    private static Integer BULKHEAD_WAIT_TIME_IN_MILLISECONDS = 1000;

    private static Logger apiLogger = Logger.getLogger("apiLogger");
    private static FileHandler apiLoggerFilehandler = null;
    private static String FILE_HANDLER_PATTERN = null;

    private static Bulkhead bulkheadA = null;
    private static Bulkhead bulkheadB = null;


    @GET
    @Path("/a")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getA()
    {
        Response aResponse = null;

        Supplier<Response> supplier = () -> getAExecute();

        supplier = Bulkhead.decorateSupplier(bulkheadA, supplier);

        try
        {
            aResponse = Try.ofSupplier(supplier).get();
        }
        catch (Exception e)
        {
            aResponse = getAlternativeResponse();
            apiLogger.warning(e.getClass().getCanonicalName()+":::"+e.getMessage());
        }

        apiLogger.info("...a response status: " + aResponse.getStatus());

        return aResponse;
    }

    @GET
    @Path("/b")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getB()
    {
        Response bResponse = null;

        Supplier<Response> supplier = () -> getBExecute();

        supplier = Bulkhead.decorateSupplier(bulkheadB, supplier);

        try
        {
            bResponse = Try.ofSupplier(supplier).get();
        }
        catch (Exception e)
        {
            bResponse = getAlternativeResponse();
            apiLogger.warning(e.getClass().getCanonicalName()+":::"+e.getMessage());
        }

        apiLogger.info("...b response status: " + bResponse.getStatus());

        return bResponse;
    }

    public Response getAExecute()
    {
        try {
            Thread.sleep(WAIT_DURATION_IN_MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return Response.status(Response.Status.OK).entity("A").build();
    }

    public Response getBExecute()
    {
        return Response.status(Response.Status.OK).entity("B").build();
    }

    private Response getAlternativeResponse()
    {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
    }


    @POST
    @Path("/a/wait-duration")
    public Response postWaitDurationInMilliseconds(@FormParam("wait-duration") Long durationInMilliseconds)
    {
        if( durationInMilliseconds != null & durationInMilliseconds >= 0 )
        {
            WAIT_DURATION_IN_MILLISECONDS = durationInMilliseconds;
            apiLogger.info("wait duration updated:"+ WAIT_DURATION_IN_MILLISECONDS + " ms");
        }
        return Response.noContent().build();
    }


    @Override
    public void contextInitialized(ServletContextEvent sce)
    {
        FILE_HANDLER_PATTERN = sce.getServletContext().getInitParameter("LOG_PATH");
        try
        {
            SimpleFormatter formatter = new SimpleFormatter();
            apiLoggerFilehandler = new FileHandler(FILE_HANDLER_PATTERN);
            apiLoggerFilehandler.setFormatter(formatter);
            apiLogger.addHandler(apiLoggerFilehandler);
        }
        catch (SecurityException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        BulkheadConfig configA = BulkheadConfig.custom()
                .maxConcurrentCalls(BULKHEAD_A_MAX_CONCURRENT_CALLS)
                .maxWaitDuration(Duration.ofMillis(BULKHEAD_WAIT_TIME_IN_MILLISECONDS))
                .build();

        BulkheadConfig configB = BulkheadConfig.custom()
                .maxConcurrentCalls(BULKHEAD_B_MAX_CONCURRENT_CALLS)
                .maxWaitDuration(Duration.ofMillis(BULKHEAD_WAIT_TIME_IN_MILLISECONDS))
                .build();

        bulkheadA = Bulkhead.of(BULKHEAD_A_NAME, configA);
        bulkheadB = Bulkhead.of(BULKHEAD_B_NAME, configB);

        apiLogger.info("\nAPI running...");

        apiLogger.info("\n" + Runtime.getRuntime().availableProcessors() + " available processors...");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

    }
}