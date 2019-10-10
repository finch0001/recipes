package com.yee.jetty

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ResourceHandler

object SimpleApp {
  def main(args: Array[String]): Unit = {
    val server = new  Server(8080)
    val handler = new ResourceHandler()
    handler.setResourceBase("E:\\资料\\open-sources\\apache-hive-3.1.1-src\\apache-hive-3.1.1-src")
    handler.setDirectoriesListed(true)
    server.setHandler(handler)
    server.start()
  }
}
