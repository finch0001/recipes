package com.yee.jetty

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}

object SimpleApp3 {

  def main(args: Array[String]): Unit = {
    val server = new Server(8080)
    val context = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath("/")
    server.setHandler(context)
    context.addServlet(new ServletHolder(new StackServlet()),"/stack")
    server.start()
    server.join()
  }

}
