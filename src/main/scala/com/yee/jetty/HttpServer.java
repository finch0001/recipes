package com.yee.jetty;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import org.apache.commons.math3.util.Pair;
import org.eclipse.jetty.rewrite.handler.RewriteHandler;
import org.eclipse.jetty.rewrite.handler.RewriteRegexRule;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.LowResourceMonitor;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler.Context;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;

import javax.servlet.http.HttpServlet;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class HttpServer {

    private final String name;
    private String appDir;
    private WebAppContext webAppContext;
    private Server webServer;

    /**
     * Create a status server on the given port.
     */
    private HttpServer(final Builder b) throws IOException {
        this.name = b.name;
        createWebServer(b);
    }

    public void start(){
        try {
            this.webServer.start();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public static class Builder {
        private final String name;
        private String host;
        private int port;
        private int maxThreads;
        private final Map<String, Object> contextAttrs = new HashMap<String, Object>();
        private String keyStorePassword;
        private String keyStorePath;
        private boolean useSSL;
        private String allowedOrigins;
        private String allowedMethods;
        private String allowedHeaders;
        private String contextRootRewriteTarget = "/index.html";
        private final List<Pair<String, Class<? extends HttpServlet>>> servlets =
                new LinkedList<Pair<String, Class<? extends HttpServlet>>>();

        public Builder(String name) {
            Preconditions.checkArgument(name != null && !name.isEmpty(), "Name must be specified");
            this.name = name;
        }

        public HttpServer build() throws IOException {
            return new HttpServer(this);
        }

        public Builder setHost(String host) {
            this.host = host;
            return this;
        }

        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        public Builder setMaxThreads(int maxThreads) {
            this.maxThreads = maxThreads;
            return this;
        }

        public Builder setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
            return this;
        }

        public Builder setAllowedMethods(String allowedMethods) {
            this.allowedMethods = allowedMethods;
            return this;
        }

        public Builder setAllowedHeaders(String allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
            return this;
        }

        public Builder setContextAttribute(String name, Object value) {
            contextAttrs.put(name, value);
            return this;
        }

        public Builder setContextRootRewriteTarget(String contextRootRewriteTarget) {
            this.contextRootRewriteTarget = contextRootRewriteTarget;
            return this;
        }

        public Builder addServlet(String endpoint, Class<? extends HttpServlet> servlet) {
            servlets.add(new Pair<String, Class<? extends HttpServlet>>(endpoint, servlet));
            return this;
        }
    }

    private void createWebServer(final Builder b) throws IOException {
        // Create the thread pool for the web server to handle HTTP requests
        QueuedThreadPool threadPool = new QueuedThreadPool();
        if (b.maxThreads > 0) {
            threadPool.setMaxThreads(b.maxThreads);
        }
        threadPool.setDaemon(true);
        threadPool.setName(b.name + "-web");
        // this.webServer = new Server(threadPool);
        this.webServer = new Server(8983);
        this.appDir = "E:\\yusheng\\personal\\github\\myproject\\recipes\\webapps";//getWebAppsPath(b.name);
        this.webAppContext = createWebAppContext(b);
        initializeWebServer(b, threadPool.getMaxThreads());
    }

    private String getWebAppsPath(String appName) throws FileNotFoundException {
        String relativePath = "webapps/" + appName;
        URL url = getClass().getClassLoader().getResource(relativePath);
        if (url == null) {
            throw new FileNotFoundException(relativePath
                    + " not found in CLASSPATH");
        }
        String urlString = url.toString();
        return urlString.substring(0, urlString.lastIndexOf('/'));
    }

    /**
     * Create the web context for the application of specified name
     */
    private WebAppContext createWebAppContext(Builder b) {
        return new WebAppContext();
        /*
        WebAppContext ctx = new WebAppContext();
        setContextAttributes(ctx.getServletContext(), b.contextAttrs);
        ctx.setDisplayName(b.name);
        ctx.setContextPath("/");
        ctx.setWar(appDir + "/" + b.name);
        return ctx;
        */
    }

    /**
     * Set servlet context attributes that can be used in jsp.
     */
    private void setContextAttributes(Context ctx, Map<String, Object> contextAttrs) {
        for (Map.Entry<String, Object> e: contextAttrs.entrySet()) {
            ctx.setAttribute(e.getKey(), e.getValue());
        }
    }

    private void initializeWebServer(final Builder b, int queueSize) throws IOException {
        // Set handling for low resource conditions.
        final LowResourceMonitor low = new LowResourceMonitor(webServer);
        low.setLowResourcesIdleTimeout(10000);
        webServer.addBean(low);

        Connector connector = createChannelConnector(queueSize, b);
        webServer.addConnector(connector);

        RewriteHandler rwHandler = new RewriteHandler();
        rwHandler.setRewriteRequestURI(true);
        rwHandler.setRewritePathInfo(false);

        RewriteRegexRule rootRule = new RewriteRegexRule();
        // rootRule.setRegex("^/$");
        rootRule.setReplacement(b.contextRootRewriteTarget);
        rootRule.setTerminating(true);

        rwHandler.addRule(rootRule);
        rwHandler.setHandler(webAppContext);

        // Configure web application contexts for the web server
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        contexts.addHandler(rwHandler);
        webServer.setHandler(contexts);

        // addServlet("jmx", "/jmx", JMXJsonServlet.class);
        // addServlet("conf", "/conf", ConfServlet.class);
        addServlet("stacks", "/stacks", StackServlet.class);
        // addServlet("conflog", "/conflog", Log4j2ConfiguratorServlet.class);

        for (Pair<String, Class<? extends HttpServlet>> p : b.servlets) {
            addServlet(p.getFirst(), "/" + p.getFirst(), p.getSecond());
            System.out.println("add servlet!");
        }

        System.out.println("stacks");

        ServletContextHandler staticCtx =
                new ServletContextHandler(contexts, "/static");
        staticCtx.setResourceBase(appDir + "/static");
        staticCtx.addServlet(DefaultServlet.class, "/*");
        staticCtx.setDisplayName("static");

        String logDir = "./"; //getLogDir(b.conf);
        if (logDir != null) {
            ServletContextHandler logCtx =
                    new ServletContextHandler(contexts, "/logs");
            setContextAttributes(logCtx.getServletContext(), b.contextAttrs);
            // logCtx.addServlet(AdminAuthorizedServlet.class, "/*");
            logCtx.setResourceBase(logDir);
            logCtx.setDisplayName("logs");
        }

        webServer.setHandler(this.webAppContext);

        System.out.println("end up");
    }

    /**
     * Add a servlet in the server.
     * @param name The name of the servlet (can be passed as null)
     * @param pathSpec The path spec for the servlet
     * @param clazz The servlet class
     */
    public void addServlet(String name, String pathSpec,
                           Class<? extends HttpServlet> clazz) {
        ServletHolder holder = new ServletHolder(clazz);
        if (name != null) {
            holder.setName(name);
        }
        webAppContext.addServlet(holder, pathSpec);
    }

    /**
     * Create a channel connector for "http/https" requests
     */
    private Connector createChannelConnector(int queueSize, Builder b) {
        ServerConnector connector;
        final HttpConfiguration conf = new HttpConfiguration();
        conf.setRequestHeaderSize(1024*64);
        final HttpConnectionFactory http = new HttpConnectionFactory(conf);
        connector = new ServerConnector(webServer, http);
        connector.setAcceptQueueSize(queueSize);
        connector.setReuseAddress(true);
        connector.setHost(b.host);
        connector.setPort(b.port);
        return connector;
    }

}
