package com.yee.jetty

import java.net.{BindException, URI, URL}

import io.netty.channel.unix.Errors.NativeIoException
import javax.servlet.DispatcherType
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions
import scala.xml.Node
import org.eclipse.jetty.client.api.Response
import org.eclipse.jetty.proxy.ProxyServlet
import org.eclipse.jetty.server.{HttpConnectionFactory, Request, Server, ServerConnector}
import org.eclipse.jetty.server.handler._
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.eclipse.jetty.servlet._
// import org.eclipse.jetty.servlets.gzip.GzipHandler
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.eclipse.jetty.util.MultiException
import org.eclipse.jetty.util.component.LifeCycle
import org.eclipse.jetty.util.thread.{QueuedThreadPool, ScheduledExecutorScheduler}
import org.json4s.JValue
import org.json4s.jackson.JsonMethods.{pretty, render}
import scala.collection.JavaConverters._

/**
  * Utilities for launching a web server using Jetty's HTTP Server class
  */
object JettyUtils{

  // Base type for a function that returns something based on an HTTP request. Allows for
  // implicit conversion from many types of functions to jetty Handlers.
  type Responder[T] = HttpServletRequest => T

  class ServletParams[T <% AnyRef](val responder: Responder[T],
                                   val contentType: String,
                                   val extractFn: T => String = (in: Any) => in.toString) {}

  // Conversions from various types of Responder's to appropriate servlet parameters
  implicit def jsonResponderToServlet(responder: Responder[JValue]): ServletParams[JValue] =
    new ServletParams(responder, "text/json", (in: JValue) => pretty(render(in)))

  implicit def htmlResponderToServlet(responder: Responder[Seq[Node]]): ServletParams[Seq[Node]] =
    new ServletParams(responder, "text/html", (in: Seq[Node]) => "<!DOCTYPE html>" + in.toString)

  implicit def textResponderToServlet(responder: Responder[String]): ServletParams[String] =
    new ServletParams(responder, "text/plain")

  def createServlet[T <% AnyRef]( servletParams: ServletParams[T],
                                  securityMgr: SecurityManager): HttpServlet = {

    new HttpServlet {
      override def doGet(request: HttpServletRequest, response: HttpServletResponse) {
        try {
          // if (securityMgr.checkUIViewPermissions(request.getRemoteUser))
          if(true){
            response.setContentType("%s;charset=utf-8".format(servletParams.contentType))
            response.setStatus(HttpServletResponse.SC_OK)
            val result = servletParams.responder(request)
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            response.getWriter.print(servletParams.extractFn(result))
          } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
              "User is not authorized to access this page.")
          }
        } catch {
          case e: IllegalArgumentException =>
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage)
          case e: Exception =>
            println(s"GET ${request.getRequestURI} failed: $e", e)
            throw e
        }
      }
      // SPARK-5983 ensure TRACE is not supported
      protected override def doTrace(req: HttpServletRequest, res: HttpServletResponse): Unit = {
        res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
      }
    }
  }

  /** Create a context handler that responds to a request with the given path prefix */
  def createServletHandler[T <% AnyRef]( path: String,
                                         servletParams: ServletParams[T],
                                         securityMgr: SecurityManager,
                                         basePath: String = ""): ServletContextHandler = {
    createServletHandler(path, createServlet(servletParams, securityMgr), basePath)
  }

  /** Create a context handler that responds to a request with the given path prefix */
  def createServletHandler( path: String,
                            servlet: HttpServlet,
                            basePath: String): ServletContextHandler = {
    val prefixedPath = if (basePath == "" && path == "/") {
      path
    } else {
      (basePath + path).stripSuffix("/")
    }

    System.out.println("prefix path:" + prefixedPath)
    val contextHandler = new ServletContextHandler
    val holder = new ServletHolder(servlet)
    contextHandler.setContextPath(prefixedPath)
    // contextHandler.addServlet(holder,"/" + path)
    contextHandler.addServlet(holder, "/")
    contextHandler
  }

  /** Create a handler that always redirects the user to the given path */
  def createRedirectHandler(
                             srcPath: String,
                             destPath: String,
                             beforeRedirect: HttpServletRequest => Unit = x => (),
                             basePath: String = "",
                             httpMethods: Set[String] = Set("GET")): ServletContextHandler = {
    val prefixedDestPath = basePath + destPath
    val servlet = new HttpServlet {
      override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
        if (httpMethods.contains("GET")) {
          doRequest(request, response)
        } else {
          response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
        }
      }
      override def doPost(request: HttpServletRequest, response: HttpServletResponse): Unit = {
        if (httpMethods.contains("POST")) {
          doRequest(request, response)
        } else {
          response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
        }
      }
      private def doRequest(request: HttpServletRequest, response: HttpServletResponse): Unit = {
        beforeRedirect(request)
        // Make sure we don't end up with "//" in the middle
        val newUrl = new URL(new URL(request.getRequestURL.toString), prefixedDestPath).toString
        response.sendRedirect(newUrl)
      }
      // SPARK-5983 ensure TRACE is not supported
      protected override def doTrace(req: HttpServletRequest, res: HttpServletResponse): Unit = {
        res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
      }
    }
    createServletHandler(srcPath, servlet, basePath)
  }

  /**
    * Get the ClassLoader which loaded Spark.
    */
  def getSparkClassLoader: ClassLoader = getClass.getClassLoader

  /** Create a handler for serving files from a static directory */
  def createStaticHandler(resourceBase: String, path: String): ServletContextHandler = {
    val contextHandler = new ServletContextHandler
    contextHandler.setInitParameter("org.eclipse.jetty.servlet.Default.gzip", "false")
    val staticHandler = new DefaultServlet
    val holder = new ServletHolder(staticHandler)
    Option(getSparkClassLoader.getResource(resourceBase)) match {
      case Some(res) =>
        holder.setInitParameter("resourceBase", res.toString)
      case None =>
        throw new Exception("Could not find resource path for Web UI: " + resourceBase)
    }
    contextHandler.setContextPath(path)
    contextHandler.addServlet(holder, "/")
    contextHandler
  }

  /** Create a handler for proxying request to Workers and Application Drivers */
  def createProxyHandler(
                          prefix: String,
                          target: String): ServletContextHandler = {
    val servlet = new ProxyServlet {
      override def rewriteTarget(request: HttpServletRequest): String = {
        val rewrittenURI = createProxyURI(
          prefix, target, request.getRequestURI(), request.getQueryString())
        if (rewrittenURI == null) {
          return null
        }
        if (!validateDestination(rewrittenURI.getHost(), rewrittenURI.getPort())) {
          return null
        }
        rewrittenURI.toString()
      }

      override def filterServerResponseHeader(
                                               clientRequest: HttpServletRequest,
                                               serverResponse: Response,
                                               headerName: String,
                                               headerValue: String): String = {
        if (headerName.equalsIgnoreCase("location")) {
          val newHeader = createProxyLocationHeader(
            prefix, headerValue, clientRequest, serverResponse.getRequest().getURI())
          if (newHeader != null) {
            return newHeader
          }
        }
        super.filterServerResponseHeader(
          clientRequest, serverResponse, headerName, headerValue)
      }
    }

    val contextHandler = new ServletContextHandler
    val holder = new ServletHolder(servlet)
    contextHandler.setContextPath(prefix)
    contextHandler.addServlet(holder, "/")
    contextHandler
  }

  /**
    * Attempt to start a Jetty server bound to the supplied hostName:port using the given
    * context handlers.
    *
    * If the desired port number is contended, continues incrementing ports until a free port is
    * found. Return the jetty Server object, the chosen port, and a mutable collection of handlers.
    */
  def startJettyServer( hostName: String,
                        port: Int,
                        sslOptions: SSLOptions,
                        handlers: Seq[ServletContextHandler],
                        serverName: String = ""): ServerInfo = {

    val collection = new ContextHandlerCollection
    // addFilters(handlers, conf)

    val gzipHandlers = handlers.map { h =>
      val gzipHandler = new GzipHandler
      gzipHandler.setHandler(h)
      gzipHandler
    }

    // Bind to the given port, or throw a java.net.BindException if the port is occupied
    def connect(currentPort: Int): (Server, Int) = {
      val pool = new QueuedThreadPool
      if (serverName.nonEmpty) {
        pool.setName(serverName)
      }
      pool.setDaemon(true)

      val server = new Server(pool)
      val connectors = new ArrayBuffer[ServerConnector]
      // Create a connector on port currentPort to listen for HTTP requests
      val httpConnector = new ServerConnector(
        server,
        null,
        // Call this full constructor to set this, which forces daemon threads:
        new ScheduledExecutorScheduler(s"$serverName-JettyScheduler", true),
        null,
        -1,
        -1,
        new HttpConnectionFactory())
      httpConnector.setPort(currentPort)
      connectors += httpConnector

      sslOptions.createJettySslContextFactory().foreach { factory =>
        // If the new port wraps around, do not try a privileged port.
        val securePort =
          if (currentPort != 0) {
            (currentPort + 400 - 1024) % (65536 - 1024) + 1024
          } else {
            0
          }
        val scheme = "https"
        // Create a connector on port securePort to listen for HTTPS requests
        val connector = new ServerConnector(server, factory)
        connector.setPort(securePort)

        connectors += connector

        // redirect the HTTP requests to HTTPS port
        collection.addHandler(createRedirectHttpsHandler(securePort, scheme))
        println("secure port:" + securePort)
      }

      gzipHandlers.foreach(collection.addHandler)
      // As each acceptor and each selector will use one thread, the number of threads should at
      // least be the number of acceptors and selectors plus 1. (See SPARK-13776)
      var minThreads = 1
      connectors.foreach { connector =>
        // Currently we only use "SelectChannelConnector"
        // Limit the max acceptor number to 8 so that we don't waste a lot of threads
        connector.setAcceptQueueSize(math.min(connector.getAcceptors, 8))
        connector.setHost(hostName)
        // The number of selectors always equals to the number of acceptors
        minThreads += connector.getAcceptors * 2
      }
      server.setConnectors(connectors.toArray)
      pool.setMaxThreads(math.max(pool.getMaxThreads, minThreads))

      val errorHandler = new ErrorHandler()
      errorHandler.setShowStacks(true)
      errorHandler.setServer(server)
      server.addBean(errorHandler)
      server.setHandler(collection)
      try {
        server.start()
        (server, httpConnector.getLocalPort)
      } catch {
        case e: Exception =>
          server.stop()
          pool.stop()
          throw e
      }
    }

    val (server, boundPort) = startServiceOnPort[Server](port, connect, serverName)
    ServerInfo(server, boundPort, collection)
  }

  /**
    * Attempt to start a service on the given port, or fail after a number of attempts.
    * Each subsequent attempt uses 1 + the port used in the previous attempt (unless the port is 0).
    *
    * @param startPort The initial port to start the service on.
    * @param startService Function to start service on a given port.
    *                     This is expected to throw java.net.BindException on port collision.
    * @param serviceName Name of the service.
    * @return (service: T, port: Int)
    */
  def startServiceOnPort[T]( startPort: Int,
                             startService: Int => (T, Int),
                             serviceName: String = ""): (T, Int) = {

    require(startPort == 0 || (1024 <= startPort && startPort < 65536),
      "startPort should be between 1024 and 65535 (inclusive), or 0 for a random free port.")

    val serviceString = if (serviceName.isEmpty) "" else s" '$serviceName'"
    val maxRetries = 3 //portMaxRetries(conf)
    for (offset <- 0 to maxRetries) {
      // Do not increment port if startPort is 0, which is treated as a special port
      val tryPort = if (startPort == 0) {
        startPort
      } else {
        // If the new port wraps around, do not try a privilege port
        ((startPort + offset - 1024) % (65536 - 1024)) + 1024
      }

      try {
        val (service, port) = startService(tryPort)
        println(s"Successfully started service$serviceString on port $port.")
        return (service, port)
      } catch {
        case e: Exception if isBindCollision(e) =>
          if (offset >= maxRetries) {
            val exceptionMessage = s"${e.getMessage}: Service$serviceString failed after " +
              s"$maxRetries retries (starting from $startPort)! Consider explicitly setting " +
              s"the appropriate port for the service$serviceString (for example spark.ui.port " +
              s"for SparkUI) to an available port or increasing spark.port.maxRetries."
            val exception = new BindException(exceptionMessage)
            // restore original stack trace
            exception.setStackTrace(e.getStackTrace)
            throw exception
          }
          println(s"Service$serviceString could not bind on port $tryPort. " +
            s"Attempting port ${tryPort + 1}.")
      }
    }
    // Should never happen
    throw new RuntimeException(s"Failed to start service$serviceString on port $startPort")
  }


  /**
    * Return whether the exception is caused by an address-port collision when binding.
    */
  def isBindCollision(exception: Throwable): Boolean = {
    exception match {
      case e: BindException =>
        if (e.getMessage != null) {
          return true
        }
        isBindCollision(e.getCause)
      case e: MultiException =>
        e.getThrowables.asScala.exists(isBindCollision)
      case e: NativeIoException =>
        (e.getMessage != null && e.getMessage.startsWith("bind() failed: ")) ||
          isBindCollision(e.getCause)
      case e: Exception => isBindCollision(e.getCause)
      case _ => false
    }
  }


  private def createRedirectHttpsHandler(securePort: Int, scheme: String): ContextHandler = {
    val redirectHandler: ContextHandler = new ContextHandler
    redirectHandler.setContextPath("/")
    redirectHandler.setHandler(new AbstractHandler {
      override def handle(
                           target: String,
                           baseRequest: Request,
                           request: HttpServletRequest,
                           response: HttpServletResponse): Unit = {
        if (baseRequest.isSecure) {
          return
        }
        val httpsURI = createRedirectURI(scheme, baseRequest.getServerName, securePort,
          baseRequest.getRequestURI, baseRequest.getQueryString)
        response.setContentLength(0)
        response.encodeRedirectURL(httpsURI)
        response.sendRedirect(httpsURI)
        baseRequest.setHandled(true)
      }
    })
    redirectHandler
  }

  def createProxyURI(prefix: String, target: String, path: String, query: String): URI = {
    if (!path.startsWith(prefix)) {
      return null
    }

    val uri = new StringBuilder(target)
    val rest = path.substring(prefix.length())

    if (!rest.isEmpty()) {
      if (!rest.startsWith("/")) {
        uri.append("/")
      }
      uri.append(rest)
    }

    val rewrittenURI = URI.create(uri.toString())
    if (query != null) {
      return new URI(
        rewrittenURI.getScheme(),
        rewrittenURI.getAuthority(),
        rewrittenURI.getPath(),
        query,
        rewrittenURI.getFragment()
      ).normalize()
    }
    rewrittenURI.normalize()
  }

  def createProxyLocationHeader(
                                 prefix: String,
                                 headerValue: String,
                                 clientRequest: HttpServletRequest,
                                 targetUri: URI): String = {
    val toReplace = targetUri.getScheme() + "://" + targetUri.getAuthority()
    if (headerValue.startsWith(toReplace)) {
      clientRequest.getScheme() + "://" + clientRequest.getHeader("host") +
        prefix + headerValue.substring(toReplace.length())
    } else {
      null
    }
  }

  // Create a new URI from the arguments, handling IPv6 host encoding and default ports.
  private def createRedirectURI(
                                 scheme: String, server: String, port: Int, path: String, query: String) = {
    val redirectServer = if (server.contains(":") && !server.startsWith("[")) {
      s"[${server}]"
    } else {
      server
    }
    val authority = s"$redirectServer:$port"
    new URI(scheme, authority, path, query, null).toString
  }

}

case class ServerInfo(server: Server, boundPort: Int, rootHandler: ContextHandlerCollection) {

  def stop(): Unit = {
    server.stop()
    // Stop the ThreadPool if it supports stop() method (through LifeCycle).
    // It is needed because stopping the Server won't stop the ThreadPool it uses.
    val threadPool = server.getThreadPool
    if (threadPool != null && threadPool.isInstanceOf[LifeCycle]) {
      threadPool.asInstanceOf[LifeCycle].stop
    }
  }
}
