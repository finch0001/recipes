package com.yee.jetty

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

object SimpleApp2 {
  def main(args: Array[String]): Unit = {
    val server = new Server(8080)
    val webApp = new WebAppContext()
    webApp.setResourceBase("D:\\software\\apache-tomcat-8.5.39-windows-x64\\apache-tomcat-8.5.39\\webapps\\examples\\websocket")
    server.setHandler(webApp)
    server.start()
  }

}
