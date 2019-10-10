package com.yee.jetty

object SimpleApp5 {

  def main(args: Array[String]): Unit = {
    val host = "localhost"
    val port = 8983
    val sslOptions = new SSLOptions(false)
    val handlers = Seq(
      // JettyUtils.createServletHandler("",new StackServlet,""),
      JettyUtils.createServletHandler("",new Log4j2ConfiguratorServlet,"")
      // JettyUtils.createServletHandler("",new JMXJsonServlet,"")
    )
    val serverInfo = JettyUtils.startJettyServer(host,port,sslOptions,handlers,"first")

    Thread.sleep(600 * 1000)
    serverInfo.stop()
  }

}
